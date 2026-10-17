package com.r0adkll.cassette.wear.ui.bikes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.r0adkll.cassette.bikes.summary.toSummary
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.surfaces.wear.sync.toOverviews
import com.r0adkll.cassette.wear.data.PhoneData
import com.r0adkll.cassette.wear.statusLabel
import com.r0adkll.cassette.wear.ui.BikeScreen
import com.r0adkll.cassette.wear.ui.BikesScreen
import com.r0adkll.cassette.wear.ui.UpdateScreen
import com.r0adkll.cassette.wear.ui.rememberNow
import com.r0adkll.cassette.wear.updates.WatchUpdate
import com.r0adkll.cassette.wear.updates.WatchUpdates
import com.r0adkll.cassette.wear.updates.megabytes
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

data class BikesUiState(
  val list: BikeList,
  /** The watch app's update from Drop, under the bikes (iteration 9). */
  val update: UpdateRow?,
  /** "Updated to build N.", on the first run after an update. */
  val updatedTo: Long?,
  val eventSink: (BikesEvent) -> Unit,
) : CircuitUiState

sealed interface BikeList {
  data object Loading : BikeList

  /** Nothing from the phone yet ([synced] false), or the phone has no bikes. */
  data class Empty(val synced: Boolean) : BikeList

  data class Bikes(val bikes: List<BikeItem>) : BikeList
}

data class BikeItem(val id: Long, val name: String, val tone: StatusTone, val label: String)

/** "Update ready" and what it's doing: "BUILD 37 · 4.1 MB", "DOWNLOADING 62%". */
data class UpdateRow(val detail: String, val failed: Boolean = false)

sealed interface BikesEvent : CircuitUiEvent {
  data class Open(val id: Long) : BikesEvent

  data object OpenUpdate : BikesEvent
}

@CircuitInject(BikesScreen::class, AppScope::class)
@Inject
class BikesPresenter(
  private val navigator: Navigator,
  private val phone: PhoneData,
  private val updates: WatchUpdates,
  private val clock: Clock,
) : Presenter<BikesUiState> {
  @Composable
  override fun present(): BikesUiState {
    val snapshot by remember {
      phone.snapshot.map { Synced(it) }
    }
      .collectAsRetainedState(initial = null)
    val now = rememberNow(clock, every = 30.seconds)
    val update by updates.state.collectAsState()
    LaunchedEffect(Unit) { updates.check() }

    val synced = snapshot
    val bikes = synced?.snapshot?.toOverviews().orEmpty()
    val list =
      when {
        synced == null -> BikeList.Loading
        bikes.isEmpty() -> BikeList.Empty(synced = synced.snapshot != null)
        else ->
          BikeList.Bikes(
            bikes.map {
              val summary = it.toSummary(now)
              BikeItem(summary.bikeId, summary.name, summary.status.tone, summary.statusLabel)
            }
          )
      }
    return BikesUiState(
      list,
      update.row(),
      updates.installedBuild.takeIf { updates.justUpdated },
    ) { event ->
      when (event) {
        is BikesEvent.Open -> navigator.goTo(BikeScreen(event.id))
        BikesEvent.OpenUpdate -> navigator.goTo(UpdateScreen)
      }
    }
  }

  private fun WatchUpdate.row(): UpdateRow? =
    when (this) {
      WatchUpdate.None -> null
      is WatchUpdate.Available -> UpdateRow("BUILD $build · ${megabytes(bytes)}")
      is WatchUpdate.Downloading -> UpdateRow("DOWNLOADING ${(progress * 100).toInt()}%")
      is WatchUpdate.Installing -> UpdateRow("INSTALLING BUILD $build")
      is WatchUpdate.Failed -> UpdateRow("UPDATE FAILED", failed = true)
    }

  /** Tells "still loading" apart from "the phone hasn't sent anything". */
  private data class Synced(val snapshot: com.r0adkll.cassette.surfaces.wear.sync.WearSnapshot?)
}
