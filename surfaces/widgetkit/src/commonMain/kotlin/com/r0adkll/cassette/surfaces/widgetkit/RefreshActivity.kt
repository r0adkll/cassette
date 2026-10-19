package com.r0adkll.cassette.surfaces.widgetkit

import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.summary.toSummary
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.capture.api.RefreshRun
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.protocol.ComponentKind
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The iOS Live Activity's content while a refresh runs (decision 0017, iteration 16, A): sent to
 * ActivityKit as the activity's content state, worded here so the Swift views only lay it out and
 * draw the countdown from [endsAt]. Times are epoch milliseconds; tones are
 * [com.r0adkll.cassette.common.compose.theme.StatusTone] names.
 */
@Serializable
data class RefreshActivityState(
  /** Which refresh: a new value starts a new activity. */
  val startedAt: Long,
  val phase: RefreshPhase,
  /** When the scan stops, for the ring and the countdown; Add 60 s moves it. */
  val endsAt: Long,
  val totalSeconds: Int,
  val bikeId: Long?,
  /** "ROAD BIKE · READING", "ROAD BIKE · READ IN 31 S". */
  val label: String,
  /** The Lock Screen's line under the label: what to do, who it's waiting for, what it found. */
  val line: String,
  /** The island's and StandBy's big words ("2 of 5.", "Charge soon.") and the line under them. */
  val headline: String,
  val detail: String,
  /** The bike's status tone once read, for Done's headline. */
  val tone: String,
  val heard: Int,
  val components: List<RefreshActivityComponent>,
  /** Ended: the app ends the activity, which then stays up for [dismissAfterSeconds]. */
  val ended: Boolean,
  val dismissAfterSeconds: Int,
)

@Serializable
data class RefreshActivityComponent(
  val code: String,
  /** The column's value: "78%", "≈11%", "2.91" (volts lose their unit to fit). */
  val value: String,
  val level: Float,
  val tone: String,
  val answered: Boolean,
)

enum class RefreshPhase {
  Listening,
  Reading,
  /** Every component answered: the green check. */
  Done,
  /** Ended with some answered: amber, "4 OF 5". */
  Partial,
  /** Ended with none answered: grey. */
  Silent,
  /** Bluetooth went off during the scan. */
  NotReady,
  /** Stopped early: the activity goes at once. */
  Stopped,
}

object RefreshActivityCodec {
  private val json = Json { encodeDefaults = true }

  fun encode(state: RefreshActivityState): String =
    json.encodeToString(RefreshActivityState.serializer(), state)
}

/** Done stays on the Lock Screen this long (iteration 16). */
const val DONE_STAYS_SECONDS = 5 * 60

/**
 * The activity for [run], or null when there's no bike to show. It follows the bike whose
 * components answered most since the refresh began (any capture path counts: a background discovery
 * during the scan too), the default bike until one has.
 */
