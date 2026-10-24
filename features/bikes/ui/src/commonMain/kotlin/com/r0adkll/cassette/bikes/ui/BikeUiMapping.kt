package com.r0adkll.cassette.bikes.ui

import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.ComponentDetail
import com.r0adkll.cassette.bikes.summary.toSummary
import com.r0adkll.cassette.core.level
import kotlin.time.Instant

/** The bike screen's state: the shared summary, plus the screen's own events. */
fun BikeOverview.toLoaded(now: Instant, eventSink: (BikeEvent) -> Unit = {}): BikeUiState.Loaded {
  val summary = toSummary(now)
  return BikeUiState.Loaded(
    bikeId = summary.bikeId,
    name = summary.name,
    status = summary.status,
    components = summary.components,
    eventSink = eventSink,
  )
}

/** The bikes to switch between, worded like the bike screen: status tone and "2 min ago". */
fun List<BikeOverview>.toChoices(now: Instant): List<BikeChoice> = map { overview ->
  val summary = overview.toSummary(now)
  BikeChoice(
    id = summary.bikeId,
    name = summary.name,
    tone = summary.status.tone,
    seen = summary.status.meta.removePrefix("Seen "),
    summary = summary.status.summary,
  )
}

/** A tile's trend: the readings that carry a level, oldest first, as on the component chart. */
fun ComponentDetail.trend(): List<Float> =
  history.filter { it.millivolts != null || it.percent != null }.map { it.level }
