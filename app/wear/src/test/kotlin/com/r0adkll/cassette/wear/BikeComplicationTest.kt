package com.r0adkll.cassette.wear

import com.r0adkll.cassette.bikes.summary.toSummary
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.surfaces.wear.sync.WearBike
import com.r0adkll.cassette.surfaces.wear.sync.WearSnapshot
import com.r0adkll.cassette.surfaces.wear.sync.bike
import com.r0adkll.cassette.surfaces.wear.sync.defaultBike
import com.r0adkll.cassette.wear.complication.BikeComplication
import com.r0adkll.cassette.wear.complication.complication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class BikeComplicationTest {
  private fun complication(vararg bikes: WearBike, default: Long? = bikes.first().id) =
    WearSnapshot(defaultBikeId = default, bikes = bikes.toList())
      .defaultBike()!!
      .toSummary(NOW)
      .complication()

  @Test
  fun goodShowsTheWeakestComponent() {
    assertEquals(
      BikeComplication(
        level = 0.88f,
        value = "88",
        code = "RD",
        status = "GOOD",
        statusTitle = null,
        tone = StatusTone.Good,
        description = "Gravel bike. All good. Rear derailleur 88%.",
      ),
      complication(gravel),
    )
  }

  @Test
  fun lowShowsTheComponentToCharge() {
    val low = complication(road)
    assertEquals("FD", low.code)
    assertEquals("≈11", low.value)
    assertEquals("FD LOW", low.status)
  }

  @Test
  fun criticalKeepsTheWordShortAndNamesTheComponentInTheTitle() {
    val critical =
      complication(
        bike(1, "Road bike", component(10, "LEFT_SHIFTER", reading(2_150, null, "CRITICAL")))
      )
    assertEquals("CHARGE", critical.status)
    assertEquals("L", critical.statusTitle)
    assertEquals("2.15", critical.value)
  }

  @Test
  fun theDefaultBikeOrTheFirst() {
    assertEquals("FD LOW", complication(gravel, road, default = 1).status)
    assertEquals("GOOD", complication(gravel, road, default = null).status)
  }

  @Test
  fun aPickedComponentShowsItselfWhateverTheBikeSays() {
    val picked =
      WearSnapshot(defaultBikeId = 1, bikes = listOf(road))
        .defaultBike()!!
        .toSummary(NOW)
        .complication(ComponentKind.REAR_DERAILLEUR)
    assertEquals(
      BikeComplication(
        level = 0.78f,
        value = "78",
        code = "RD",
        status = "78%",
        statusTitle = "RD",
        tone = StatusTone.Good,
        description = "Road bike. Rear derailleur 78%, good.",
      ),
      picked,
    )
  }

  @Test
  fun aComponentTheBikeLacksFallsBackToTheWeakest() {
    val gravelFd =
      WearSnapshot(defaultBikeId = 2, bikes = listOf(gravel))
        .defaultBike()!!
        .toSummary(NOW)
        .complication(ComponentKind.FRONT_DERAILLEUR)
    assertEquals("RD", gravelFd.code)
    assertEquals("GOOD", gravelFd.status)
  }

  @Test
  fun aPickedBikeOrTheDefaultOnceItsGone() {
    val snapshot = WearSnapshot(defaultBikeId = 1, bikes = listOf(road, gravel))
    assertEquals("Gravel bike", snapshot.bike(2)?.bike?.name)
    assertEquals("Road bike", snapshot.bike(3)?.bike?.name)
    assertEquals("Road bike", snapshot.bike(null)?.bike?.name)
  }

  @Test
  fun staleIsOld() {
    val stale = complication(road.copy(lastSeenAt = (NOW - 20.days).toEpochMilliseconds()))
    assertEquals("OLD", stale.status)
    assertEquals(StatusTone.Stale, stale.tone)
  }
}
