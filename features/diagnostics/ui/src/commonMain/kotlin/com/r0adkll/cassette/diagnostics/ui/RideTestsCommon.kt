package com.r0adkll.cassette.diagnostics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.RideSummary
import com.r0adkll.cassette.capture.api.RideVerdict
import com.r0adkll.cassette.capture.api.activeSession
import com.r0adkll.cassette.capture.api.summarizeSessions
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.data.ble.ScanProfile
import com.slack.circuit.retained.collectAsRetainedState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** The capture log's ride tests: the one recording, if any, and the rest, newest first. */
data class RideTests(val active: RideSummary?, val past: List<RideSummary>)

/** Ride tests from [log]; with [tick], re-summarised every second so a running test counts up. */
@Composable
internal fun rememberRideTests(log: CaptureLog, clock: Clock, tick: Boolean = false): RideTests {
  val events by remember { log.events }.collectAsRetainedState(emptyList())
  val now by
    produceState(clock.now(), tick) {
      while (tick) {
        delay(1_000)
        value = clock.now()
      }
    }
  val sessions = summarizeSessions(events, maxOf(now, clock.now()).toEpochMilliseconds())
  val activeAt = events.activeSession()?.at
  return RideTests(
    active = sessions.firstOrNull { it.startedAt == activeAt },
    past = sessions.filter { it.startedAt != activeAt },
  )
}

/** A component as results name it: its badge code and name. */
data class ComponentName(val code: String, val name: String)

/** Every bike's components by capture key, so results say "Rear derailleur", not a serial. */
@Composable
internal fun rememberComponentNames(bikes: BikeRepository): Map<String, ComponentName> {
  val overviews by remember { bikes.observeOverviews() }.collectAsRetainedState(emptyList())
  return overviews
    .flatMap { it.components }
    .associate {
      it.component.key to ComponentName(it.component.kind.short, it.component.kind.label)
    }
}

/** A scan profile's name in ride tests, including ones no longer offered (old sessions). */
internal fun profileLabel(name: String): String =
  when (name) {
    ScanProfile.AllLowPower.name -> "Low power"
    ScanProfile.Batched30s.name -> "Batched 30 s"
    "AllBalanced" -> "Balanced"
    "FirstAndLost" -> "First + lost"
    else -> name
  }

internal val ScanProfile.label: String
  get() = profileLabel(name)

/** "46 s", "14 min", "1 h 20 min". */
internal fun spoken(millis: Long): String {
  val d = millis.milliseconds
  return when {
    d.inWholeMinutes < 1 -> "${d.inWholeSeconds} s"
    d.inWholeHours < 1 -> "${d.inWholeMinutes} min"
    else -> "${d.inWholeHours} h ${d.inWholeMinutes % 60} min"
  }
}

/** A clock face: "9:12", "1:04:30". */
internal fun clockFace(millis: Long): String {
  val d = millis.milliseconds
  val s = (d.inWholeSeconds % 60).toString().padStart(2, '0')
  return if (d.inWholeHours < 1) "${d.inWholeMinutes}:$s"
  else "${d.inWholeHours}:${(d.inWholeMinutes % 60).toString().padStart(2, '0')}:$s"
}

/** For the raw details: "14 min 2 s". */
internal fun precise(millis: Long): String {
  val d = millis.milliseconds
  return when {
    d.inWholeMinutes < 1 -> "${d.inWholeSeconds} s"
    d.inWholeHours < 1 -> "${d.inWholeMinutes} min ${d.inWholeSeconds % 60} s"
    else -> "${d.inWholeHours} h ${d.inWholeMinutes % 60} min"
  }
}

internal val RideVerdict.tone: StatusTone
  get() =
    when (this) {
      RideVerdict.KeptUp -> StatusTone.Good
      RideVerdict.Patchy -> StatusTone.Low
      RideVerdict.Lost -> StatusTone.Critical
    }

