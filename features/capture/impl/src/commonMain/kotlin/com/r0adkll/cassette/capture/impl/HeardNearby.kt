package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.Sighting
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.protocol.AxsAdvertParser
import com.r0adkll.cassette.protocol.ComponentKind
import kotlin.time.Instant

/**
 * The export's "Heard nearby" section (decision 0028): every device the log's background wakes
 * heard, re-parsed from their raw bytes, and which bike and system it's on. A hub on no bike is a
 * system no bike has; a key that's an address came in frames without a serial. Reads [db], so call
 * it off the main thread.
 */
internal fun heardNearbyReport(events: List<CaptureEvent>, db: CassetteDatabase): String {
  val wakes = events.filterIsInstance<CaptureEvent.Delivery>()
  val heard = linkedMapOf<String, HeardDevice>()
  wakes.forEach { wake ->
    wake.sightings.forEach { heard.getOrPut(it.key) { HeardDevice(it.key) }.add(it) }
  }
  if (heard.isEmpty()) return "Heard nearby: no background wakes in the capture log."

  val bikes = db.bikeQueries.selectAll().executeAsList().associateBy { it.id }
  val placed =
    heard.values.groupBy { device ->
      val component =
        db.componentQueries.selectByKey(device.key).executeAsOneOrNull()?.takeIf {
          it.removed_at == null
        } ?: db.componentQueries.selectByAddress(device.key).executeAsList().firstOrNull()
      component?.bike_id
    }
  return buildString {
    appendLine(
      "Heard nearby: ${heard.size} devices in ${wakes.size} background wakes, " +
        "${Instant.fromEpochMilliseconds(wakes.first().at)} to " +
        Instant.fromEpochMilliseconds(wakes.last().at)
    )
    val order = listOf(null) + bikes.keys.filter { it in placed }
    order.forEach { bikeId ->
      val devices = placed[bikeId] ?: return@forEach
      appendLine(
        if (bikeId == null) "Not on a bike"
        else "On bike $bikeId (${bikes[bikeId]?.name ?: "gone"})"
      )
      devices
        .sortedWith(compareByDescending<HeardDevice> { it.hub }.thenByDescending { it.count })
        .forEach { appendLine("  ${it.line(db)}") }
    }
  }
}

/** One device's sightings, by key. */
private class HeardDevice(val key: String) {
  var count = 0
  val frames = linkedMapOf<String, Int>()
  var weakest = Int.MAX_VALUE
  var strongest = Int.MIN_VALUE
  var last = 0L
  var hub = false
  var kind = ComponentKind.UNKNOWN
  var modelId: Int? = null

  fun add(sighting: Sighting) {
    count++
    frames[sighting.frame] = (frames[sighting.frame] ?: 0) + 1
    weakest = minOf(weakest, sighting.rssi)
    strongest = maxOf(strongest, sighting.rssi)
    last = maxOf(last, sighting.heardAt)
    val reading =
      runCatching {
        AxsAdvertParser.parse(
          name = sighting.key.takeIf { sighting.frame == "POWER_METER" },
          manufacturer = sighting.mfg?.hexBytes() ?: ByteArray(0),
          serviceData = sighting.svc?.hexBytes(),
          rssi = sighting.rssi,
          address = sighting.key,
          timestampMillis = sighting.heardAt,
        )
      }
        .getOrNull() ?: return
    hub = hub || reading.bridge
    if (kind == ComponentKind.UNKNOWN) kind = reading.kind
    modelId = modelId ?: reading.modelId
  }

  /** "1013571428 hub · Rear shock · model 1085 STATUS 19 -77 to -27 dBm last … system …" */
  fun line(db: CassetteDatabase): String {
    val what =
      listOfNotNull(
          "hub".takeIf { hub },
          kind.takeIf { it != ComponentKind.UNKNOWN }?.label,
          modelId?.let { "model $it" },
          "no serial".takeIf { ':' in key && !hub && kind == ComponentKind.UNKNOWN },
        )
        .joinToString(" · ")
    val seen = frames.entries.joinToString { (frame, n) -> "$frame $n" }
    return listOfNotNull(
        key.padEnd(18),
        what,
        seen,
        "$weakest to $strongest dBm",
        "last ${Instant.fromEpochMilliseconds(last)}",
        place(db),
      )
      .joinToString("  ")
  }

  /** Its system, or whose address it is; null when it's on no bike. */
  private fun place(db: CassetteDatabase): String? {
    val byKey =
      db.componentQueries.selectByKey(key).executeAsOneOrNull()?.takeIf { it.removed_at == null }
    if (byKey != null) {
      val hubSerial = byKey.hub_serial ?: return null
      if (hubSerial.toString() == key) return "its own system"
      val hubKind = db.componentQueries.selectByKey(hubSerial.toString()).executeAsOneOrNull()?.kind
      return "in the ${hubKind?.let(::labelOf) ?: "AXS"} system ($hubSerial)"
    }
    val byAddress = db.componentQueries.selectByAddress(key).executeAsList().firstOrNull()
    return byAddress?.let { "the ${labelOf(it.kind)}'s address (${it.key})" }
  }
}

private fun labelOf(kind: String) =
  ComponentKind.entries.firstOrNull { it.name == kind }?.label ?: kind

private fun String.hexBytes(): ByteArray =
  split(' ').filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
