package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.Diagnostics
import com.r0adkll.cassette.capture.api.GattKind
import com.r0adkll.cassette.capture.api.HubDump
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.data.ble.AdvertScanner
import com.r0adkll.cassette.data.ble.GattClient
import com.r0adkll.cassette.data.ble.GattResult
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.data.db.toDomain
import com.r0adkll.cassette.protocol.AxsReading
import com.r0adkll.cassette.protocol.SystemBatteryTable
import com.r0adkll.cassette.protocol.toHex
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

  override fun liveAdverts(): Flow<AxsReading> = scanner.live()

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

  override suspend fun heardNearby(): String {
    val events = log.events.first()
    return withContext(dispatchers.io) { heardNearbyReport(events, db) }
  }
}