fun refreshActivityState(
  run: RefreshRun,
  overviews: List<BikeOverview>,
  defaultBikeId: BikeId?,
): RefreshActivityState? {
  fun BikeOverview.answered() = components.count { c ->
    c.latest?.let { it.at >= run.startedAt } == true
  }
  val bike =
    overviews
      .filter { it.components.isNotEmpty() }
      .maxWithOrNull(
        compareBy<BikeOverview> { it.answered() }.thenBy { it.bike.id == defaultBikeId }
      ) ?: return null

  val now = run.endedAt ?: run.startedAt
  val rows = bike.toSummary(now).components
  val answeredIds =
    bike.components
      .filter { c -> c.latest?.let { it.at >= run.startedAt } == true }
      .map { it.component.id.value }
      .toSet()
  val heard = answeredIds.size
  val total = rows.size
  val waiting = bike.components.filter { it.component.id.value !in answeredIds }

  val progress = run.progress
  val phase =
    when {
      progress is RefreshProgress.Done && progress.stopped -> RefreshPhase.Stopped
      progress is RefreshProgress.Done && heard == total -> RefreshPhase.Done
      progress is RefreshProgress.Done && heard > 0 -> RefreshPhase.Partial
      progress is RefreshProgress.Done -> RefreshPhase.Silent
      !run.bluetoothReady -> RefreshPhase.NotReady
      heard == 0 -> RefreshPhase.Listening
      else -> RefreshPhase.Reading
    }
  val totalSeconds = (progress as? RefreshProgress.Scanning)?.totalSeconds ?: 60
  val name = bike.bike.name.uppercase()
  val status = bike.toSummary(now).status

  val label =
    when (phase) {
      RefreshPhase.Listening -> "$name · LISTENING"
      RefreshPhase.Reading -> "$name · READING"
      RefreshPhase.Done -> "$name · READ IN ${run.seconds()} S"
      RefreshPhase.Partial -> "$name · $heard OF $total READ"
      RefreshPhase.Silent -> "$name · NO ANSWER"
      RefreshPhase.NotReady -> "$name · NOT READY"
      RefreshPhase.Stopped -> "$name · STOPPED"
    }
  val line =
    when (phase) {
      RefreshPhase.Listening -> "Shift once to wake the bike."
      RefreshPhase.Reading ->
        if (waiting.all { it.component.kind == ComponentKind.POWER_METER }) {
          "Waiting for PM. Spin the crank."
        } else {
          "Waiting for ${rows.filter { it.id !in answeredIds }.map { it.code }.spoken()}."
        }
      RefreshPhase.Done -> "${status.headline} ${status.summary}"
      RefreshPhase.Partial ->
        "No answer from ${rows.filter { it.id !in answeredIds }.map { it.code }.spoken()}."
      RefreshPhase.Silent -> "Nothing answered. Shift once, then refresh."
      RefreshPhase.NotReady -> "Bluetooth is off. Turn it on, then refresh."
      RefreshPhase.Stopped -> "Stopped."
    }
  val headline =
    when (phase) {
      RefreshPhase.Listening -> "Listening."
      RefreshPhase.Reading,
      RefreshPhase.Partial -> "$heard of $total."
      RefreshPhase.Done -> status.headline
      RefreshPhase.Silent -> "No answer."
      RefreshPhase.NotReady -> "Bluetooth is off."
      RefreshPhase.Stopped -> "Stopped."
    }
  val detail =
    when (phase) {
      RefreshPhase.Done -> status.summary
      RefreshPhase.Silent -> "Shift once, then refresh."
      RefreshPhase.NotReady -> "Turn it on, then refresh."
      RefreshPhase.Stopped -> ""
      else -> line
    }
  val ended = run.endedAt != null || phase == RefreshPhase.NotReady

  return RefreshActivityState(
    startedAt = run.startedAt.toEpochMilliseconds(),
    phase = phase,
    endsAt = (run.startedAt + totalSeconds.seconds).toEpochMilliseconds(),
    totalSeconds = totalSeconds,
    bikeId = bike.bike.id.value,
    label = label,
    line = line,
    headline = headline,
    detail = detail,
    tone = status.tone.name,
    heard = heard,
    components =
      rows.map {
        RefreshActivityComponent(
          code = it.code,
          value = it.value.removeSuffix(" V"),
          level = it.level,
          tone = it.tone.name,
          answered = it.id in answeredIds,
        )
      },
    ended = ended,
    dismissAfterSeconds = if (phase == RefreshPhase.Stopped) 0 else DONE_STAYS_SECONDS,
  )
}

private fun RefreshRun.seconds(): Long = endedAt?.let { (it - startedAt).inWholeSeconds } ?: 0

/** "L", "L and R", "L, R and PM". */
private fun List<String>.spoken(): String =
  if (size <= 1) joinToString() else dropLast(1).joinToString(", ") + " and " + last()
