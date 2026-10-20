package com.r0adkll.cassette.diagnostics.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.common.compose.AppPermission
import com.r0adkll.cassette.common.compose.AppPlatform
import com.r0adkll.cassette.common.compose.LocalAppPlatform
import com.r0adkll.cassette.common.compose.LocalPermissionController
import com.r0adkll.cassette.common.compose.PermissionState
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.SettingsRow
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.common.screens.RideTestRecordingScreen
import com.r0adkll.cassette.common.screens.RideTestSetupScreen
import com.r0adkll.cassette.data.ble.ScanProfile
import com.r0adkll.cassette.settings.api.CassetteSettings
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
import kotlin.time.Instant
import kotlinx.coroutines.launch

data class RideTestSetupUiState(
  /** 1: what to test and the name; 2: the checks before you go. */
  val step: Int,
  /** The everyday profile: the first choice, and what comes back after a test of another. */
  val usual: ScanProfile,
  val profile: ScanProfile,
  val label: String,
  val checks: ReadyChecks,
  val starting: Boolean,
  val eventSink: (RideTestSetupEvent) -> Unit,
) : CircuitUiState {
  /** A mode is always chosen, so only the name can hold Next back. */
  val canContinue: Boolean
    get() = label.isNotBlank()
}

/** What step 2 checks; Nearby devices comes from the UI's permission controller. */
data class ReadyChecks(
  val bluetoothReady: Boolean,
  val backgroundCapture: Boolean,
  /** The bike that opens first, and when it was last heard; null without a bike. */
  val bikeName: String?,
  val bikeLastSeen: Instant?,
  val now: Instant,
)

sealed interface RideTestSetupEvent : CircuitUiEvent {
  /** Step 2 goes back to step 1; step 1 leaves. */
  data object Back : RideTestSetupEvent

  data class ProfileChosen(val profile: ScanProfile) : RideTestSetupEvent

  data class LabelChanged(val label: String) : RideTestSetupEvent

  data object Next : RideTestSetupEvent

  data object Start : RideTestSetupEvent
}

/**
 * A new ride test's setup (decision 0018): step 1, the mode and the ride's name; step 2, what's
 * ready before the ride. Both are one screen so the steps slide between each other. Start opens the
 * recording, which isn't a step: it's the same screen however it's reached.
 */
@CircuitInject(RideTestSetupScreen::class, AppScope::class)
@Inject
class RideTestSetupPresenter(
  private val navigator: Navigator,
  private val settings: CassetteSettings,
  private val bikes: BikeRepository,
  private val capture: CaptureController,
  private val runner: RideTestRunner,
  private val clock: Clock,
) : Presenter<RideTestSetupUiState> {
  @Composable
  override fun present(): RideTestSetupUiState {
    val scope = rememberCoroutineScope()
    val usual by remember { settings.scanProfile }.collectAsRetainedState(ScanProfile.AllLowPower)
    val background by remember { settings.backgroundCapture }.collectAsRetainedState(true)
    val bike by remember { bikes.observeOverview(null) }.collectAsRetainedState(null)
    var step by rememberRetained { mutableIntStateOf(1) }
    var chosen by rememberRetained { mutableStateOf<ScanProfile?>(null) }
    var label by rememberRetained { mutableStateOf("") }
    var starting by rememberRetained { mutableStateOf(false) }
    val profile = chosen ?: usual
    val checks =
      ReadyChecks(
        bluetoothReady = capture.isReady,
        backgroundCapture = background,
        bikeName = bike?.bike?.name,
        bikeLastSeen = bike?.bike?.lastSeenAt,
        now = clock.now(),
      )
    return RideTestSetupUiState(step, usual, profile, label, checks, starting) { event ->
      when (event) {
        RideTestSetupEvent.Back -> if (step > 1) step-- else navigator.pop()
        is RideTestSetupEvent.ProfileChosen -> chosen = event.profile
        is RideTestSetupEvent.LabelChanged -> label = event.label
        RideTestSetupEvent.Next -> if (label.isNotBlank()) step = 2
        RideTestSetupEvent.Start ->
          if (!starting && label.isNotBlank()) {
            starting = true
            scope.launch {
              runner.start(label.trim(), profile)
              // Setup goes, so Back from the recording lands on the list.
              navigator.pop()
              navigator.goTo(RideTestRecordingScreen)
            }
          }
      }
    }
  }
}

