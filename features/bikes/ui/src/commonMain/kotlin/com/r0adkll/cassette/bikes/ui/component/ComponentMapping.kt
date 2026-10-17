package com.r0adkll.cassette.bikes.ui.component

import com.r0adkll.cassette.bikes.api.ComponentDetail
import com.r0adkll.cassette.bikes.summary.ago
import com.r0adkll.cassette.bikes.summary.tone
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.core.charges
import com.r0adkll.cassette.core.isEstimate
import com.r0adkll.cassette.core.isStale
import com.r0adkll.cassette.core.level
import com.r0adkll.cassette.core.status
import com.r0adkll.cassette.core.valueText
import com.r0adkll.cassette.protocol.PACK_MIN_MILLIVOLTS
import kotlin.math.roundToInt
import kotlin.time.Instant

internal fun ComponentDetail.toModel(range: HistoryRange, now: Instant): ComponentDetailModel {
  val reading = latest
  val stale = reading != null && isStale(reading.at, now)
  val tone =
    when {
      reading == null -> StatusTone.Unknown
      stale -> StatusTone.Stale
      else -> reading.status.tone
    }
  val lastCharge = charges(history).lastOrNull()
  val coinCell = reading?.millivolts?.let { it < PACK_MIN_MILLIVOLTS } == true
  return ComponentDetailModel(
    bikeName = bike.name,
    name = component.kind.label,
    // Estimates keep their ≈ everywhere (decision 0003).
    value = reading?.valueText() ?: "—",
    headline =
      when (tone) {
        StatusTone.Good -> "Good."
        StatusTone.Low -> "Low."
        StatusTone.Critical -> "Charge now."
        StatusTone.Stale -> "Old reading."
        StatusTone.Unknown -> "No reading yet."
      },
    source =
      when {
        reading == null -> "Wake the bike and refresh to read it"
        reading.isEstimate -> "Estimated from voltage, ${ago(now - reading.at)}"
        reading.method == ReadMethod.Hub -> "Reported by the hub, ${ago(now - reading.at)}"
        else -> "Reported by the ${component.kind.label.lowercase()}, ${ago(now - reading.at)}"
      },
    level = reading?.level ?: 0f,
    tone = tone,
    stats =
      listOf(
        "Voltage" to (reading?.millivolts?.let { volts(it) } ?: "—"),
        "Shifts" to (component.actuations?.let(::thousands) ?: "—"),
        "Firmware" to (component.firmware ?: "—"),
        "Model" to (component.modelId?.toString() ?: "—"),
        "Serial" to (component.serial?.toString() ?: component.key),
        "Last read" to (reading?.let { ago(now - it.at).removeSuffix(" ago") } ?: "—"),
      ),
    range = range,
    chart = history.filter { it.millivolts != null || it.percent != null }.map { it.level },
    chartCaption =
      when {
        lastCharge != null ->
          "${if (coinCell) "Battery replaced" else "Charged"} ${ago(now - lastCharge.at)}"
        history.size < 2 -> "History builds as Cassette hears the bike."
        else ->
          "No ${if (coinCell) "battery change" else "charge"} in the last ${range.label.lowercase()}"
      },
  )
}

private fun volts(millivolts: Int): String {
  val hundredths = (millivolts / 10.0).roundToInt()
  return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')} V"
}

private fun thousands(value: Long): String =
  value.toString().reversed().chunked(3).joinToString(",").reversed()
