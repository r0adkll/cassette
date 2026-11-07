package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.Diagnostics
import com.r0adkll.cassette.capture.api.GattKind
import com.r0adkll.cassette.capture.api.HubDump
import com.r0adkll.cassette.capture.api.ShimanoProbe
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.data.ble.AdvertScanner
import com.r0adkll.cassette.data.ble.BATTERY_LEVEL_CHARACTERISTIC
import com.r0adkll.cassette.data.ble.GattClient
import com.r0adkll.cassette.data.ble.GattResult
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.data.db.toDomain
import com.r0adkll.cassette.protocol.Advert
import com.r0adkll.cassette.protocol.ShimanoAdvert
import com.r0adkll.cassette.protocol.SystemBatteryTable
import com.r0adkll.cassette.protocol.shortUuid
import com.r0adkll.cassette.protocol.toHex
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
@Inject
class DefaultDiagnostics(
  private val scanner: AdvertScanner,
  private val gatt: GattClient,
  private val recorder: CaptureRecorder,
  private val db: CassetteDatabase,
  private val log: CaptureLog,
  private val dispatchers: CassetteDispatchers,
  private val clock: Clock,
) : Diagnostics {

  override fun liveAdverts(): Flow<Advert> = flow {
    // By address: when its last advert was logged, and what it said.
    val logged = mutableMapOf<String, Pair<Long, String>>()
    emitAll(
      scanner.live().onEach { advert ->
        if (advert !is ShimanoAdvert) return@onEach
        val said = advert.fingerprint()
        val last = logged[advert.address]
        if (
          last == null ||
            (last.second != said && advert.timestampMillis - last.first >= SHIMANO_LOG_GAP_MILLIS)
        ) {
          logged[advert.address] = advert.timestampMillis to said
          log.append(advert.toEvent())
        }
      }
    )
  }

  override suspend fun probeShimano(address: String): ShimanoProbe {
    val started = clock.now()
    val result = gatt.read(address, listOf(BATTERY_LEVEL_CHARACTERISTIC) + DEVICE_INFORMATION.keys)
    val now = clock.now()
    val probe =
      when (result) {
        is GattResult.Failed ->
          ShimanoProbe(address, now, null, emptyMap(), emptyList(), result.reason)
        is GattResult.Values ->
          ShimanoProbe(
            address = address,
            at = now,
            batteryPercent =
              result.values[BATTERY_LEVEL_CHARACTERISTIC]?.firstOrNull()?.toInt()?.and(0xFF),
            deviceInfo =
              DEVICE_INFORMATION.mapNotNull { (uuid, name) ->
                  result.values[uuid]?.decodeToString()?.trim('\u0000', ' ')?.let { name to it }
                }
                .toMap(),
            services = result.services.map(::shortUuid),
            failure = null,
          )
      }
    log.append(
      CaptureEvent.Gatt(
        at = now.toEpochMilliseconds(),
        key = address,
        kind = GattKind.ShimanoProbe,
        background = false,
        durationMillis = (now - started).inWholeMilliseconds,
        outcome = result.outcome(probe.batteryPercent != null),
        hex = (result as? GattResult.Values)?.values?.get(BATTERY_LEVEL_CHARACTERISTIC)?.toHex(),
        detail = probe.lines.joinToString("; "),
      )
    )
    return probe
  }

  override suspend fun readHubTables(bikeId: BikeId): List<HubDump> {
    val (bike, hubs) =
      withContext(dispatchers.io) {
        val bike =
          db.bikeQueries.selectById(bikeId.value).executeAsOneOrNull() ?: return@withContext null
        val hubs =
          db.hubQueries.selectForBike(bike.id).executeAsList().map { hub ->
            hub.serial to
              db.componentQueries.selectByKey(hub.serial.toString()).executeAsOneOrNull()?.address
          }
        bike.toDomain() to hubs
      } ?: return emptyList()
    return hubs.map { (serial, address) -> readHub(bike, serial, address) }
  }

  private suspend fun readHub(bike: Bike, serial: Long, address: String?): HubDump {
    val started = clock.now()
    val result = address?.let {
      gatt.read(
        it,
        listOf(SystemBatteryTable.CHARACTERISTIC, SystemBatteryTable.VERSIONS_CHARACTERISTIC),
      )
    }
    val values = (result as? GattResult.Values)?.values
    val batteryBytes = values?.get(SystemBatteryTable.CHARACTERISTIC)
    val versionBytes = values?.get(SystemBatteryTable.VERSIONS_CHARACTERISTIC)
    val battery = batteryBytes?.let(SystemBatteryTable::parse)
    val versions = versionBytes?.let(SystemBatteryTable::parseVersions)
    val now = clock.now()
    // Never heard, so never read: there's nothing to log.
    if (result != null)
      log.append(
        CaptureEvent.Gatt(
          at = now.toEpochMilliseconds(),
          key = serial.toString(),
          kind = GattKind.HubTable,
          background = false,
          durationMillis = (now - started).inWholeMilliseconds,
          outcome = result.outcome(battery != null),
          hex = batteryBytes?.toHex(),
          detail = result.detail(battery != null),
        )
      )
    if (battery != null) recorder.applyHubTable(bike, battery, versions, now)
    return HubDump(serial, address, now, batteryBytes, versionBytes, battery, versions)
  }

  private fun ShimanoAdvert.fingerprint(): String =
    rawHex ?: "$name|$manufacturerHex|$services|$serviceData"

  private fun ShimanoAdvert.toEvent() =
    CaptureEvent.Shimano(
      at = timestampMillis,
      address = address,
      name = name,
      rssi = rssi,
      mfg = manufacturerHex,
      services = shortServices,
      serviceData = serviceData.mapKeys { shortUuid(it.key) },
      raw = rawHex,
    )

  override suspend fun heardNearby(): String {
    val events = log.events.first()
    return withContext(dispatchers.io) { heardNearbyReport(events, db) }
  }

  private companion object {
    /** A Shimano advert whose bytes keep changing is logged at most this often. */
    const val SHIMANO_LOG_GAP_MILLIS = 5_000L

    /**
     * The standard Device Information a probe reads, by its name in the log. Not the serial number:
     * Shimano derives its E-TUBE keys from serials, and the export doesn't need one.
     */
    val DEVICE_INFORMATION =
      linkedMapOf(
        "00002a29-0000-1000-8000-00805f9b34fb" to "maker",
        "00002a24-0000-1000-8000-00805f9b34fb" to "model",
        "00002a27-0000-1000-8000-00805f9b34fb" to "hardware",
        "00002a26-0000-1000-8000-00805f9b34fb" to "firmware",
        "00002a28-0000-1000-8000-00805f9b34fb" to "software",
      )
  }
}
