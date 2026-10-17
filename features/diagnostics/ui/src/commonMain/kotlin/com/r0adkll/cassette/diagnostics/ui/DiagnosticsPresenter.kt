package com.r0adkll.cassette.diagnostics.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.r0adkll.cassette.alerts.api.Alerts
import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.api.DeveloperTools
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.DiagnosticsSharer
import com.r0adkll.cassette.capture.api.WearAction
import com.r0adkll.cassette.common.screens.DiagnosticsScreen
import com.r0adkll.cassette.common.screens.HubTablesScreen
import com.r0adkll.cassette.common.screens.LiveAdvertsScreen
import com.r0adkll.cassette.common.screens.RideTestsScreen
import com.r0adkll.cassette.data.ble.ScanProfile
import com.r0adkll.cassette.settings.api.CaptureHealth
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.r0adkll.cassette.surfaces.wear.sync.ConnectedWatch
import com.r0adkll.cassette.surfaces.wear.sync.ConnectedWatches
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.produceRetainedState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
  val health: CaptureHealth,
  val bluetoothReady: Boolean,
  val profile: ScanProfile,
  val bike: BikeOverview?,
  val now: Instant,
  val confirmRemove: Boolean,
  val logEvents: Int,
  val sharing: Boolean,
  val wear: WearDiagnostics,
  val eventSink: (DiagnosticsEvent) -> Unit,
) : CircuitUiState

/** The Wear panel (decision 0013). */
data class WearDiagnostics(
  /** Null while checking. */
  val watches: WatchCheck?,
  val lastPublish: CaptureEvent.Wear?,
  val lastRequest: CaptureEvent.Wear?,
)

sealed interface WatchCheck {
  data class Found(val watches: List<ConnectedWatch>) : WatchCheck

  /** No Wear OS app (Data Layer) on this phone. */
  data object Unavailable : WatchCheck
}

sealed interface DiagnosticsEvent : CircuitUiEvent {
  data object Back : DiagnosticsEvent

  data object OpenLiveAdverts : DiagnosticsEvent

  data object OpenHubTables : DiagnosticsEvent

  data object OpenRideTests : DiagnosticsEvent

  data object Share : DiagnosticsEvent

  data class SetProfile(val profile: ScanProfile) : DiagnosticsEvent

  data object LoadSampleBike : DiagnosticsEvent

  /** Records a low reading and runs the alerts as if the bike just woke. */
  data object SimulateLowBattery : DiagnosticsEvent

  /** First tap asks, second removes. */
  data object RemoveBike : DiagnosticsEvent
}

@CircuitInject(DiagnosticsScreen::class, AppScope::class)
@Inject
class DiagnosticsPresenter(
  private val navigator: Navigator,
  private val settings: CassetteSettings,
  private val bikes: BikeRepository,
  private val capture: CaptureController,
  private val developerTools: DeveloperTools,
  private val log: CaptureLog,
  private val sharer: DiagnosticsSharer,
  private val alerts: Alerts,
  private val connectedWatches: ConnectedWatches,
  private val clock: Clock,
) : Presenter<DiagnosticsUiState> {

  @Composable
  override fun present(): DiagnosticsUiState {
    val scope = rememberCoroutineScope()
    val health by remember {
      settings.captureHealth
    }
      .collectAsRetainedState(CaptureHealth(null, null, null, 0))
    val profile by remember { settings.scanProfile }.collectAsRetainedState(ScanProfile.AllLowPower)
    val bike by remember { bikes.observeOverview(null) }.collectAsRetainedState(null)
    var confirmRemove by remember { mutableStateOf(false) }
    val events by remember { log.events }.collectAsRetainedState(emptyList())
    var sharing by remember { mutableStateOf(false) }
    val watches by
      produceRetainedState<WatchCheck?>(null) {
        value = connectedWatches.list()?.let(WatchCheck::Found) ?: WatchCheck.Unavailable
      }
    val wearEvents = events.filterIsInstance<CaptureEvent.Wear>()

    return DiagnosticsUiState(
      health,
      capture.isReady,
      profile,
      bike,
      clock.now(),
      confirmRemove,
      events.size,
      sharing,
      WearDiagnostics(
        watches,
        lastPublish = wearEvents.lastOrNull { it.action == WearAction.Published },
        lastRequest = wearEvents.lastOrNull { it.action == WearAction.RefreshRequested },
      ),
    ) { event ->
      when (event) {
        DiagnosticsEvent.Back -> navigator.pop()
        DiagnosticsEvent.OpenLiveAdverts -> navigator.goTo(LiveAdvertsScreen)
        DiagnosticsEvent.OpenRideTests -> navigator.goTo(RideTestsScreen)
        DiagnosticsEvent.Share ->
          if (!sharing) {
            sharing = true
            scope.launch {
              sharer.share()
              sharing = false
            }
          }
        DiagnosticsEvent.OpenHubTables ->
          bike?.let { navigator.goTo(HubTablesScreen(it.bike.id.value)) }
        is DiagnosticsEvent.SetProfile ->
          scope.launch {
            settings.setScanProfile(event.profile)
            capture.ensureBackgroundCapture("profile changed")
          }
        DiagnosticsEvent.SimulateLowBattery ->
          bike?.let { overview ->
            scope.launch {
              if (developerTools.simulateLowBattery(overview.bike.id)) {
                alerts.onCaptured(setOf(overview.bike.id), woke = setOf(overview.bike.id))
              }
            }
          }
        DiagnosticsEvent.LoadSampleBike ->
          scope.launch {
            developerTools.loadSampleBike()
            capture.ensureBackgroundCapture("sample bike loaded")
          }
        DiagnosticsEvent.RemoveBike ->
          if (!confirmRemove) confirmRemove = true
          else {
            confirmRemove = false
            bike?.let {
              scope.launch {
                bikes.deleteBike(it.bike.id).also {
                  capture.ensureBackgroundCapture("bike removed")
                }
              }
            }
          }
      }
    }
  }
}
