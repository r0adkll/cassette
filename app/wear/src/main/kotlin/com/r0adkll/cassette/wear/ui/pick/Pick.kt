package com.r0adkll.cassette.wear.ui.pick

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.r0adkll.cassette.bikes.summary.toSummary
import com.r0adkll.cassette.bikes.summary.weakest
import com.r0adkll.cassette.common.compose.components.word
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.surfaces.wear.sync.bike
import com.r0adkll.cassette.surfaces.wear.sync.defaultBike
import com.r0adkll.cassette.surfaces.wear.sync.toOverviews
import com.r0adkll.cassette.wear.complication.ComplicationPick
import com.r0adkll.cassette.wear.complication.ComplicationPicks
import com.r0adkll.cassette.wear.data.PhoneData
import com.r0adkll.cassette.wear.statusLabel
import com.r0adkll.cassette.wear.ui.ComplicationPicked
import com.r0adkll.cassette.wear.ui.PickBikeScreen
import com.r0adkll.cassette.wear.ui.PickComponentScreen
import com.r0adkll.cassette.wear.ui.rememberNow
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.rememberAnsweringNavigator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** A complication's settings, one question at a time (decision 0027). */
data class PickUiState(
  /** Over the list: "WHICH BIKE?", "ROAD BIKE · WHICH ONE?". */
  val title: String,
  val choices: List<PickChoice>,
  val eventSink: (PickEvent) -> Unit,
) : CircuitUiState

data class PickChoice(
  val name: String,
  /** Under the name: "NOW ROAD BIKE", "FD LOW", "78% · GOOD". */
  val label: String,
  val leading: Leading,
  /** The label's and icon's colour; null is muted. */
  val tone: StatusTone?,
  /** The complication's current pick. */
  val selected: Boolean,
  val event: PickEvent,
)

/** What leads a choice: the bike icon, the bike's status icon, "lowest first", or a code. */
sealed interface Leading {
  data object DefaultBike : Leading

  data object Status : Leading

  data object Weakest : Leading

  data class Code(val code: String) : Leading
}

sealed interface PickEvent : CircuitUiEvent {
  /** A bike, or the default bike (null). */
  data class Bike(val bikeId: Long?) : PickEvent

  /** A component by its kind's name, or the weakest (null). */
  data class Component(val kind: String?) : PickEvent
}

/** The default bike, then each bike with its status, as the bike list words it. */
@CircuitInject(PickBikeScreen::class, AppScope::class)
@Inject
class PickBikePresenter(
  private val screen: PickBikeScreen,
  private val navigator: Navigator,
  private val phone: PhoneData,
  private val picks: ComplicationPicks,
  private val clock: Clock,
) : Presenter<PickUiState> {
  @Composable
  override fun present(): PickUiState {
    val snapshot by phone.snapshot.collectAsRetainedState(initial = null)
    val now = rememberNow(clock, every = 30.seconds)
    val current = remember { picks.get(screen.complicationId) }
    // The component step pops with the saved pick; this step passes it on, which ends the activity.
    val next = rememberAnsweringNavigator<ComplicationPicked>(navigator) { navigator.pop(it) }
    val default = snapshot?.defaultBike()?.bike?.name
    val choices =
      listOf(
        PickChoice(
          "Default bike",
          default?.let { "NOW ${it.uppercase()}" } ?: "FOLLOWS YOUR PHONE",
          Leading.DefaultBike,
          tone = null,
          selected = current != null && current.bikeId == null,
          event = PickEvent.Bike(null),
        )
      ) +
        snapshot?.toOverviews().orEmpty().map { overview ->
          val bike = overview.toSummary(now)
          PickChoice(
            bike.name,
            bike.statusLabel,
            Leading.Status,
            bike.status.tone,
            selected = current?.bikeId == bike.bikeId,
            event = PickEvent.Bike(bike.bikeId),
          )
        }
    return PickUiState("WHICH BIKE?", choices) { event ->
      if (event is PickEvent.Bike)
        next.goTo(PickComponentScreen(screen.complicationId, event.bikeId))
    }
  }
}

/** The weakest (the default), then each of the bike's components with its value and status. */
@CircuitInject(PickComponentScreen::class, AppScope::class)
@Inject
class PickComponentPresenter(
  private val screen: PickComponentScreen,
  private val navigator: Navigator,
  private val phone: PhoneData,
  private val picks: ComplicationPicks,
  private val clock: Clock,
) : Presenter<PickUiState> {
  @Composable
  override fun present(): PickUiState {
    val snapshot by phone.snapshot.collectAsRetainedState(initial = null)
    val now = rememberNow(clock, every = 30.seconds)
    val current = remember {
      picks.get(screen.complicationId)?.takeIf { it.bikeId == screen.bikeId }
    }
    val bike = snapshot?.bike(screen.bikeId)?.toSummary(now)
    val components = bike?.components.orEmpty().distinctBy { it.kind }
    val weakest = components.weakest()
    val choices =
      listOf(
        PickChoice(
          "Weakest",
          weakest?.let { "NOW ${it.code} ${it.value}" } ?: "OR THE ONE TO CHARGE",
          Leading.Weakest,
          tone = null,
          selected = current != null && current.kind == null,
          event = PickEvent.Component(null),
        )
      ) +
        components.map {
          PickChoice(
            it.name,
            "${it.value} · ${it.tone.word}",
            Leading.Code(it.code),
            it.tone,
            selected = current?.kind == it.kind.name,
            event = PickEvent.Component(it.kind.name),
          )
        }
    val title =
      if (screen.bikeId == null) "DEFAULT BIKE · WHICH ONE?"
      else "${bike?.name?.uppercase() ?: "BIKE"} · WHICH ONE?"
    return PickUiState(title, choices) { event ->
      if (event is PickEvent.Component) {
        picks.set(screen.complicationId, ComplicationPick(screen.bikeId, event.kind))
        navigator.pop(ComplicationPicked)
      }
    }
  }
}
