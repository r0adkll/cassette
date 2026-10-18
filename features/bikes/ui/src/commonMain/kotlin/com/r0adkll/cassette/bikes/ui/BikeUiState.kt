package com.r0adkll.cassette.bikes.ui

import com.r0adkll.cassette.bikes.summary.BikeStatus
import com.r0adkll.cassette.bikes.summary.ComponentRowModel
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.settings.api.Tip
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
    val refresh: RefreshButton = RefreshButton.Idle,
    /** At most one tip at a time, under the components. */
    val tip: Tip? = null,
    /** Every bike, for the tablet rail and the foldable's chips (decision 0014). */
    val bikes: List<BikeChoice> = emptyList(),
    /** Each component's last 30 days (0 to 1, oldest first), by component id, for the tiles. */
    val trends: Map<Long, List<Float>> = emptyMap(),
    override val eventSink: (BikeEvent) -> Unit,
  ) : BikeUiState
}

/** A bike to switch to: its name, overall status and when it was seen ("2 min ago"). */
data class BikeChoice(val id: Long, val name: String, val tone: StatusTone, val seen: String)

sealed interface RefreshButton {
  data object Idle : RefreshButton

  data class Scanning(val secondsLeft: Int) : RefreshButton

  /** Bluetooth is off or the Nearby devices permission is missing. */
  data object Unavailable : RefreshButton
}

sealed interface BikeEvent : CircuitUiEvent {
  data class OpenComponent(val id: Long) : BikeEvent

  /** Shows another bike in place, from the rail or a chip. */
  data class SelectBike(val id: Long) : BikeEvent

  data object OpenSettings : BikeEvent

  data object Refresh : BikeEvent

  data object AddBike : BikeEvent

  /** The tip's button: ask the system to add it. Either way the tip is done. */
  data class AcceptTip(val tip: Tip) : BikeEvent

  data class DismissTip(val tip: Tip) : BikeEvent
}
