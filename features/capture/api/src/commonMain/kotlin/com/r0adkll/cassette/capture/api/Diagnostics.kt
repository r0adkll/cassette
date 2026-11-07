package com.r0adkll.cassette.capture.api

import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.protocol.Advert
import com.r0adkll.cassette.protocol.ComponentVersions
import com.r0adkll.cassette.protocol.SystemComponent
import com.r0adkll.cassette.protocol.SystemTable
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/** Raw views of capture for the Diagnostics area, in every build (decision 0005). */
interface Diagnostics {
  /**
   * Every AXS advert heard, ours or not, with its raw bytes, and Shimano Di2 adverts (decision
   * 0029), which also go to the capture log.
   */
  fun liveAdverts(): Flow<Advert>

  /**
   * Reads a Shimano derailleur's open characteristics, Battery Level and Device Information, in one
   * short connection, and logs what it found (decision 0029). No pairing, and never Shimano's own
   * services.
   */
  suspend fun probeShimano(address: String): ShimanoProbe

  /**
   * Reads each of the bike's hubs' tables now (decision 0028), records them, and returns the raw
   * bytes, a dump per hub; empty if the bike is gone.
   */
  suspend fun readHubTables(bikeId: BikeId): List<HubDump>

  /**
   * For the export: every device the capture log's background wakes heard, and which bike and
   * system it's on, or none (decision 0028).
   */
  suspend fun heardNearby(): String
}

/**
 * One hub's tables; the bytes are null when it didn't answer (or was never heard: no [address]).
 */
class HubDump(
  val hubSerial: Long,
  val address: String?,
  val at: Instant,
  val batteryBytes: ByteArray?,
  val versionBytes: ByteArray?,
  val battery: SystemTable<SystemComponent>?,
  val versions: SystemTable<ComponentVersions>?,
) {
  val answered: Boolean
    get() = batteryBytes != null || versionBytes != null
}

/**
 * What [Diagnostics.probeShimano] read; [failure] says why nothing was, and [services] are the
 * short UUIDs the derailleur listed (only those read, on iOS).
 */
class ShimanoProbe(
  val address: String,
  val at: Instant,
  val batteryPercent: Int?,
  /** Device Information, by name: "model RD-R9250". */
  val deviceInfo: Map<String, String>,
  val services: List<String>,
  val failure: String?,
) {
  /** One line per finding, for the Live adverts row. */
  val lines: List<String>
    get() =
      if (failure != null) listOf("read failed: $failure")
      else
        listOf("battery ${batteryPercent?.let { "$it %" } ?: "not reported"}") +
          deviceInfo.map { (name, value) -> "$name $value" } +
          listOfNotNull(services.takeIf { it.isNotEmpty() }?.joinToString(prefix = "services "))
}
