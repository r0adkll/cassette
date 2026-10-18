package com.r0adkll.cassette.bikes.ui

import com.r0adkll.cassette.bikes.summary.BikeStatus
import com.r0adkll.cassette.bikes.summary.ComponentRowModel
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.settings.api.Tip
import com.slack.circuit.foundation.NavEvent
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState

sealed interface BikeUiState : CircuitUiState {
  val eventSink: (BikeEvent) -> Unit

  data class Loading(override val eventSink: (BikeEvent) -> Unit) : BikeUiState

  /** No bikes yet. */
  data class Empty(override val eventSink: (BikeEvent) -> Unit) : BikeUiState

  data class Loaded(
    val bikeId: Long,
    val name: String,
    /** "1 OF 2"; null when there's only one bike. */
    val position: String?,
    val status: BikeStatus,
    val components: List<ComponentRowModel>,
    /** The status block's title: the plain headline, or a playful one (decision 0015). */
    val title: String = status.headline,
    val refresh: RefreshButton = RefreshButton.Idle,
    /** At most one tip at a time, under the components. */
    val tip: Tip? = null,
    /** Every bike, for the tablet rail and the foldable's chips (decision 0014). */
    val bikes: List<BikeChoice> = emptyList(),
    /** Each component's last 30 days (0 to 1, oldest first), by component id, for the tiles. */
    val trends: Map<Long, List<Float>> = emptyMap(),
    /** The component open in the dashboard's sheet; null when it's closed. */
    val sheet: Long? = null,
    /**
     * The components that have answered the running (or just finished) refresh, by id; null when no
     * refresh is on screen. The tabletop greys out the rest as waiting.
     */
    val heard: Set<Long>? = null,
    /** When the bike was last read ("2 h ago"); null if it never has been. */
    val lastRead: String? = null,
    override val eventSink: (BikeEvent) -> Unit,
  ) : BikeUiState
}

/** A bike to switch to: its name, overall status and when it was seen ("2 min ago"). */
data class BikeChoice(val id: Long, val name: String, val tone: StatusTone, val seen: String)

sealed interface RefreshButton {
  data object Idle : RefreshButton

  /** [totalSeconds] grows by 60 with each Add 60 s; the tabletop's ring drains over it. */
  data class Scanning(val secondsLeft: Int, val totalSeconds: Int = 60) : RefreshButton

  /**
   * The refresh ended after [seconds]; the tabletop shows it until it's tapped away. Elsewhere it
   * reads as [Idle].
   */
  data class Done(val seconds: Int) : RefreshButton

  /** Bluetooth is off or the Nearby devices permission is missing. */
  data object Unavailable : RefreshButton
}

sealed interface BikeEvent : CircuitUiEvent {
  data class OpenComponent(val id: Long) : BikeEvent

  /** Shows another bike in place, from the rail or a chip. */
  data class SelectBike(val id: Long) : BikeEvent

  /** Opens the component in the dashboard's sheet, or switches the open sheet to it. */
  data class ShowComponent(val id: Long) : BikeEvent

  data object CloseSheet : BikeEvent

  /** Navigation from the screen inside the sheet, other than closing it. */
  data class SheetNavigation(val event: NavEvent) : BikeEvent

  data object OpenSettings : BikeEvent

  data object Refresh : BikeEvent

  /** The tabletop's Stop: ends the scan now. */
  data object StopRefresh : BikeEvent

  /** The tabletop's Add 60 s. */
  data object MoreTime : BikeEvent

  /** Tapping the tabletop's Done circle: back to ready. */
  data object FinishRefresh : BikeEvent

  data object AddBike : BikeEvent

  /** The tip's button: ask the system to add it. Either way the tip is done. */
  data class AcceptTip(val tip: Tip) : BikeEvent

  data class DismissTip(val tip: Tip) : BikeEvent
}
