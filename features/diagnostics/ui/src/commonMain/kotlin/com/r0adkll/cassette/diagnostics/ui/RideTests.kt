package com.r0adkll.cassette.diagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.r0adkll.cassette.common.compose.AppPlatform
import com.r0adkll.cassette.common.compose.LocalAppPlatform
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.Chevron
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.SettingsRow
import com.r0adkll.cassette.common.compose.components.SettingsSection
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.RideTestRecordingScreen
import com.r0adkll.cassette.common.screens.RideTestResultScreen
import com.r0adkll.cassette.common.screens.RideTestSetupScreen
import com.r0adkll.cassette.common.screens.RideTestsScreen
import com.r0adkll.cassette.data.ble.ScanProfile
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch

data class RideTestsUiState(
  val active: RideSummary?,
  val past: List<RideSummary>,
  val comparison: List<ProfileResults>,
  val now: Instant,
  val confirmClear: Boolean,
  val eventSink: (RideTestsEvent) -> Unit,
) : CircuitUiState

sealed interface RideTestsEvent : CircuitUiEvent {
  data object Back : RideTestsEvent

  data object NewTest : RideTestsEvent

  data object OpenRecording : RideTestsEvent

  data class OpenResult(val startedAt: Long) : RideTestsEvent

  /** First tap asks, second clears. */
  data object ClearLog : RideTestsEvent
}

/** One profile's finished tests, added up for the comparison table. */
data class ProfileResults(
  val profile: ScanProfile,
  val rides: Int,
  val totalMillis: Long,
  /** Null until a test with this profile has finished. */
  val wakesPerHour: Double?,
  val longestGapMillis: Long?,
  /** Phone battery per 15 minutes of riding, where the phone reported it. */
  val phonePer15Min: Double?,
)

/**
 * Each offered profile's finished tests of at least [COMPARED_MILLIS], in the order the profiles
 * are offered. Shorter ones say little about a ride.
 */
internal fun compareProfiles(past: List<RideSummary>): List<ProfileResults> =
  ScanProfile.entries.map { profile ->
    val rides = past.filter {
      it.profile == profile.name && it.endedAt != null && it.durationMillis >= COMPARED_MILLIS
    }
    val total = rides.sumOf { it.durationMillis }
    val withBattery = rides.filter { it.phoneBatteryUsed != null }
    val batteryMillis = withBattery.sumOf { it.durationMillis }
    ProfileResults(
      profile,
      rides = rides.size,
      totalMillis = total,
      wakesPerHour = if (total > 0) rides.sumOf { it.wakes } * 3_600_000.0 / total else null,
      longestGapMillis = rides.filter { it.wakes >= 2 }.maxOfOrNull { it.longestGapMillis },
      phonePer15Min =
        if (batteryMillis > 0)
          withBattery.sumOf { it.phoneBatteryUsed!! } * 900_000.0 / batteryMillis
        else null,
    )
  }

/**
 * Ride tests (decision 0009): the one recording, how the scan profiles compare, and past tests. New
 * tests start with a short setup ([RideTestSetupScreen]).
 */
