package com.r0adkll.cassette.wear.complication

import com.r0adkll.cassette.bikes.summary.BikeSummary
import com.r0adkll.cassette.bikes.summary.picked
import com.r0adkll.cassette.bikes.summary.weakest
import com.r0adkll.cassette.common.compose.components.word
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.wear.worst

/**
 * What the complications show for a bike (decisions 0013 and 0027). Watch faces may recolour them,
 * so the icon and words carry the status too.
 */
data class BikeComplication(
  /**
   * The component the ranged value shows: the one picked, else the one setting the status, else the
   * weakest.
   */
  val level: Float?,
  /** Its value without the unit: "78", "≈94", "2.91". */
  val value: String?,
  /** Its code: "RD". */
  val code: String?,
  /**
   * The short text, about seven characters at most: "GOOD", "FD LOW", "CHARGE", "OLD" (the design
   * system's status words, so it reads like the phone's labels), or a picked component's value
   * ("92%", "2.91 V").
   */
  val status: String,
  /**
   * Under the short text where the face shows a title: the component to charge ("FD"), or the
   * picked one.
   */
  val statusTitle: String?,
  /** The bike's status, or a picked component's own. */
  val tone: StatusTone,
  /** For accessibility: "Road bike. All good. Rear derailleur 78%." */
  val description: String,
)

/**
 * The complication for this bike. With [picked] (a component kind the bike has), it shows that
 * component whatever the rest of the bike says, in its own status (decision 0027); otherwise the
 * one setting the status, else the weakest.
 */
fun BikeSummary.complication(picked: ComponentKind? = null): BikeComplication {
  picked?.let(components::picked)?.let { component ->
    return BikeComplication(
      level = component.level,
      value = component.value.compact(),
      code = component.code,
      status = component.value,
      statusTitle = component.code,
      tone = component.tone,
      description =
        "$name. ${component.name} ${component.value}, ${component.tone.word.lowercase()}.",
    )
  }
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
    value = shown?.value?.compact(),
    code = shown?.code,
    status = label,
    statusTitle = shown?.code.takeIf { status.tone == StatusTone.Critical },
    tone = status.tone,
    description =
      listOfNotNull(name + ".", status.headline, shown?.let { "${it.name} ${it.value}." })
        .joinToString(" "),
  )
}

private fun String.compact() = removeSuffix("%").removeSuffix(" V")
