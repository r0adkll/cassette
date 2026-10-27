package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.BikeDiscovery
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.Discovered
import com.r0adkll.cassette.capture.api.FoundHub
import com.r0adkll.cassette.capture.api.FoundPowerMeter
import com.r0adkll.cassette.capture.api.HubDetails
import com.r0adkll.cassette.capture.api.KnownHub
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.data.ble.AdvertScanner
import com.r0adkll.cassette.data.ble.GattClient
import com.r0adkll.cassette.data.ble.GattResult
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.data.db.toDomain
import com.r0adkll.cassette.protocol.AxsReading
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.protocol.FrameType
import com.r0adkll.cassette.protocol.SystemBatteryTable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
@Inject
class DefaultBikeDiscovery(
  private val scanner: AdvertScanner,
  private val gatt: GattClient,
  private val recorder: CaptureRecorder,
  private val capture: CaptureController,
  private val db: CassetteDatabase,
  private val dispatchers: CassetteDispatchers,
  private val clock: Clock,
) : BikeDiscovery {

  override fun discover(): Flow<Discovered> =
    scanner
      .live()
      .runningFold(Found()) { found, reading -> found.with(reading) }
      .map { found ->
        val hubs = found.hubs.values.sortedByDescending { it.rssi }
        Discovered(
          hubs = hubs.filterNot { isKnown(it.serial.toString()) },
          powerMeters =
            found.powerMeters.values.filterNot { isKnown(it.name) }.sortedByDescending { it.rssi },
          known =
            hubs.mapNotNull { hub ->
              db.hubQueries.selectBySerial(hub.serial).executeAsOneOrNull()?.let {
                KnownHub(hub, BikeId(it.bike_id))
              }
            },
        )
      }
      .distinctUntilChanged()

  private fun isKnown(key: String) =
    db.componentQueries.selectByKey(key).executeAsOneOrNull() != null

  /** Everything heard so far, by serial or name, keeping each one's latest signal. */
  private data class Found(
    val hubs: Map<Long, FoundHub> = emptyMap(),
    val powerMeters: Map<String, FoundPowerMeter> = emptyMap(),
  ) {
    fun with(reading: AxsReading): Found =
      when {
        // The hub is the component that reports for the system (the rear derailleur on
        // road).
        reading.frame == FrameType.STATUS && reading.bridge && reading.serial != null -> {
          val previous = hubs[reading.serial]
          val hub =
            FoundHub(
              serial = reading.serial!!,
              address = reading.address,
              kind =
                reading.kind.takeIf { it != ComponentKind.UNKNOWN }
                  ?: previous?.kind
                  ?: ComponentKind.UNKNOWN,
              modelId = reading.modelId ?: previous?.modelId,
              rssi = reading.rssi,
              battery = reading.battery ?: previous?.battery,
              firmware = reading.firmware ?: previous?.firmware,
            )
          copy(hubs = hubs + (hub.serial to hub))
        }
        reading.frame == FrameType.POWER_METER && reading.name != null -> {
          val meter =
            FoundPowerMeter(
              reading.name!!,
              reading.address,
              reading.modelId,
              reading.rssi,
            )
          copy(powerMeters = powerMeters + (meter.name to meter))
        }
        else -> this
      }
  }

  override suspend fun readHub(hub: FoundHub): HubDetails? {
    val result =
      gatt.read(
        hub.address,
        listOf(
          SystemBatteryTable.CHARACTERISTIC,
          SystemBatteryTable.VERSIONS_CHARACTERISTIC,
        ),
      )
    val values = (result as? GattResult.Values)?.values ?: return null
    val table =
      values[SystemBatteryTable.CHARACTERISTIC]?.let(SystemBatteryTable::parse) ?: return null
    val versions =
      values[SystemBatteryTable.VERSIONS_CHARACTERISTIC]?.let(SystemBatteryTable::parseVersions)
    return HubDetails(hub, table, versions)
  }

  override suspend fun addBike(
    name: String,
    type: BikeType,
    hubs: List<HubDetails>,
    powerMeter: FoundPowerMeter?,
  ): BikeId {
    require(hubs.isNotEmpty()) { "A bike needs a system" }
    val now = clock.now()
    val nowMillis = now.toEpochMilliseconds()
    val bike =
      withContext(dispatchers.io) {
        db.transactionWithResult {
          db.bikeQueries.insert(
            name.trim(),
            type.name,
            null,
            hubs.first().hub.serial,
            nowMillis,
            nowMillis,
          )
          val bikeId = db.bikeQueries.lastInsertedId().executeAsOne()
          if (powerMeter != null) {
            db.componentQueries.insert(
              bikeId,
              powerMeter.name,
              null,
              ComponentKind.POWER_METER.name,
              powerMeter.modelId?.toLong(),
              null,
              null,
              null,
              nowMillis,
              nowMillis,
              null,
            )
            db.componentQueries.setAddress(
              powerMeter.address,
              db.componentQueries.lastInsertedId().executeAsOne(),
            )
          }
          db.bikeQueries.selectById(bikeId).executeAsOne().toDomain()
        }
      }
    applySystems(bike, hubs, now)
    capture.ensureBackgroundCapture("bike added")
    return bike.id
  }

  override suspend fun addSystems(bike: BikeId, hubs: List<HubDetails>) {
    val row =
      withContext(dispatchers.io) { db.bikeQueries.selectById(bike.value).executeAsOneOrNull() }
        ?: return
    applySystems(row.toDomain(), hubs, clock.now())
  }

  /** Each hub's system: its row, then every component it lists, with their batteries. */
  private suspend fun applySystems(bike: Bike, hubs: List<HubDetails>, now: Instant) {
    hubs.forEachIndexed { index, hub ->
      val owner =
        withContext(dispatchers.io) {
          db.transactionWithResult {
            db.hubQueries.selectBySerial(hub.hub.serial).executeAsOneOrNull()?.bike_id
              ?: bike.id.value.also {
                // A millisecond apart, so the systems list in the order they were found.
                db.hubQueries.insert(hub.hub.serial, it, now.toEpochMilliseconds() + index)
              }
          }
        }
      // Another bike's system already; it stays where the user put it.
      if (owner != bike.id.value) return@forEachIndexed
      recorder.applyHubTable(bike, hub.table, hub.versions, now)
      withContext(dispatchers.io) {
        db.componentQueries.selectByKey(hub.hub.serial.toString()).executeAsOneOrNull()?.let {
          db.componentQueries.setAddress(hub.hub.address, it.id)
        }
      }
      hub.hub.battery?.let { recorder.applyAdvertBattery(hub.hub.serial.toString(), it, now) }
    }
  }
}
