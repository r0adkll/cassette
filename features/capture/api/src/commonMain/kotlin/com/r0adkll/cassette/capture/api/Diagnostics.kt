package com.r0adkll.cassette.capture.api

import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.protocol.AxsReading
import com.r0adkll.cassette.protocol.ComponentVersions
import com.r0adkll.cassette.protocol.SystemComponent
import com.r0adkll.cassette.protocol.SystemTable
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/** Raw views of capture for the Diagnostics area, in every build (decision 0005). */
interface Diagnostics {
  /** Every AXS advert heard, ours or not, with its raw bytes. */
  fun liveAdverts(): Flow<AxsReading>

  /** Reads the bike's hub tables now, records them, and returns the raw bytes; null on failure. */
  suspend fun readHubTables(bikeId: BikeId): HubDump?
}

class HubDump(
  val hubSerial: Long,
  val address: String,
  val at: Instant,
  val batteryBytes: ByteArray?,
  val versionBytes: ByteArray?,
  val battery: SystemTable<SystemComponent>?,
  val versions: SystemTable<ComponentVersions>?,
)
