package com.r0adkll.cassette.wear

import com.r0adkll.cassette.bikes.summary.toSummary
import com.r0adkll.cassette.surfaces.wear.sync.WearSnapshot
import com.r0adkll.cassette.surfaces.wear.sync.toOverviews
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class WatchWordsTest {
  private fun summary(bike: com.r0adkll.cassette.surfaces.wear.sync.WearBike) =
    WearSnapshot(defaultBikeId = bike.id, bikes = listOf(bike))
      .toOverviews()
      .single()
      .toSummary(NOW + 2.minutes)

  @Test
  fun lowNamesTheComponent() {
    val low = summary(road)
    assertEquals("FD LOW", low.statusLabel)
    assertEquals("FD ≈12% · 2 min ago", low.detailLine)
  }

  @Test
  fun criticalAndGood() {
    val critical =
      bike(1, "Road bike", component(10, "REAR_DERAILLEUR", reading(7_000, 4, "CRITICAL")))
    assertEquals("RD CHARGE NOW", summary(critical).statusLabel)
    val good = summary(gravel)
    assertEquals("ALL GOOD", good.statusLabel)
    assertEquals("Weakest RD 88% · 2 min ago", good.detailLine)
  }

  @Test
  fun staleAndUnread() {
    val stale = summary(road.copy(lastSeenAt = (NOW - 20.days).toEpochMilliseconds()))
    assertEquals("NOT SEEN", stale.statusLabel)
    assertEquals("Seen 20 days ago", stale.detailLine)
    val unread = summary(bike(3, "New bike", component(30, "REAR_DERAILLEUR", null)))
    assertEquals("NO READINGS", unread.statusLabel)
  }
}
