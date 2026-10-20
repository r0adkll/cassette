package com.r0adkll.cassette.diagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.RideSummary
import com.r0adkll.cassette.capture.api.activeSession
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.RideTestRecordingScreen
import com.r0adkll.cassette.common.screens.RideTestResultScreen
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlinx.coroutines.launch

data class RideTestRecordingUiState(
  /** Null once the test has ended (stopped here or elsewhere). */
  val test: RideSummary?,
  /** The profile Stop puts back, when the test tried another. */
  val restores: String?,
  val stopping: Boolean,
  val eventSink: (RideTestRecordingEvent) -> Unit,
) : CircuitUiState

sealed interface RideTestRecordingEvent : CircuitUiEvent {
  data object Back : RideTestRecordingEvent

  data object Stop : RideTestRecordingEvent
}

/** The test that's recording, counting up each second. Stop opens its results. */
@CircuitInject(RideTestRecordingScreen::class, AppScope::class)
@Inject
class RideTestRecordingPresenter(
  private val navigator: Navigator,
  private val log: CaptureLog,
  private val runner: RideTestRunner,
  private val clock: Clock,
) : Presenter<RideTestRecordingUiState> {
  @Composable
  override fun present(): RideTestRecordingUiState {
    val scope = rememberCoroutineScope()
    val tests = rememberRideTests(log, clock, tick = true)
    val events by remember { log.events }.collectAsRetainedState(emptyList())
    var stopping by rememberRetained { mutableStateOf(false) }
    return RideTestRecordingUiState(
      test = tests.active,
      restores = events.activeSession()?.restoreProfile?.let(::profileLabel),
      stopping = stopping,
    ) { event ->
      when (event) {
        RideTestRecordingEvent.Back -> navigator.pop()
        RideTestRecordingEvent.Stop ->
          if (!stopping) {
            stopping = true
            scope.launch {
              val startedAt = runner.stop()
              navigator.pop()
              if (startedAt != null) navigator.goTo(RideTestResultScreen(startedAt))
            }
          }
      }
    }
  }
}

@CircuitInject(RideTestRecordingScreen::class, AppScope::class)
@Composable
fun RideTestRecordingUi(state: RideTestRecordingUiState, modifier: Modifier = Modifier) {
  val sink = state.eventSink
  val colors = CassetteTheme.colors
  val test = state.test
  DiagnosticsScaffold(
    test?.label?.let { "$it." } ?: "Ride test.",
    onBack = { sink(RideTestRecordingEvent.Back) },
    modifier,
  ) {
    if (test == null) {
      Text(
        "No test is recording. It may have been stopped from another screen.",
        style = CassetteTheme.type.body,
        color = colors.muted,
      )
      return@DiagnosticsScaffold
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(Modifier.size(10.dp).clip(CircleShape).background(colors.brand))
      Kicker("Recording · ${profileLabel(test.profile)}")
    }
    Panel {
      Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text("RIDING FOR", style = CassetteTheme.type.status, color = colors.muted)
          Text(
            clockFace(test.durationMillis),
            style = CassetteTheme.type.numeralLarge,
            color = colors.text,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Figure(
            "Last heard",
            if (test.wakes == 0) "Not yet" else "${spoken(test.silentTailMillis)} ago",
            Modifier.weight(1f),
          )
          Figure(
            "Longest quiet",
            if (test.wakes < 2) "—" else spoken(test.longestGapMillis),
            Modifier.weight(1f),
          )
        }
        WakeTimeline(
          test.timeline,
          test.durationMillis,
          "Wakes so far: ${test.wakes}, longest quiet ${spoken(test.longestGapMillis)}",
        )
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Icon(
        LucideIcons.Smartphone,
        contentDescription = null,
        tint = colors.muted,
        modifier = Modifier.size(20.dp),
      )
      Text(
        "You can lock the phone or close Cassette. It keeps listening, and the test keeps " +
          "running until you stop it.",
        style = CassetteTheme.type.body,
        color = colors.muted,
        modifier = Modifier.weight(1f),
      )
    }
    CassetteButton(
      if (state.stopping) "Stopping…" else "Stop and see results",
      onClick = { sink(RideTestRecordingEvent.Stop) },
      icon = LucideIcons.Square,
    )
    state.restores?.let {
      Text(
        "Stopping puts the scan back to $it.",
        style = CassetteTheme.type.bodySmall,
        color = colors.muted,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
