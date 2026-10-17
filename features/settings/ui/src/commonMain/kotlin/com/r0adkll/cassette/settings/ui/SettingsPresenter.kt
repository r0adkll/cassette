package com.r0adkll.cassette.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.common.screens.DiagnosticsScreen
import com.r0adkll.cassette.common.screens.LicencesScreen
import com.r0adkll.cassette.common.screens.SettingsScreen
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.r0adkll.cassette.settings.api.NotificationSettings
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch

data class SettingsUiState(
  val bikes: List<Bike>,
  val pinned: BikeId?,
  val choosingBike: Boolean,
  val backgroundCapture: Boolean,
  val notifications: NotificationSettings,
  val eventSink: (SettingsEvent) -> Unit,
) : CircuitUiState {
  /** "Most recently seen (Road bike)" or the pinned bike's name. */
  val defaultBikeLabel: String
    get() =
      bikes.firstOrNull { it.id == pinned }?.name
        ?: bikes.firstOrNull()?.let { "Most recently seen (${it.name})" }
        ?: "Most recently seen"
}

sealed interface SettingsEvent : CircuitUiEvent {
  data object Back : SettingsEvent

  data object ChooseBike : SettingsEvent

  data class PinBike(val id: BikeId?) : SettingsEvent

  data class SetBackgroundCapture(val enabled: Boolean) : SettingsEvent

  data class SetNotifications(val settings: NotificationSettings) : SettingsEvent

  data object OpenDiagnostics : SettingsEvent

  data object OpenLicences : SettingsEvent
}

@CircuitInject(SettingsScreen::class, AppScope::class)
@Inject
class SettingsPresenter(
  private val navigator: Navigator,
  private val settings: CassetteSettings,
  private val bikes: BikeRepository,
  private val capture: CaptureController,
) : Presenter<SettingsUiState> {

  @Composable
  override fun present(): SettingsUiState {
    val scope = rememberCoroutineScope()
    val bikeList by remember { bikes.observeBikes() }.collectAsRetainedState(emptyList())
    val pinned by remember { settings.pinnedBike }.collectAsRetainedState(null)
    val background by remember { settings.backgroundCapture }.collectAsRetainedState(true)
    val notifications by remember {
      settings.notifications
    }
      .collectAsRetainedState(NotificationSettings())
    var choosing by remember { mutableStateOf(false) }

    return SettingsUiState(bikeList, pinned, choosing, background, notifications) { event ->
      when (event) {
        SettingsEvent.Back -> navigator.pop()
        SettingsEvent.ChooseBike -> choosing = !choosing
        is SettingsEvent.PinBike -> {
          choosing = false
          scope.launch { settings.setPinnedBike(event.id) }
        }
        is SettingsEvent.SetBackgroundCapture ->
          scope.launch {
            settings.setBackgroundCapture(event.enabled)
            capture.ensureBackgroundCapture("settings changed")
          }
        is SettingsEvent.SetNotifications ->
          scope.launch { settings.setNotifications(event.settings) }
        SettingsEvent.OpenDiagnostics -> navigator.goTo(DiagnosticsScreen)
        SettingsEvent.OpenLicences -> navigator.goTo(LicencesScreen)
      }
    }
  }
}
