package com.r0adkll.cassette.wear.complication

import com.r0adkll.cassette.bikes.summary.BikeSummary
import com.r0adkll.cassette.bikes.summary.weakest
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.wear.worst

/**
 * What the complications show for the default bike (decision 0013). Watch faces may recolour them,
 * so the icon and words carry the status too.
 */
data class BikeComplication(
  /** The component the ranged value shows: the one setting the status, else the weakest. */
  val level: Float?,
  /** Its value without the unit: "78", "≈94", "2.91". */
  val value: String?,
  /** Its code: "RD". */
  val code: String?,
  /**
   * The short text, about seven characters at most: "GOOD", "FD LOW", "CHARGE", "OLD" (the design
   * system's status words, so it reads like the phone's labels).
   */
  val status: String,
  /** Under the short text where the face shows a title: the component to charge ("FD"). */
  val statusTitle: String?,
  val tone: StatusTone,
  /** For accessibility: "Road bike. All good. Rear derailleur 78%." */
  val description: String,
)

fun BikeSummary.complication(): BikeComplication {
  val shown =
    when (status.tone) {
      StatusTone.Low,
      StatusTone.Critical -> worst
      else -> components.weakest()
    }
  // Short-text slots fit about seven characters: "ALL GOOD" already gets cut.
  val label =
    when (status.tone) {
      StatusTone.Good -> "GOOD"
      StatusTone.Low -> "${shown?.code ?: ""} LOW".trim()
      StatusTone.Critical -> "CHARGE"
      StatusTone.Stale -> "OLD"
      StatusTone.Unknown -> "NO DATA"
    }
  return BikeComplication(
    level = shown?.level,
    value = shown?.value?.removeSuffix("%")?.removeSuffix(" V"),
    code = shown?.code,
    status = label,
    statusTitle = shown?.code.takeIf { status.tone == StatusTone.Critical },
    tone = status.tone,
    description =
      listOfNotNull(name + ".", status.headline, shown?.let { "${it.name} ${it.value}." })
        .joinToString(" "),
  )
}
