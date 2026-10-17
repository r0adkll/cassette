package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.Diagnostics
import com.r0adkll.cassette.capture.api.HubDump
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.data.ble.AdvertScanner
import com.r0adkll.cassette.data.ble.GattClient
import com.r0adkll.cassette.data.ble.GattResult
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.data.db.toDomain
import com.r0adkll.cassette.protocol.AxsReading
import com.r0adkll.cassette.protocol.SystemBatteryTable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
@Inject
class DefaultDiagnostics(
  private val scanner: AdvertScanner,
  private val gatt: GattClient,
  private val recorder: CaptureRecorder,
  private val db: CassetteDatabase,
  private val dispatchers: CassetteDispatchers,
  private val clock: Clock,
) : Diagnostics {

  override fun liveAdverts(): Flow<AxsReading> = scanner.live()

  override suspend fun readHubTables(bikeId: BikeId): HubDump? {
    val (bike, hubAddress) =
      withContext(dispatchers.io) {
        val bike =
          db.bikeQueries.selectById(bikeId.value).executeAsOneOrNull() ?: return@withContext null
        val hub =
          bike.hub_serial?.let {
            db.componentQueries.selectByKey(it.toString()).executeAsOneOrNull()
          }
        bike.toDomain() to hub?.address
      } ?: return null
    val address = hubAddress ?: return null
    val values =
      (gatt.read(
          address,
          listOf(SystemBatteryTable.CHARACTERISTIC, SystemBatteryTable.VERSIONS_CHARACTERISTIC),
        ) as? GattResult.Values)
        ?.values ?: return null
    val batteryBytes = values[SystemBatteryTable.CHARACTERISTIC]
    val versionBytes = values[SystemBatteryTable.VERSIONS_CHARACTERISTIC]
    val battery = batteryBytes?.let(SystemBatteryTable::parse)
    val versions = versionBytes?.let(SystemBatteryTable::parseVersions)
    val now = clock.now()
    if (battery != null) recorder.applyHubTable(bike, battery, versions, now)
    return HubDump(bike.hubSerial ?: 0, address, now, batteryBytes, versionBytes, battery, versions)
  }
}
