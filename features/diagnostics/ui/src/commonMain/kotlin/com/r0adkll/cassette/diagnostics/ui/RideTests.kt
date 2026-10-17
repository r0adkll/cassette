package com.r0adkll.cassette.diagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.PhoneBattery
import com.r0adkll.cassette.capture.api.RideSummary
import com.r0adkll.cassette.capture.api.activeSession
import com.r0adkll.cassette.capture.api.summarizeSessions
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.RideTestsScreen
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class RideTestsUiState(
  val active: RideSummary?,
  val sessions: List<RideSummary>,
  val label: String,
  val confirmClear: Boolean,
  val eventSink: (RideTestsEvent) -> Unit,
) : CircuitUiState

sealed interface RideTestsEvent : CircuitUiEvent {
  data object Back : RideTestsEvent

  data class LabelChanged(val label: String) : RideTestsEvent

  data object Start : RideTestsEvent

  data object Stop : RideTestsEvent

  /** First tap asks, second clears. */
  data object ClearLog : RideTestsEvent
}

/**
 * Ride tests (decision 0009): mark a session, ride, and see whether the background scan kept waking
 * the app and whether reads worked. Starting one also arms background capture.
 */
@CircuitInject(RideTestsScreen::class, AppScope::class)
@Inject
class RideTestsPresenter(
  private val navigator: Navigator,
  private val log: CaptureLog,
  private val settings: CassetteSettings,
  private val capture: CaptureController,
  private val battery: PhoneBattery,
  private val clock: Clock,
) : Presenter<RideTestsUiState> {

  @Composable
  override fun present(): RideTestsUiState {
    val scope = rememberCoroutineScope()
    val events by remember { log.events }.collectAsRetainedState(emptyList())
    var label by remember { mutableStateOf("Ride") }
    var confirmClear by remember { mutableStateOf(false) }
    val sessions = summarizeSessions(events, clock.now().toEpochMilliseconds())
    val activeStart = events.activeSession()

    return RideTestsUiState(
      active = activeStart?.let { start -> sessions.firstOrNull { it.startedAt == start.at } },
      sessions = sessions.filter { it.startedAt != activeStart?.at },
      label = label,
      confirmClear = confirmClear,
    ) { event ->
      when (event) {
        RideTestsEvent.Back -> navigator.pop()
        is RideTestsEvent.LabelChanged -> label = event.label
        RideTestsEvent.Start ->
          scope.launch {
            val now = clock.now().toEpochMilliseconds()
            log.append(
              CaptureEvent.SessionStarted(
                now,
                label.ifBlank { "Ride" },
                settings.scanProfile.first().name,
                battery.percent(),
              )
            )
            capture.ensureBackgroundCapture("ride test started")
          }
        RideTestsEvent.Stop ->
          scope.launch {
            log.append(
              CaptureEvent.SessionEnded(clock.now().toEpochMilliseconds(), battery.percent())
            )
          }
        RideTestsEvent.ClearLog ->
          if (!confirmClear) confirmClear = true
          else {
            confirmClear = false
            scope.launch { log.clear() }
          }
      }
    }
  }
}

@CircuitInject(RideTestsScreen::class, AppScope::class)
@Composable
fun RideTestsUi(state: RideTestsUiState, modifier: Modifier = Modifier) {
  val sink = state.eventSink
  val colors = CassetteTheme.colors
  DiagnosticsScaffold("Ride tests.", onBack = { sink(RideTestsEvent.Back) }, modifier) {
    Text(
      "Start, ride with the app closed, stop when you're back. The summary shows whether the background " +
        "scan kept waking Cassette and whether battery reads worked.",
      style = CassetteTheme.type.body,
      color = colors.muted,
    )
    val active = state.active
    if (active != null) {
      Summary(active, running = true)
      CassetteButton("Stop ride test", onClick = { sink(RideTestsEvent.Stop) })
    } else {
      Kicker("Label")
      BasicTextField(
        value = state.label,
        onValueChange = { sink(RideTestsEvent.LabelChanged(it)) },
        singleLine = true,
        textStyle = CassetteTheme.type.itemTitle.copy(color = colors.text),
        cursorBrush = SolidColor(colors.brand),
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.panel)
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
      )
      CassetteButton(
        "Start ride test",
        onClick = { sink(RideTestsEvent.Start) },
        icon = LucideIcons.Route,
      )
    }
    if (state.sessions.isNotEmpty()) Kicker("Past sessions")
    state.sessions.forEach { Summary(it, running = false) }
    CassetteButton(
      if (state.confirmClear) "Tap again to clear the log" else "Clear capture log",
      onClick = { sink(RideTestsEvent.ClearLog) },
      primary = false,
    )
  }
}

@Composable
private fun Summary(summary: RideSummary, running: Boolean) {
  val colors = CassetteTheme.colors
  Panel {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      Text(
        summary.label,
        style = CassetteTheme.type.itemTitle,
        color = colors.text,
        modifier = Modifier.weight(1f),
      )
      Text(
        if (running) "RECORDING" else summary.profile,
        style = CassetteTheme.type.status,
        color = if (running) colors.brand else colors.muted,
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      MonoLine("length", duration(summary.durationMillis))
      MonoLine("wakes", "${summary.wakes} (${summary.wakesPerHour.roundToInt()}/h)")
      MonoLine("longest gap", duration(summary.longestGapMillis))
      MonoLine("quiet at end", duration(summary.silentTailMillis))
      MonoLine(
        "reads",
        "${summary.gattOk}/${summary.gattAttempts} ok" +
          (summary.gattAverageMillis?.let { ", avg ${it / 100 / 10.0} s" } ?: ""),
      )
      if (summary.budgetExceeded > 0) MonoLine("out of time", summary.budgetExceeded.toString())
      if (summary.armFailures.isNotEmpty())
        MonoLine("arm failed", summary.armFailures.distinct().joinToString())
      summary.phoneBatteryUsed?.let { MonoLine("phone", "−$it%") }
      summary.components.forEach {
        MonoLine(
          it.key.take(14),
          "heard in ${it.wakesHeardIn} · gap ${duration(it.longestSilenceMillis)}" +
            if (it.readsOk > 0) " · ${it.readsOk} reads" else "",
        )
      }
    }
  }
}

private fun duration(millis: Long): String {
  val d = millis.milliseconds
  return when {
    d.inWholeMinutes < 1 -> "${d.inWholeSeconds} s"
    d.inWholeHours < 1 -> "${d.inWholeMinutes} min ${d.inWholeSeconds % 60} s"
    else -> "${d.inWholeHours} h ${d.inWholeMinutes % 60} min"
  }
}
