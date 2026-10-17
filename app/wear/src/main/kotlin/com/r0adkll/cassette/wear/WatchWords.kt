package com.r0adkll.cassette.wear

import com.r0adkll.cassette.bikes.summary.BikeSummary
import com.r0adkll.cassette.bikes.summary.ComponentRowModel
import com.r0adkll.cassette.bikes.summary.weakest
import com.r0adkll.cassette.common.compose.theme.StatusTone

/**
 * The bike's status in a few words, for the bike list and the short-text complication: "ALL GOOD",
 * "FD LOW", "RD CHARGE NOW", "NOT SEEN", "NO READINGS".
 */
val BikeSummary.statusLabel: String
  get() =
    when (status.tone) {
      StatusTone.Good -> "ALL GOOD"
      StatusTone.Low -> "${worst?.code ?: ""} LOW".trim()
      StatusTone.Critical -> "${worst?.code ?: ""} CHARGE NOW".trim()
      StatusTone.Stale -> "NOT SEEN"
      StatusTone.Unknown -> if (components.weakest() == null) "NO READINGS" else "UNKNOWN"
    }

/** The component that sets the bike's status: the first with the bike's tone. */
val BikeSummary.worst: ComponentRowModel?
  get() = components.firstOrNull { it.tone == status.tone } ?: components.weakest()

/**
 * The line under the headline on the tile: "Weakest RD 78% · 2 min ago", "FD 18% · 2 min ago",
 * "Seen 20 days ago".
 */
val BikeSummary.detailLine: String
  get() {
    val seen = status.meta.removePrefix("Seen ")
    return when (status.tone) {
      StatusTone.Good ->
        components.weakest()?.let { "Weakest ${it.code} ${it.value} · $seen" } ?: seen
      StatusTone.Low,
      StatusTone.Critical -> worst?.let { "${it.code} ${it.value} · $seen" } ?: seen
      StatusTone.Stale -> status.meta
      StatusTone.Unknown -> status.summary
    }
  }