@CircuitInject(RideTestsScreen::class, AppScope::class)
@Inject
class RideTestsPresenter(
  private val navigator: Navigator,
  private val log: CaptureLog,
  private val clock: Clock,
) : Presenter<RideTestsUiState> {

  @Composable
  override fun present(): RideTestsUiState {
    val scope = rememberCoroutineScope()
    val tests = rememberRideTests(log, clock)
    var confirmClear by remember { mutableStateOf(false) }
    return RideTestsUiState(
      active = tests.active,
      past = tests.past,
      comparison = compareProfiles(tests.past),
      now = clock.now(),
      confirmClear = confirmClear,
    ) { event ->
      when (event) {
        RideTestsEvent.Back -> navigator.pop()
        RideTestsEvent.NewTest -> navigator.goTo(RideTestSetupScreen)
        RideTestsEvent.OpenRecording -> navigator.goTo(RideTestRecordingScreen)
        is RideTestsEvent.OpenResult -> navigator.goTo(RideTestResultScreen(event.startedAt))
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
  val ios = LocalAppPlatform.current == AppPlatform.Ios
  DiagnosticsScaffold("Ride tests.", onBack = { sink(RideTestsEvent.Back) }, modifier) {
    val active = state.active
    if (active != null) {
      Panel(onClick = { sink(RideTestsEvent.OpenRecording) }) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(Modifier.size(10.dp).clip(CircleShape).background(colors.brand))
          Column(Modifier.weight(1f)) {
            Text(active.label, style = CassetteTheme.type.itemTitle, color = colors.text)
            Text(
              "Recording for ${spoken(active.durationMillis)} · ${profileLabel(active.profile)}",
              style = CassetteTheme.type.bodySmall,
              color = colors.muted,
            )
          }
          Chevron()
        }
      }
    } else {
      Text(
        "Ride with the phone in your pocket, then see whether Cassette kept hearing your bike.",
        style = CassetteTheme.type.body,
        color = colors.muted,
      )
      CassetteButton(
        "New ride test",
        onClick = { sink(RideTestsEvent.NewTest) },
        icon = LucideIcons.Route,
      )
    }
    // iOS sets the scan's cadence itself, so there's only one way to test (decision 0017).
    if (!ios) Comparison(state.comparison)
    if (state.past.isNotEmpty())
      SettingsSection("Past tests") {
        state.past.forEachIndexed { i, test ->
          SettingsRow(
            test.label,
            subtitle =
              "${agoShort(state.now - Instant.fromEpochMilliseconds(test.startedAt))} · " +
                "${spoken(test.durationMillis)} · " +
                profileLabel(test.profile),
            onClick = { sink(RideTestsEvent.OpenResult(test.startedAt)) },
            divider = i < state.past.lastIndex,
            trailing = {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                VerdictLabel(test.verdict)
                Chevron()
              }
            },
          )
        }
      }
    if (active == null)
      CassetteButton(
        if (state.confirmClear) "Tap again to clear the log" else "Clear capture log",
        onClick = { sink(RideTestsEvent.ClearLog) },
        primary = false,
      )
  }
}

/** Low power against Batched 30 s: rides, wakes an hour, the longest quiet and phone battery. */
@Composable
private fun Comparison(results: List<ProfileResults>) {
  val colors = CassetteTheme.colors
  Kicker(results.joinToString(" against ") { it.profile.label })
  Panel {
    @Composable
    fun Line(label: String, value: (ProfileResults) -> String?) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          label,
          style = CassetteTheme.type.bodySmall,
          color = colors.muted,
          modifier = Modifier.weight(1f),
        )
        results.forEach { result ->
          val text = value(result)
          Text(
            text ?: "—",
            style = CassetteTheme.type.itemTitle,
            color = if (text != null) colors.text else colors.muted,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
    Row {
      Box(Modifier.weight(1f))
      results.forEach {
        Text(
          it.profile.label.uppercase(),
          style = CassetteTheme.type.status,
          color = if (it.rides > 0) colors.text else colors.muted,
          modifier = Modifier.weight(1f),
        )
      }
    }
    Line("Rides") { if (it.rides > 0) "${it.rides} · ${spoken(it.totalMillis)}" else "None yet" }
    Line("Wakes an hour") { r -> r.wakesPerHour?.let { "About ${roundWakes(it)}" } }
    Line("Longest quiet") { r -> r.longestGapMillis?.let(::spoken) }
    Line("Phone battery") { r ->
      r.phonePer15Min?.let { if (it < 0.05) "No change" else "−${oneDecimal(it)}% / 15 min" }
    }
  }
  results
    .firstOrNull { it.rides == 0 }
    ?.let {
      Text(
        "Ride once for 5 minutes or more with ${it.profile.label} to fill in its column.",
        style = CassetteTheme.type.bodySmall,
        color = colors.muted,
        modifier = Modifier.fillMaxWidth(),
      )
    }
}

/** Wakes an hour to two significant figures: "1,000", "120". */
private fun roundWakes(perHour: Double): String {
  val n = perHour.roundToInt()
  val rounded =
    when {
      n >= 1_000 -> (n + 50) / 100 * 100
      n >= 100 -> (n + 5) / 10 * 10
      else -> n
    }
  return if (rounded >= 1_000) "${rounded / 1_000},${(rounded % 1_000).toString().padStart(3, '0')}"
  else rounded.toString()
}

private fun oneDecimal(value: Double): String {
  val tenths = (value * 10).roundToInt()
  return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

private const val COMPARED_MILLIS = 5 * 60_000L
