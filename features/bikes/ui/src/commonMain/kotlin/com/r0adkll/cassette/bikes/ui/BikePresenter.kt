package com.r0adkll.cassette.bikes.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.api.HomeSurfaces
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.common.screens.AddBikeScreen
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.common.screens.ComponentScreen
import com.r0adkll.cassette.common.screens.SettingsScreen
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.r0adkll.cassette.settings.api.Tip
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.foundation.onNavEvent
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@CircuitInject(BikeScreen::class, AppScope::class)
@Inject
class BikePresenter(
  private val screen: BikeScreen,
  private val navigator: Navigator,
  private val repository: BikeRepository,
  private val capture: CaptureController,
  private val clock: Clock,
  private val settings: CassetteSettings,
  private val surfaces: HomeSurfaces,
) : Presenter<BikeUiState> {

  @Composable
  override fun present(): BikeUiState {
    val scope = rememberCoroutineScope()
    // The rail and the chips switch bikes in place; the screen's bike is only where it starts.
    var selected by rememberRetained { mutableStateOf(screen.bikeId) }
    // The component open in the dashboard's sheet (decision 0014).
    var sheet by rememberRetained { mutableStateOf<Long?>(null) }
    val overview by
      remember(selected) { repository.observeOverview(selected?.let(::BikeId)).map { Result(it) } }
        .collectAsRetainedState(initial = null)

    // Every bike with its status, for the rail and the chips on large screens (decision 0014).
    val bikes by remember {
      repository.observeBikes().flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyList())
        else combine(list.map { repository.observeOverview(it.id) }) { it.filterNotNull() }
      }
    }
      .collectAsRetainedState(initial = emptyList())

    // The tiles' 30-day trends, by component id.
    val componentIds = overview?.overview?.components?.map { it.component.id }.orEmpty()
    val trends by
      remember(componentIds) {
          if (componentIds.isEmpty()) flowOf(emptyMap())
          else
            combine(
              componentIds.map { id ->
                repository.observeComponent(id, clock.now() - TrendDays.days).map { detail ->
                  id.value to detail?.trend().orEmpty()
                }
              }
            ) {
              it.toMap()
            }
        }
        .collectAsRetainedState(initial = emptyMap())

    // "Seen 2 min ago" keeps itself current.
    var now by remember { mutableStateOf(clock.now()) }
    LaunchedEffect(Unit) {
      while (true) {
        delay(30.seconds)
        now = clock.now()
      }
    }

    var refresh by remember { mutableStateOf<RefreshButton>(RefreshButton.Idle) }

    val dismissed by settings.dismissedTips.collectAsRetainedState(initial = null)
    val tip =
      remember(dismissed) {
        val done = dismissed ?: return@remember null
        when {
          Tip.Widget !in done && surfaces.canOfferWidget() -> Tip.Widget
          Tip.QuickSettingsTile !in done && surfaces.canOfferTile() -> Tip.QuickSettingsTile
          else -> null
        }
      }

    val eventSink: (BikeEvent) -> Unit = { event ->
      when (event) {
        is BikeEvent.OpenComponent -> navigator.goTo(ComponentScreen(event.id))
        is BikeEvent.SelectBike -> {
          selected = event.id
          sheet = null
        }
        is BikeEvent.ShowComponent -> sheet = event.id
        BikeEvent.CloseSheet -> sheet = null
        is BikeEvent.SheetNavigation -> navigator.onNavEvent(event.event)
        BikeEvent.OpenSettings -> navigator.goTo(SettingsScreen)
        BikeEvent.AddBike -> navigator.goTo(AddBikeScreen)
        is BikeEvent.AcceptTip -> {
          when (event.tip) {
            Tip.Widget -> surfaces.requestWidget()
            Tip.QuickSettingsTile -> surfaces.requestTile()
          }
          scope.launch { settings.dismissTip(event.tip) }
        }
        is BikeEvent.DismissTip -> scope.launch { settings.dismissTip(event.tip) }
        BikeEvent.Refresh ->
          if (refresh !is RefreshButton.Scanning) {
            scope.launch {
              capture.refresh().collect { progress ->
                refresh =
                  when (progress) {
                    is RefreshProgress.Scanning -> RefreshButton.Scanning(progress.secondsLeft)
                    is RefreshProgress.Done -> RefreshButton.Idle
                    RefreshProgress.NotReady -> RefreshButton.Unavailable
                  }
              }
            }
          }
      }
    }

    val loaded = overview ?: return BikeUiState.Loading(eventSink)
    val bike = loaded.overview ?: return BikeUiState.Empty(eventSink)
    val loadedBike = bike.toLoaded(now, eventSink)
    return loadedBike.copy(
      refresh = refresh,
      tip = tip,
      bikes = bikes.toChoices(now),
      trends = trends,
      // A component that has gone (removed with its bike) closes the sheet.
      sheet = sheet?.takeIf { id -> loadedBike.components.any { it.id == id } },
    )
  }

  /** Wraps the nullable overview so "loading" and "no bike" are different states. */
  private data class Result(val overview: BikeOverview?)

  private companion object {
    const val TrendDays = 30
  }
}
