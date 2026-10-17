package com.r0adkll.cassette.onboarding.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.common.compose.AppPermission
import com.r0adkll.cassette.common.compose.LocalPermissionController
import com.r0adkll.cassette.common.compose.PermissionState
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.IconCircleButton
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.icons.CassetteMarks
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteRed
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.AddBikeScreen
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.common.screens.OnboardingPermissionsScreen
import com.r0adkll.cassette.common.screens.OnboardingScreen
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch

private val MaxContentWidth = 480.dp

data class WelcomeUiState(val eventSink: (WelcomeEvent) -> Unit) : CircuitUiState

sealed interface WelcomeEvent : CircuitUiEvent {
  data object GetStarted : WelcomeEvent
}

/** First run (decision 0011): the welcome, then the permissions screen. */
@CircuitInject(OnboardingScreen::class, AppScope::class)
@Inject
class WelcomePresenter(private val navigator: Navigator) : Presenter<WelcomeUiState> {
  @Composable
  override fun present() = WelcomeUiState { event ->
    when (event) {
      WelcomeEvent.GetStarted -> navigator.goTo(OnboardingPermissionsScreen)
    }
  }
}

data class PermissionsUiState(val eventSink: (PermissionsEvent) -> Unit) : CircuitUiState

sealed interface PermissionsEvent : CircuitUiEvent {
  data object Back : PermissionsEvent

  /** Always allowed: anything not granted can be fixed later in Settings. */
  data object Continue : PermissionsEvent
}

/**
 * Asking is done by the UI (the platform's [LocalPermissionController]); the presenter only
 * finishes onboarding. The bike screen sits under add-bike, so closing it lands somewhere sensible.
 */
@CircuitInject(OnboardingPermissionsScreen::class, AppScope::class)
@Inject
class PermissionsPresenter(
  private val navigator: Navigator,
  private val settings: CassetteSettings,
  private val capture: CaptureController,
) : Presenter<PermissionsUiState> {
  @Composable
  override fun present(): PermissionsUiState {
    val scope = rememberCoroutineScope()
    return PermissionsUiState { event ->
      when (event) {
        PermissionsEvent.Back -> navigator.pop()
        PermissionsEvent.Continue ->
          scope.launch {
            settings.setOnboarded()
            capture.ensureBackgroundCapture("onboarding finished")
            navigator.resetRoot(BikeScreen())
            navigator.goTo(AddBikeScreen)
          }
      }
    }
  }
}

