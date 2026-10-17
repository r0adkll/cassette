package com.r0adkll.cassette.bikes.ui

import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.summary.toSummary
import kotlin.time.Instant

/** The bike screen's state: the shared summary, plus the screen's own position and events. */
fun BikeOverview.toLoaded(now: Instant, eventSink: (BikeEvent) -> Unit = {}): BikeUiState.Loaded {
  val summary = toSummary(now)
  return BikeUiState.Loaded(
    bikeId = summary.bikeId,
    name = summary.name,
    position = if (bikeCount > 1) "1 OF $bikeCount" else null,
    status = summary.status,
    components = summary.components,
    eventSink = eventSink,
  )
}