internal val RideVerdict.word: String
  get() =
    when (this) {
      RideVerdict.KeptUp -> "Kept up"
      RideVerdict.Patchy -> "Patchy"
      RideVerdict.Lost -> "Lost the bike"
    }

internal val RideVerdict.icon: ImageVector
  get() =
    when (this) {
      RideVerdict.KeptUp -> LucideIcons.CircleCheck
      RideVerdict.Patchy -> LucideIcons.TriangleAlert
      RideVerdict.Lost -> LucideIcons.CircleX
    }

/** The verdict's icon and word in its status colour, for past tests. */
@Composable
internal fun VerdictLabel(verdict: RideVerdict, modifier: Modifier = Modifier) {
  val color = CassetteTheme.colors.status(verdict.tone)
  Row(
    modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(verdict.icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
    Text(verdict.word.uppercase(), style = CassetteTheme.type.status, color = color)
  }
}

/**
 * Wakes across the session as bars, one per slice of [RideSummary.timeline]. A quiet stretch of at
 * least [QUIET_TINT_MILLIS] shows as tinted columns in the low colour; shorter empty slices (early
 * in a test, or between Batched 30 s wakes) are a baseline.
 */
@Composable
internal fun WakeTimeline(
  timeline: List<Int>,
  durationMillis: Long,
  description: String,
  modifier: Modifier = Modifier,
  height: Dp = 52.dp,
) {
  val colors = CassetteTheme.colors
  val bar = colors.text
  val baseline = colors.line
  val quiet = colors.status(StatusTone.Low).copy(alpha = 0.28f)
  val most = (timeline.maxOrNull() ?: 0).coerceAtLeast(1)
  val tinted = quietSlices(timeline, durationMillis)
  Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
    if (timeline.isEmpty()) return@Canvas
    val gap = 1.dp.toPx()
    val width = (size.width - gap * (timeline.size - 1)) / timeline.size
    val radius = CornerRadius(1.dp.toPx())
    val line = 2.dp.toPx()
    timeline.forEachIndexed { i, wakes ->
      val x = i * (width + gap)
      when {
        wakes > 0 -> {
          val h = size.height * (0.2f + 0.8f * wakes / most)
          drawRoundRect(bar, Offset(x, size.height - h), Size(width, h), radius)
        }
        i in tinted -> drawRoundRect(quiet, Offset(x, 0f), Size(width, size.height), radius)
        else -> drawRoundRect(baseline, Offset(x, size.height - line), Size(width, line), radius)
      }
    }
  }
}

/** The empty slices that belong to a run lasting at least [QUIET_TINT_MILLIS]. */
internal fun quietSlices(timeline: List<Int>, durationMillis: Long): Set<Int> {
  if (timeline.isEmpty()) return emptySet()
  val slice = durationMillis / timeline.size
  val tinted = mutableSetOf<Int>()
  var runStart = -1
  for (i in 0..timeline.size) {
    val empty = i < timeline.size && timeline[i] == 0
    if (empty && runStart < 0) runStart = i
    if (!empty && runStart >= 0) {
      if ((i - runStart) * slice >= QUIET_TINT_MILLIS) tinted += runStart until i
      runStart = -1
    }
  }
  return tinted
}

private const val QUIET_TINT_MILLIS = 45_000L

/** Phone battery a test used: "−1%", or "No change". */
internal fun phoneUsed(percent: Int): String = if (percent > 0) "−$percent%" else "No change"

/** A label above a big number with a muted note under it, for results and the recording card. */
@Composable
internal fun Figure(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  note: String? = null,
) {
  val colors = CassetteTheme.colors
  Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(label.uppercase(), style = CassetteTheme.type.status, color = colors.muted)
    Text(value, style = CassetteTheme.type.numeral, color = colors.text)
    if (note != null) Text(note, style = CassetteTheme.type.bodySmall, color = colors.muted)
  }
}