@CircuitInject(RideTestSetupScreen::class, AppScope::class)
@Composable
fun RideTestSetupUi(state: RideTestSetupUiState, modifier: Modifier = Modifier) {
  val sink = state.eventSink
  // The name's keyboard closes as step 2 slides in, rather than covering its checks.
  val focus = LocalFocusManager.current
  LaunchedEffect(state.step) { focus.clearFocus() }
  // System back on step 2 goes back a step, as the top bar's Back does.
  NavigationBackHandler(
    rememberNavigationEventState(NavigationEventInfo.None),
    isBackEnabled = state.step > 1,
    onBackCompleted = { sink(RideTestSetupEvent.Back) },
  )
  DiagnosticsScaffold("Ride test.", onBack = { sink(RideTestSetupEvent.Back) }, modifier) {
    AnimatedContent(
      targetState = state.step,
      transitionSpec = {
        // Forward slides in from the end, back from the start; the title above stays put.
        val forward = targetState > initialState
        val enter =
          slideInHorizontally(tween(StepMillis)) { if (forward) it / 3 else -it / 3 } +
            fadeIn(tween(StepMillis))
        val exit =
          slideOutHorizontally(tween(StepMillis)) { if (forward) -it / 3 else it / 3 } +
            fadeOut(tween(StepMillis / 2))
        (enter togetherWith exit).using(SizeTransform(clip = false))
      },
      label = "step",
    ) { step ->
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (step) {
          1 -> WhatToTest(state)
          else -> BeforeYouGo(state)
        }
      }
    }
  }
}

@Composable
private fun ColumnScope.WhatToTest(state: RideTestSetupUiState) {
  val sink = state.eventSink
  val colors = CassetteTheme.colors
  val ios = LocalAppPlatform.current == AppPlatform.Ios
  Kicker("Step 1 of 2 · What to test")
  Text(
    "Ride with the phone in your pocket. Afterwards Cassette tells you whether it kept " +
      "hearing your bike.",
    style = CassetteTheme.type.body,
    color = colors.muted,
  )
  // iOS sets the scan's cadence itself: there's only the everyday check (decision 0017).
  if (!ios)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      ScanProfile.entries
        .sortedByDescending { it == state.usual }
        .forEach { profile ->
          Choice(
            title = "${profile.label} mode",
            detail =
              if (profile == state.usual) "${profile.behaviour} Your everyday scan."
              else "${profile.behaviour} ${state.usual.label} comes back when you stop.",
            selected = profile == state.profile,
            onSelect = { sink(RideTestSetupEvent.ProfileChosen(profile)) },
          )
        }
    }
  Kicker("Name this ride", Modifier.padding(top = 4.dp))
  NameField(state.label, onChange = { sink(RideTestSetupEvent.LabelChanged(it)) })
  CassetteButton(
    "Next",
    onClick = { sink(RideTestSetupEvent.Next) },
    enabled = state.canContinue,
  )
}

@Composable
private fun ColumnScope.BeforeYouGo(state: RideTestSetupUiState) {
  val sink = state.eventSink
  val checks = state.checks
  val nearby by LocalPermissionController.current.states.collectAsState()
  Kicker("Step 2 of 2 · Before you go")
  Panel {
    Check(
      if (checks.bluetoothReady) "Bluetooth is on" else "Bluetooth is off",
      if (checks.bluetoothReady) "Ready to listen" else "Turn Bluetooth on before you ride",
      LucideIcons.Bluetooth,
      ok = checks.bluetoothReady,
    )
    val allowed =
      nearby[AppPermission.Nearby] != PermissionState.Blocked &&
        nearby[AppPermission.Nearby] != PermissionState.Askable
    Check(
      if (allowed) "Nearby devices allowed" else "Nearby devices not allowed",
      if (allowed) "Needed to hear the bike in the background"
      else "Allow it in the phone's settings, or Cassette can't listen",
      LucideIcons.Radio,
      ok = allowed,
    )
    Check(
      if (checks.backgroundCapture) "Background capture on" else "Background capture off",
      if (checks.backgroundCapture) "Cassette listens while the phone is locked"
      else "Turn it on in Settings → Capture before you start",
      LucideIcons.Activity,
      ok = checks.backgroundCapture,
    )
    val seen = checks.bikeLastSeen
    Check(
      when {
        checks.bikeName == null -> "No bike yet"
        seen == null -> "${checks.bikeName} not heard yet"
        else -> "${checks.bikeName} heard ${agoShort(checks.now - seen)}"
      },
      if (checks.bikeName == null) "Add a bike first" else "Wake it with a shift if it's asleep",
      LucideIcons.Bike,
      ok = checks.bikeName != null && seen != null,
      divider = false,
    )
  }
  Kicker("On the ride", Modifier.padding(top = 4.dp))
  Steps(
    "Lock the phone and put it in a pocket or bag, as on any ride.",
    "Ride for at least 15 minutes. Longer rides, and a stop or two, tell more.",
    "Back home, open Cassette and tap Stop to see how it went.",
  )
  CassetteButton(
    if (state.starting) "Starting…" else "Start ride test",
    onClick = { sink(RideTestSetupEvent.Start) },
    icon = LucideIcons.Route,
  )
}

