package com.r0adkll.cassette.wear.ui.bikes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.r0adkll.cassette.bikes.summary.toSummary
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.surfaces.wear.sync.toOverviews
import com.r0adkll.cassette.wear.data.PhoneData
import com.r0adkll.cassette.wear.statusLabel
import com.r0adkll.cassette.wear.ui.BikeScreen
import com.r0adkll.cassette.wear.ui.BikesScreen
import com.r0adkll.cassette.wear.ui.rememberNow
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.map

sealed interface BikesUiState : CircuitUiState {
  data object Loading : BikesUiState

  /** Nothing from the phone yet ([synced] false), or the phone has no bikes. */
  data class Empty(val synced: Boolean) : BikesUiState

  data class Loaded(val bikes: List<BikeItem>, val eventSink: (BikesEvent) -> Unit) : BikesUiState
}

data class BikeItem(val id: Long, val name: String, val tone: StatusTone, val label: String)

sealed interface BikesEvent : CircuitUiEvent {
  data class Open(val id: Long) : BikesEvent
}

@CircuitInject(BikesScreen::class, AppScope::class)
@Inject
class BikesPresenter(
  private val navigator: Navigator,
  private val phone: PhoneData,
  private val clock: Clock,
) : Presenter<BikesUiState> {
  @Composable
  override fun present(): BikesUiState {
    val snapshot by remember {
      phone.snapshot.map { Synced(it) }
    }
      .collectAsRetainedState(initial = null)
    val now = rememberNow(clock, every = 30.seconds)

    val synced = snapshot ?: return BikesUiState.Loading
    val bikes = synced.snapshot?.toOverviews().orEmpty()
    if (bikes.isEmpty()) return BikesUiState.Empty(synced = synced.snapshot != null)
    return BikesUiState.Loaded(
      bikes.map {
        val summary = it.toSummary(now)
        BikeItem(summary.bikeId, summary.name, summary.status.tone, summary.statusLabel)
      }
    ) { event ->
      when (event) {
        is BikesEvent.Open -> navigator.goTo(BikeScreen(event.id))
      }
    }
  }

  /** Tells "still loading" apart from "the phone hasn't sent anything". */
  private data class Synced(val snapshot: com.r0adkll.cassette.surfaces.wear.sync.WearSnapshot?)
}
