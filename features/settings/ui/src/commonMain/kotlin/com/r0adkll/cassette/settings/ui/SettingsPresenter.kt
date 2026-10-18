package com.r0adkll.cassette.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.summary.toSummary
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.common.screens.AddBikeScreen
import com.r0adkll.cassette.common.screens.BikeSettingsScreen
import com.r0adkll.cassette.common.screens.DiagnosticsScreen
import com.r0adkll.cassette.common.screens.LicencesScreen
import com.r0adkll.cassette.common.screens.SettingsCategory
import com.r0adkll.cassette.common.screens.SettingsScreen
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.r0adkll.cassette.settings.api.NotificationSettings
import com.r0adkll.cassette.settings.api.ThemeMode
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

data class SettingsUiState(
  /** Every bike, most recently seen first. */
  val garage: List<GarageBike>,
  /** The bike that always opens first; null opens the last bike seen. */
  val pinned: BikeId?,
  val backgroundCapture: Boolean,
  val notifications: NotificationSettings,
  val themeMode: ThemeMode = ThemeMode.System,
  /** The screen's category: on a phone, null is the list and a category is its own screen. */
  val category: SettingsCategory? = null,
  /** The category open beside the list on wide windows. */
  val selected: SettingsCategory = category ?: SettingsCategory.Bikes,
  val eventSink: (SettingsEvent) -> Unit,
) : CircuitUiState {
  /** "Gravel bike opens first", or the last bike seen does. */
  val opensFirstLabel: String
    get() =
      garage.firstOrNull { it.opensFirst }?.let { "${it.name} opens first" }
        ?: "The last bike seen opens first"
}

/** A bike's card in the garage (iteration 13, B2). */
data class GarageBike(
  val id: Long,
  val name: String,
  /** "Road", "Gravel", "MTB" or "Other". */
  val type: String,
  /** "seen 2 min ago", "never seen". */
  val seen: String,
  val tone: StatusTone,
  /** Each component's code with its status. */
  val components: List<Pair<String, StatusTone>>,
  val opensFirst: Boolean,
)

sealed interface SettingsEvent : CircuitUiEvent {
  data object Back : SettingsEvent

  /** On: the last bike seen opens first. Off: the most recent one is pinned, until changed. */
  data class SetFollowLastSeen(val follow: Boolean) : SettingsEvent

  data class OpenBike(val id: Long) : SettingsEvent

  data object AddBike : SettingsEvent

  data class SetBackgroundCapture(val enabled: Boolean) : SettingsEvent

  data class SetNotifications(val settings: NotificationSettings) : SettingsEvent

  data class SetThemeMode(val mode: ThemeMode) : SettingsEvent

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
  private val clock: Clock,
) : Presenter<SettingsUiState> {

  @Composable
  override fun present(): SettingsUiState {
    val scope = rememberCoroutineScope()
    val overviews by remember { bikes.observeOverviews() }.collectAsRetainedState(emptyList())
    val pinned by remember { settings.pinnedBike }.collectAsRetainedState(null)
    val background by remember { settings.backgroundCapture }.collectAsRetainedState(true)
    val notifications by remember {
      settings.notifications
    }
      .collectAsRetainedState(NotificationSettings())
    val themeMode by remember { settings.themeMode }.collectAsRetainedState(ThemeMode.System)
    var selected by rememberRetained {
      mutableStateOf(screen.category ?: SettingsCategory.Bikes)
    }
    val garage = overviews.toGarage(pinned, clock.now())

    return SettingsUiState(
      garage,
      pinned,
      background,
      notifications,
      themeMode = themeMode,
      category = screen.category,
      selected = selected,
    ) { event ->
      when (event) {
        SettingsEvent.Back -> navigator.pop()
        is SettingsEvent.SetFollowLastSeen ->
          scope.launch {
            settings.setPinnedBike(
              if (event.follow) null else garage.firstOrNull()?.id?.let(::BikeId)
            )
          }
        is SettingsEvent.OpenBike -> navigator.goTo(BikeSettingsScreen(event.id))
        SettingsEvent.AddBike -> navigator.goTo(AddBikeScreen)
        is SettingsEvent.SetBackgroundCapture ->
          scope.launch {
            settings.setBackgroundCapture(event.enabled)
            capture.ensureBackgroundCapture("settings changed")
          }
        is SettingsEvent.SetNotifications ->
          scope.launch { settings.setNotifications(event.settings) }
        is SettingsEvent.SetThemeMode -> scope.launch { settings.setThemeMode(event.mode) }
        is SettingsEvent.OpenCategory ->
          navigator.goTo(
            when (event.category) {
              SettingsCategory.Diagnostics -> DiagnosticsScreen()
              SettingsCategory.Licences -> LicencesScreen
              else -> SettingsScreen(event.category)
            }
          )
        is SettingsEvent.SelectCategory -> selected = event.category
      }
    }
  }
}

/** The garage's cards, worded by the shared summary like the bike screen. */
internal fun List<BikeOverview>.toGarage(pinned: BikeId?, now: Instant): List<GarageBike> =
  map { overview ->
    val summary = overview.toSummary(now)
    GarageBike(
      id = summary.bikeId,
      name = summary.name,
      type = overview.bike.type.label,
      seen = summary.status.meta.replaceFirstChar { it.lowercase() },
      tone = summary.status.tone,
      components = summary.components.map { it.code to it.tone },
      opensFirst = overview.bike.id == pinned,
    )
  }

internal val BikeType.label: String
  get() =
    when (this) {
      BikeType.Road -> "Road"
      BikeType.Gravel -> "Gravel"
      BikeType.Mtb -> "MTB"
      BikeType.Other -> "Other"
    }