/** A step's slide. */
private const val StepMillis = 320

/** What the mode does, for its choice in step 1. */
private val ScanProfile.behaviour: String
  get() =
    when (this) {
      ScanProfile.AllLowPower -> "Wakes Cassette for every advert heard."
      ScanProfile.Batched30s -> "Wakes Cassette at most every 30 s, which may save phone battery."
    }

/** A choice drawn as a panel with a radio dot, outlined in the text colour when [selected]. */
@Composable
private fun Choice(title: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
  val colors = CassetteTheme.colors
  Panel(
    Modifier.semantics {
      this.selected = selected
      role = Role.RadioButton
    },
    onClick = onSelect,
    selected = selected,
  ) {
    Row(
      Modifier.padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        Modifier.padding(top = 2.dp)
          .size(20.dp)
          .clip(CircleShape)
          .border(2.dp, if (selected) colors.text else colors.muted, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(colors.text))
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = CassetteTheme.type.itemTitle, color = colors.text)
        Text(detail, style = CassetteTheme.type.bodySmall, color = colors.muted)
      }
    }
  }
}

/** The ride's name; while empty, an example in the muted colour. */
@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
  val colors = CassetteTheme.colors
  val style = CassetteTheme.type.itemTitle
  BasicTextField(
    value = value,
    onValueChange = onChange,
    singleLine = true,
    textStyle = style.copy(color = colors.text),
    cursorBrush = SolidColor(colors.brand),
    keyboardOptions =
      KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences,
        imeAction = ImeAction.Done,
      ),
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(colors.panel)
        .border(1.dp, colors.line, RoundedCornerShape(16.dp))
        .padding(horizontal = 16.dp, vertical = 14.dp),
    decorationBox = { field ->
      Box {
        // Regular weight, so the example never reads as a name already typed.
        if (value.isEmpty())
          Text(
            "Morning commute",
            style = style.copy(fontWeight = FontWeight.Normal),
            color = colors.muted,
          )
        field()
      }
    },
  )
}

/** A check: its icon, what it is and what to do, and a green tick or an amber warning. */
@Composable
private fun Check(
  title: String,
  detail: String,
  icon: ImageVector,
  ok: Boolean,
  divider: Boolean = true,
) {
  val tone = if (ok) StatusTone.Good else StatusTone.Low
  SettingsRow(
    title,
    subtitle = detail,
    icon = icon,
    divider = divider,
    trailing = {
      Icon(
        if (ok) LucideIcons.CircleCheck else LucideIcons.TriangleAlert,
        contentDescription = if (ok) "Ready" else "Needs attention",
        tint = CassetteTheme.colors.status(tone),
        modifier = Modifier.size(22.dp),
      )
    },
  )
}

/** Numbered steps, one line of advice each. */
@Composable
private fun ColumnScope.Steps(vararg steps: String) {
  val colors = CassetteTheme.colors
  steps.forEachIndexed { i, step ->
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Box(
        Modifier.size(28.dp).clip(CircleShape).background(colors.track),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          "${i + 1}",
          style = CassetteTheme.type.numeral.copy(fontSize = CassetteTheme.type.itemTitle.fontSize),
          color = colors.text,
        )
      }
      Text(
        step,
        style = CassetteTheme.type.body,
        color = colors.text,
        modifier = Modifier.weight(1f).padding(top = 4.dp),
      )
    }
  }
}
