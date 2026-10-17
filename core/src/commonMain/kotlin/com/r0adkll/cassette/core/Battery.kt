package com.r0adkll.cassette.core

import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.PACK_MIN_MILLIVOLTS
import com.r0adkll.cassette.protocol.batteryStatus
import com.r0adkll.cassette.protocol.estimatePackPercent
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Readings older than this are shown as old, and the bike gets a "not seen" reminder (decision
 * 0010).
 */
val STALE_AFTER = 14.days

/**
 * What a reading means for display: the component's own status when it reports one, otherwise ours
 * from SRAM's thresholds (decision 0003).
 */
val BatteryReading.status: BatteryStatus
  get() = reportedStatus ?: batteryStatus(millivolts, percent)

/** True when [percent] is our estimate from a pack's voltage, so the UI must mark it (≈). */
val BatteryReading.isEstimate: Boolean
  get() = percent == null && millivolts != null && millivolts >= PACK_MIN_MILLIVOLTS

/** The percentage to show: reported, or estimated from pack voltage; null for coin cells. */
val BatteryReading.displayPercent: Int?
  get() =
    percent?.takeIf { it > 0 }
      ?: millivolts?.takeIf { it >= PACK_MIN_MILLIVOLTS }?.let(::estimatePackPercent)

/**
 * Bar fill from 0 to 1. Coin cells have no percentage, so their bar maps 2.0–3.0 V linearly; it is
 * a visual cue only and never shown as a number.
 */
val BatteryReading.level: Float
  get() {
    displayPercent?.let {
      return it / 100f
    }
    val mv = millivolts ?: return 0f
    return ((mv - 2_000) / 1_000f).coerceIn(0f, 1f)
  }

fun isStale(lastSeenAt: Instant?, now: Instant): Boolean =
  lastSeenAt == null || now - lastSeenAt > STALE_AFTER

/** Worst first: CRITICAL, LOW, OK, UNKNOWN. */
val BatteryStatus.severity: Int
  get() =
    when (this) {
      BatteryStatus.CRITICAL -> 3
      BatteryStatus.LOW -> 2
      BatteryStatus.OK -> 1
      BatteryStatus.UNKNOWN -> 0
    }

/**
 * How a reading is written everywhere (app, widgets, tile, notifications): "78%", "≈97%" for a pack
 * estimate (decision 0003), or "2.91 V" for a coin cell.
 */
fun BatteryReading.valueText(): String {
  val percent = displayPercent
  return when {
    percent != null && isEstimate -> "≈$percent%"
    percent != null -> "$percent%"
    millivolts != null -> "${volts(millivolts)} V"
    else -> "—"
  }
}

/** Millivolts as volts with two decimals: 2910 → "2.91". */
fun volts(millivolts: Int): String {
  val hundredths = kotlin.math.round(millivolts / 10.0).toInt()
  return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}