/** The poster: a red block with the promise, then what Cassette does. */
@CircuitInject(OnboardingScreen::class, AppScope::class)
@Composable
fun WelcomeUi(state: WelcomeUiState, modifier: Modifier = Modifier) {
  val colors = CassetteTheme.colors
  // The app pads every screen inside the system bars; the poster's red runs up under the status
  // bar.
  val statusBar = WindowInsets.safeDrawing.getTop(LocalDensity.current).toFloat()
  Column(modifier.fillMaxSize()) {
    Box(
      Modifier.fillMaxWidth().drawBehind {
        drawRect(
          CassetteRed,
          topLeft = Offset(0f, -statusBar),
          size = Size(size.width, size.height + statusBar),
        )
      },
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        Modifier.widthIn(max = MaxContentWidth)
          .fillMaxWidth()
          .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Text("CASSETTE", style = CassetteTheme.type.label, color = Color.White)
        Row(verticalAlignment = Alignment.Top) {
          Text(
            "Know\nbefore\nyou ride.",
            style = CassetteTheme.type.display.copy(fontSize = 66.sp, lineHeight = 0.88.em),
            color = Color.White,
          )
          Spacer(Modifier.weight(1f))
          // The tape, drawn straight on the red: the poster is its shell.
          Image(
            rememberVectorPainter(CassetteMarks.Tape),
            contentDescription = null,
            modifier = Modifier.size(112.dp),
          )
        }
        Text(
          "Battery levels for your SRAM AXS bike, without the guesswork.",
          style = CassetteTheme.type.body,
          color = Color.White,
        )
      }
    }
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier.widthIn(max = MaxContentWidth)
          .fillMaxSize()
          .padding(horizontal = 20.dp)
          .padding(top = 8.dp, bottom = 24.dp)
      ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
          Point(
            "01",
            "Hears the bike when it wakes",
            "No pairing, no app open: a shake or a shift is enough.",
          )
          Point("02", "Shows it at a glance", "Widgets, Quick Settings and notifications.")
          Point(
            "03",
            "Warns you before a ride",
            "A nudge when the bike wakes and something is low.",
          )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          CassetteButton("Get started", onClick = { state.eventSink(WelcomeEvent.GetStarted) })
          Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(LucideIcons.Info, null, tint = colors.muted, modifier = Modifier.size(13.dp))
            Text(
              "Unofficial · not affiliated with SRAM",
              style = CassetteTheme.type.bodySmall,
              color = colors.muted,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun Point(number: String, title: String, text: String) {
  val colors = CassetteTheme.colors
  Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
  Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    Text(
      number,
      style = CassetteTheme.type.label.copy(fontSize = 14.sp),
      color = colors.muted,
      modifier = Modifier.width(22.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = CassetteTheme.type.itemTitle, color = colors.text)
      Text(text, style = CassetteTheme.type.bodySmall, color = colors.muted)
    }
  }
}

/** The checklist: each permission asks on its own, and Continue is always there. */
@CircuitInject(OnboardingPermissionsScreen::class, AppScope::class)
@Composable
fun PermissionsUi(state: PermissionsUiState, modifier: Modifier = Modifier) {
  val colors = CassetteTheme.colors
  val controller = LocalPermissionController.current
  val states by controller.states.collectAsState()
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      Modifier.widthIn(max = MaxContentWidth)
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(
          LucideIcons.ArrowLeft,
          "Back",
          onClick = { state.eventSink(PermissionsEvent.Back) },
        )
        Spacer(Modifier.weight(1f))
        Kicker("Set up")
      }
      Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text("Two things to allow.", style = CassetteTheme.type.display, color = colors.text)
        Text(
          "Cassette runs quietly in the background. These let it hear your bike and tell you when to charge.",
          style = CassetteTheme.type.body,
          color = colors.muted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          PermissionCard(
            icon = LucideIcons.BluetoothSearching,
            title = "Nearby devices",
            text =
              "Hears your bike's AXS radio when it wakes, even with the app closed. It never uses your location.",
            state = states[AppPermission.Nearby] ?: PermissionState.Askable,
            onAllow = { controller.request(AppPermission.Nearby) },
            onSettings = controller::openSettings,
          )
          PermissionCard(
            icon = LucideIcons.Bell,
            title = "Notifications",
            text =
              "Tells you when a battery runs low, and nudges you before a ride if something needs charging.",
            state = states[AppPermission.Notifications] ?: PermissionState.Askable,
            onAllow = { controller.request(AppPermission.Notifications) },
            onSettings = controller::openSettings,
          )
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(LucideIcons.Info, null, tint = colors.muted, modifier = Modifier.size(15.dp))
          Text(
            "You can change these any time in Settings.",
            style = CassetteTheme.type.bodySmall,
            color = colors.muted,
          )
        }
        CassetteButton("Continue", onClick = { state.eventSink(PermissionsEvent.Continue) })
      }
    }
  }
}

@Composable
private fun PermissionCard(
  icon: ImageVector,
  title: String,
  text: String,
  state: PermissionState,
  onAllow: () -> Unit,
  onSettings: () -> Unit,
) {
  val colors = CassetteTheme.colors
  val granted = state == PermissionState.Granted
  Panel {
    Row(
      Modifier.padding(top = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        Modifier.size(40.dp)
          .clip(CircleShape)
          .background(if (granted) colors.good else colors.track),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          icon,
          null,
          tint = if (granted) colors.onInk else colors.text,
          modifier = Modifier.size(20.dp),
        )
      }
      Text(
        title,
        style = CassetteTheme.type.itemTitle,
        color = colors.text,
        modifier = Modifier.weight(1f),
      )
      when (state) {
        PermissionState.Granted ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(LucideIcons.Check, null, tint = colors.good, modifier = Modifier.size(16.dp))
            Text("ALLOWED", style = ChipText, color = colors.good)
          }
        PermissionState.Askable -> Chip("Allow", primary = true, onClick = onAllow)
        PermissionState.Blocked ->
          Chip("Settings", primary = false, icon = LucideIcons.Settings, onClick = onSettings)
      }
    }
    Text(text, style = CassetteTheme.type.bodySmall, color = colors.muted)
    if (state == PermissionState.Blocked)
      Text(
        "Turned off. Open Settings to allow it.",
        style = CassetteTheme.type.bodySmall,
        color = colors.low,
      )
    Spacer(Modifier.height(2.dp))
  }
}

private val ChipText
  @Composable get() = CassetteTheme.type.button.copy(fontSize = 14.sp)

@Composable
private fun Chip(text: String, primary: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
  val colors = CassetteTheme.colors
  val shape = RoundedCornerShape(17.dp)
  val content = if (primary) colors.onInk else colors.text
  Row(
    Modifier.height(34.dp)
      .clip(shape)
      .then(
        if (primary) Modifier.background(colors.ink) else Modifier.border(1.dp, colors.line, shape)
      )
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (icon != null) Icon(icon, null, tint = content, modifier = Modifier.size(15.dp))
    Text(text.uppercase(), style = ChipText, color = content)
  }
}
