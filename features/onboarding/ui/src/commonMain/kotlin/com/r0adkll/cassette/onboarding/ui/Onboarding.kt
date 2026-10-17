package com.r0adkll.cassette.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.common.compose.AppPermission
import com.r0adkll.cassette.common.compose.LocalPermissionController
import com.r0adkll.cassette.common.compose.PermissionState
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.AddBikeScreen
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.common.screens.OnboardingScreen
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch

enum class OnboardingStep {
  Welcome,
  Nearby,
  Notifications,
}

data class OnboardingUiState(val step: OnboardingStep, val eventSink: (OnboardingEvent) -> Unit) :
  CircuitUiState

sealed interface OnboardingEvent : CircuitUiEvent {
  /** The step is done: allowed, skipped ("Not now"), or welcome read. */
  data object Next : OnboardingEvent
}

/**
 * First run (decision 0011): welcome, then one explainer per permission, then add-bike. The bike
 * screen sits under add-bike, so closing it lands somewhere sensible.
 */
@CircuitInject(OnboardingScreen::class, AppScope::class)
@Inject
class OnboardingPresenter(
  private val navigator: Navigator,
  private val settings: CassetteSettings,
  private val capture: CaptureController,
) : Presenter<OnboardingUiState> {

  @Composable
  override fun present(): OnboardingUiState {
    val scope = rememberCoroutineScope()
    var index by rememberRetained { mutableIntStateOf(0) }
    val step = OnboardingStep.entries[index]
    return OnboardingUiState(step) { event ->
      when (event) {
        OnboardingEvent.Next ->
          if (index < OnboardingStep.entries.lastIndex) index++
          else
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

@CircuitInject(OnboardingScreen::class, AppScope::class)
@Composable
fun OnboardingUi(state: OnboardingUiState, modifier: Modifier = Modifier) {
  val permissions = LocalPermissionController.current
  val next = { state.eventSink(OnboardingEvent.Next) }
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      Modifier.widthIn(max = 480.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Kicker("Cassette", Modifier.weight(1f))
        Kicker("${state.step.ordinal + 1} of ${OnboardingStep.entries.size}")
      }
      Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        when (state.step) {
          OnboardingStep.Welcome -> Welcome()
          OnboardingStep.Nearby ->
            Explainer(
              title = "Hear the bike.",
              body =
                "Cassette listens for your bike's Bluetooth adverts and reads its hub when it wakes, " +
                  "even with the app closed. It never uses your location.",
              icon = LucideIcons.Bluetooth,
              permissionName = "Nearby devices",
              permissionUse = "Hear the bike when it wakes",
            )
          OnboardingStep.Notifications ->
            Explainer(
              title = "Know when to charge.",
              body =
                "A notification when something gets low, a nudge when you wake the bike before a ride, " +
                  "and a reminder if it's been put away for weeks. Each can be turned off in Settings.",
              icon = LucideIcons.Bell,
              permissionName = "Notifications",
              permissionUse = "Low battery alerts and pre-ride nudges",
            )
        }
      }
      when (state.step) {
        OnboardingStep.Welcome -> CassetteButton("Get started", onClick = next)
        OnboardingStep.Nearby -> PermissionButtons(AppPermission.Nearby, next)
        OnboardingStep.Notifications -> PermissionButtons(AppPermission.Notifications, next)
      }
    }
  }
}

@Composable
private fun Welcome() {
  val colors = CassetteTheme.colors
  Text("Know before you ride.", style = CassetteTheme.type.display, color = colors.text)
  Text(
    "Cassette keeps an eye on your SRAM AXS batteries: it reads them when the bike wakes and shows " +
      "them in the app, on your home screen and in Quick Settings.",
    style = CassetteTheme.type.body,
    color = colors.muted,
  )
  Panel {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(LucideIcons.Info, null, tint = colors.muted, modifier = Modifier.size(20.dp))
      Text(
        "Unofficial. Not affiliated with SRAM. It only reads what your components share; it never " +
          "changes their settings.",
        style =
          CassetteTheme.type.bodySmall.copy(fontSize = CassetteTheme.type.body.fontSize * 0.87f),
        color = colors.text,
      )
    }
  }
}

@Composable
private fun Explainer(
  title: String,
  body: String,
  icon: ImageVector,
  permissionName: String,
  permissionUse: String,
) {
  val colors = CassetteTheme.colors
  Text(title, style = CassetteTheme.type.display, color = colors.text)
  Text(body, style = CassetteTheme.type.body, color = colors.muted)
  Panel {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.track),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, null, tint = colors.text, modifier = Modifier.size(20.dp))
      }
      Column(Modifier.weight(1f)) {
        Text(permissionName, style = CassetteTheme.type.itemTitle, color = colors.text)
        Text(permissionUse, style = CassetteTheme.type.bodySmall, color = colors.muted)
      }
    }
  }
}

/**
 * Allow (or open settings once Android stops asking), plus "Not now". Granted moves straight on.
 */
@Composable
private fun PermissionButtons(permission: AppPermission, next: () -> Unit) {
  val controller = LocalPermissionController.current
  var state by remember(permission) { mutableStateOf(controller.state(permission)) }
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    when (state) {
      PermissionState.Granted ->
        CassetteButton("Continue", onClick = next, icon = LucideIcons.CircleCheck)
      PermissionState.Askable ->
        CassetteButton(
          "Allow",
          onClick = {
            controller.request(permission) { result ->
              state = result
              if (result == PermissionState.Granted) next()
            }
          },
        )
      PermissionState.Blocked ->
        CassetteButton("Open settings", onClick = { controller.openSettings() })
    }
    if (state != PermissionState.Granted) CassetteButton("Not now", onClick = next, primary = false)
  }
}
