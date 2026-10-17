package com.r0adkll.cassette.wear.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.r0adkll.cassette.wear.ui.AllowUpdatesScreen
import com.r0adkll.cassette.wear.ui.UpdateScreen
import com.r0adkll.cassette.wear.updates.WatchUpdate
import com.r0adkll.cassette.wear.updates.WatchUpdates
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

data class UpdateUiState(
  val update: WatchUpdate,
  val installedBuild: Long,
  val eventSink: (UpdateEvent) -> Unit,
) : CircuitUiState

sealed interface UpdateEvent : CircuitUiEvent {
  /** Install, or try again after a failure. */
  data object Install : UpdateEvent
}

@CircuitInject(UpdateScreen::class, AppScope::class)
@Inject
class UpdatePresenter(private val navigator: Navigator, private val updates: WatchUpdates) :
  Presenter<UpdateUiState> {
  @Composable
  override fun present(): UpdateUiState {
    val update by updates.state.collectAsState()
    return UpdateUiState(update, updates.installedBuild) { event ->
      when (event) {
        UpdateEvent.Install ->
          if (updates.canInstall()) updates.install() else navigator.goTo(AllowUpdatesScreen)
      }
    }
  }
}

/** The three moments of the allow step (iteration 9). */
enum class AllowStep {
  /** "Allow updates." with Open settings. */
  Explain,
  /** Where the switch is, just before App info opens. */
  Path,
  /** Back in the app, allowed. */
  Allowed,
}

data class AllowUpdatesUiState(val step: AllowStep, val eventSink: (AllowUpdatesEvent) -> Unit) :
  CircuitUiState

sealed interface AllowUpdatesEvent : CircuitUiEvent {
  data object OpenSettings : AllowUpdatesEvent

  /** The app is in front again, perhaps back from Settings. */
  data object Resumed : AllowUpdatesEvent
}

@CircuitInject(AllowUpdatesScreen::class, AppScope::class)
@Inject
class AllowUpdatesPresenter(private val navigator: Navigator, private val updates: WatchUpdates) :
  Presenter<AllowUpdatesUiState> {
  @Composable
  override fun present(): AllowUpdatesUiState {
    var step by remember {
      mutableStateOf(if (updates.canInstall()) AllowStep.Allowed else AllowStep.Explain)
    }
    LaunchedEffect(step) {
      when (step) {
        // Long enough to read the two steps, then the system's page.
        AllowStep.Path -> {
          delay(PATH_FOR)
          updates.openAllowSettings()
        }
        // "You won't be asked again", then on with the update the Tester asked for.
        AllowStep.Allowed -> {
          delay(ALLOWED_FOR)
          updates.install()
          navigator.pop()
        }
        AllowStep.Explain -> Unit
      }
    }
    return AllowUpdatesUiState(step) { event ->
      when (event) {
        AllowUpdatesEvent.OpenSettings -> step = AllowStep.Path
        AllowUpdatesEvent.Resumed ->
          if (updates.canInstall()) step = AllowStep.Allowed
          else if (step == AllowStep.Path) step = AllowStep.Explain
      }
    }
  }

  companion object {
    val PATH_FOR = 2_500.milliseconds
    val ALLOWED_FOR = 1_500.milliseconds
  }
}
