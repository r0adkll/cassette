package com.r0adkll.cassette.wear.ui.bike

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.r0adkll.cassette.bikes.summary.BikeStatus
import com.r0adkll.cassette.bikes.summary.ComponentRowModel
import com.r0adkll.cassette.bikes.summary.toSummary
import com.r0adkll.cassette.surfaces.wear.sync.WearSnapshot
import com.r0adkll.cassette.surfaces.wear.sync.toOverviews
import com.r0adkll.cassette.wear.WatchRefresh
import com.r0adkll.cassette.wear.data.PhoneData
import com.r0adkll.cassette.wear.ui.BikeScreen
import com.r0adkll.cassette.wear.ui.rememberNow
import com.r0adkll.cassette.wear.watchRefresh
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface BikeUiState : CircuitUiState {
  data object Loading : BikeUiState

  /** Removed on the phone. */
  data object Gone : BikeUiState

  data class Loaded(
    val name: String,
    val status: BikeStatus,
    val components: List<ComponentRowModel>,
    val refresh: WatchRefresh,
    val eventSink: (BikeEvent) -> Unit,
  ) : BikeUiState
}

sealed interface BikeEvent : CircuitUiEvent {
  /** Asks the phone to run its 60-second refresh. */
  data object Refresh : BikeEvent
}

@CircuitInject(BikeScreen::class, AppScope::class)
@Inject
class BikePresenter(
  private val screen: BikeScreen,
  private val phone: PhoneData,
  private val clock: Clock,
) : Presenter<BikeUiState> {
  @Composable
  override fun present(): BikeUiState {
    val scope = rememberCoroutineScope()
    val snapshot by remember {
      phone.snapshot.map { Synced(it) }
    }
      .collectAsRetainedState(initial = null)
    val phoneRefresh by remember { phone.refresh }.collectAsRetainedState(initial = null)
    var askedAt by remember { mutableStateOf<Instant?>(null) }
    var noPhoneAt by remember { mutableStateOf<Instant?>(null) }

    // Every second while Refresh is counting down or showing a message; "seen" only needs minutes.
    val busy = watchRefresh(phoneRefresh, askedAt, noPhoneAt, clock.now()) != WatchRefresh.Idle
    val now = rememberNow(clock, every = if (busy) 1.seconds else 30.seconds)
    val refresh = watchRefresh(phoneRefresh, askedAt, noPhoneAt, now)

    val synced = snapshot ?: return BikeUiState.Loading
    val overview =
      synced.snapshot?.toOverviews()?.firstOrNull { it.bike.id.value == screen.bikeId }
        ?: return BikeUiState.Gone
    val summary = overview.toSummary(now)
    return BikeUiState.Loaded(summary.name, summary.status, summary.components, refresh) { event ->
      when (event) {
        BikeEvent.Refresh ->
          if (refresh == WatchRefresh.Idle) {
            askedAt = clock.now()
            noPhoneAt = null
            scope.launch {
              if (!phone.requestRefresh()) {
                askedAt = null
                noPhoneAt = clock.now()
              }
            }
          }
      }
    }
  }

  private data class Synced(val snapshot: WearSnapshot?)
}
