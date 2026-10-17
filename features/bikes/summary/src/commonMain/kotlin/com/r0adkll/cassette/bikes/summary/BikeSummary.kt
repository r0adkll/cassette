package com.r0adkll.cassette.bikes.summary

import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.ComponentWithReading
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.core.isEstimate
import com.r0adkll.cassette.core.isStale
import com.r0adkll.cassette.core.level
import com.r0adkll.cassette.core.severity
import com.r0adkll.cassette.core.status
import com.r0adkll.cassette.core.valueText
import com.r0adkll.cassette.core.volts
import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.ComponentKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A bike as every surface words it: the status block and one row per component. */
data class BikeSummary(
  val bikeId: Long,
  val name: String,
  val bikeCount: Int,
  val status: BikeStatus,
  val components: List<ComponentRowModel>,
)

/** What the status block says (decision 0012). */
data class BikeStatus(
  val tone: StatusTone,
  val headline: String,
  val summary: String,
  val meta: String,
)

data class ComponentRowModel(
  val id: Long,
  val code: String,
  val name: String,
  val source: String,
  val value: String,
  val level: Float,
  val tone: StatusTone,
)

/**
 * Pure mapping from data to what the bike screen shows; tested without Compose. Widgets, the tile
 * and the watch use it too, so they word and colour things exactly like the app.
 */
fun BikeOverview.toSummary(now: Instant): BikeSummary {
  val stale = isStale(bike.lastSeenAt, now)
  return BikeSummary(
    bikeId = bike.id.value,
    name = bike.name,
    bikeCount = bikeCount,
    status = status(stale, now),
    components = components.map { it.toRow(stale) },
  )
}

/** The component with the lowest level among those with a reading; null when none has one. */
fun List<ComponentRowModel>.weakest(): ComponentRowModel? = filter {
  it.value != "—"
}
  .minByOrNull { it.level }

private fun BikeOverview.status(stale: Boolean, now: Instant): BikeStatus {
  val seen = bike.lastSeenAt?.let { "Seen ${ago(now - it)}" } ?: "Never seen"
  if (stale && bike.lastSeenAt != null) {
    val days = (now - bike.lastSeenAt!!).inWholeDays
    return BikeStatus(
      StatusTone.Stale,
      "Not seen in $days days.",
      "Readings below may be out of date. Wake the bike or refresh to update.",
      seen,
    )
  }
  val read = components.filter { it.latest != null }
  if (read.isEmpty()) {
    return BikeStatus(
      StatusTone.Unknown,
      "No readings yet.",
      "Wake the bike so Cassette can read its batteries.",
      seen,
    )
  }
  val worst = read.maxBy { it.latest!!.status.severity }
  val worstStatus = worst.latest!!.status
  val alsoAffected = read.count { it.latest!!.status == worstStatus } - 1
  val more = if (alsoAffected > 0) " (and $alsoAffected more)" else ""
  return when (worstStatus) {
    BatteryStatus.CRITICAL ->
      BikeStatus(
        StatusTone.Critical,
        "Charge now.",
        "${worst.name} is critical, ${worst.value}$more.",
        seen,
      )
    BatteryStatus.LOW ->
      BikeStatus(
        StatusTone.Low,
        "Charge soon.",
        "${worst.name} at ${worst.value}$more.",
        seen,
      )
    BatteryStatus.OK -> {
      val lowest = read.minBy { it.latest!!.level }
      BikeStatus(
        StatusTone.Good,
        "All good.",
        "Lowest: ${lowest.name.lowercase()}, ${lowest.value}.",
        seen,
      )
    }
    BatteryStatus.UNKNOWN ->
      BikeStatus(
        StatusTone.Unknown,
        "Status unknown.",
        "The components didn't report a battery level.",
        seen,
      )
  }
}

private fun ComponentWithReading.toRow(stale: Boolean): ComponentRowModel {
  val reading = latest
  val tone =
    when {
      reading == null -> StatusTone.Unknown
      stale -> StatusTone.Stale
      else -> reading.status.tone
    }
  return ComponentRowModel(
    id = component.id.value,
    code = component.kind.code,
    name = name,
    source = source,
    value = value,
    level = reading?.level ?: 0f,
    tone = tone,
  )
}

private val ComponentWithReading.name: String
  get() = component.kind.label

/** Decision 0003: reported first, estimates marked, coin cells in volts. */
private val ComponentWithReading.value: String
  get() = latest?.valueText() ?: "—"

private val ComponentWithReading.source: String
  get() {
    val reading = latest ?: return "No reading yet"
    val firmware = component.firmware?.let { " · fw $it" }.orEmpty()
    return when {
      reading.isEstimate -> "Estimated from ${volts(reading.millivolts!!)} V"
      reading.percent == null && reading.millivolts != null -> "Coin cell$firmware"
      reading.method == ReadMethod.Gatt -> "Reported by the ${component.kind.label.lowercase()}"
      else -> "Reported$firmware"
    }
  }

val BatteryStatus.tone: StatusTone
  get() =
    when (this) {
      BatteryStatus.OK -> StatusTone.Good
      BatteryStatus.LOW -> StatusTone.Low
      BatteryStatus.CRITICAL -> StatusTone.Critical
      BatteryStatus.UNKNOWN -> StatusTone.Unknown
    }

val ComponentKind.code: String
  get() =
    when (this) {
      ComponentKind.REAR_DERAILLEUR -> "RD"
      ComponentKind.FRONT_DERAILLEUR -> "FD"
      ComponentKind.LEFT_SHIFTER,
      ComponentKind.LEFT_REVERB_CONTROLLER,
      ComponentKind.LEFT_FLATBAR_CONTROLLER,
      ComponentKind.LEFT_POD -> "L"
      ComponentKind.RIGHT_SHIFTER,
      ComponentKind.RIGHT_REVERB_CONTROLLER,
      ComponentKind.RIGHT_FLATBAR_CONTROLLER,
      ComponentKind.RIGHT_POD -> "R"
      ComponentKind.POWER_METER -> "PM"
      ComponentKind.DROPPER_POST -> "DP"
      ComponentKind.FORK -> "FK"
      ComponentKind.REAR_SHOCK -> "SH"
      ComponentKind.BLIPBOX -> "BB"
      ComponentKind.WIRELESS_BLIPS -> "BL"
      ComponentKind.PEDAL_SENSOR -> "PS"
      ComponentKind.AIRWIZ -> "AW"
      ComponentKind.POWERTRAIN -> "PT"
      ComponentKind.CONTROLLER,
      ComponentKind.UNKNOWN -> "?"
    }

fun ago(elapsed: Duration): String =
  when {
    elapsed < 1.minutes -> "just now"
    elapsed.inWholeMinutes < 60 -> "${elapsed.inWholeMinutes} min ago"
    elapsed.inWholeHours < 24 -> "${elapsed.inWholeHours} h ago"
    elapsed.inWholeDays == 1L -> "yesterday"
    else -> "${elapsed.inWholeDays} days ago"
  }
