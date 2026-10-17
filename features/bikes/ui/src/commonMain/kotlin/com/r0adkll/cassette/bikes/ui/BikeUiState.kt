package com.r0adkll.cassette.bikes.ui

import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState

sealed interface BikeUiState : CircuitUiState {
  val eventSink: (BikeEvent) -> Unit

  data class Loading(override val eventSink: (BikeEvent) -> Unit) : BikeUiState

  /** No bikes yet. */
  data class Empty(override val eventSink: (BikeEvent) -> Unit) : BikeUiState

  data class Loaded(
    val name: String,
    /** "1 OF 2" */
    val position: String,
    val status: BikeStatus,
    val components: List<ComponentRowModel>,
    val refresh: RefreshButton = RefreshButton.Idle,
    override val eventSink: (BikeEvent) -> Unit,
  ) : BikeUiState
}

sealed interface RefreshButton {
  data object Idle : RefreshButton

  data class Scanning(val secondsLeft: Int) : RefreshButton

  /** Bluetooth is off or the Nearby devices permission is missing. */
  data object Unavailable : RefreshButton
}

/** What the status block says (decision 0012). */
data class BikeStatus(
  val tone: StatusTone,
  val headline: String,
  val summary: String,
  val meta: String,
)

data class ComponentRowModel(
  val id: Long,
  val code: String,
  val name: String,
  val source: String,
  val value: String,
  val level: Float,
  val tone: StatusTone,
)

sealed interface BikeEvent : CircuitUiEvent {
  data class OpenComponent(val id: Long) : BikeEvent

  data object OpenSettings : BikeEvent

  data object Refresh : BikeEvent

  data object AddBike : BikeEvent

  /** Developer action until capture is ported; see DeveloperTools. */
  data object LoadSampleBike : BikeEvent
}
