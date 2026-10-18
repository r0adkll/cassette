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
import com.r0adkll.cassette.common.screens.SettingsCategory
import com.r0adkll.cassette.common.screens.SettingsScreen
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.r0adkll.cassette.settings.api.NotificationSettings
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
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
  /** The screen's category: on a phone, null is the list and a category is its own screen. */
  val category: SettingsCategory? = null,
  /** The category open beside the list on wide windows. */
  val selected: SettingsCategory = category ?: SettingsCategory.Bikes,
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

  /** On a phone: the category's screen (Diagnostics and Licences open their own). */
  data class OpenCategory(val category: SettingsCategory) : SettingsEvent

  /** On a wide window: the category beside the list. */
  data class SelectCategory(val category: SettingsCategory) : SettingsEvent
}

@CircuitInject(SettingsScreen::class, AppScope::class)
@Inject
class SettingsPresenter(
  private val screen: SettingsScreen,
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
    var selected by rememberRetained {
      mutableStateOf(screen.category ?: SettingsCategory.Bikes)
    }

    return SettingsUiState(
      bikeList,
      pinned,
      choosing,
      background,
      notifications,
      category = screen.category,
      selected = selected,
    ) { event ->
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
        is SettingsEvent.OpenCategory ->
          navigator.goTo(
            when (event.category) {
              SettingsCategory.Diagnostics -> DiagnosticsScreen()
              SettingsCategory.Licences -> LicencesScreen
              else -> SettingsScreen(event.category)
            }
          )
        is SettingsEvent.SelectCategory -> {
          choosing = false
          selected = event.category
        }
      }
    }
  }
}
