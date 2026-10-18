package com.r0adkll.cassette.bikes.ui.component

import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState

data class ComponentUiState(
  val loaded: ComponentDetailModel?,
  /** Inside the dashboard's sheet: no back button, and the content fills the sheet. */
  val inSheet: Boolean = false,
  val eventSink: (ComponentEvent) -> Unit,
) : CircuitUiState

data class ComponentDetailModel(
  val bikeName: String,
  val name: String,
  val value: String,
  val headline: String,
  val source: String,
  val level: Float,
  val tone: StatusTone,
  /** Label to value, in grid order. */
  val stats: List<Pair<String, String>>,
  val range: HistoryRange,
  /** Levels 0–1, oldest first; fewer than two means there's no chart yet. */
  val chart: List<Float>,
  val chartCaption: String,
)

/** History ranges; samples are kept for a year (decision 0010). */
enum class HistoryRange(val days: Int, val label: String) {
  Month(30, "30D"),
  Quarter(90, "90D"),
  Year(365, "1Y"),
}

sealed interface ComponentEvent : CircuitUiEvent {
  data object Back : ComponentEvent

  data class SelectRange(val range: HistoryRange) : ComponentEvent
}
