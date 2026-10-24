package com.r0adkll.cassette.bikes.ui

import com.r0adkll.cassette.common.compose.theme.StatusTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BikesSheetTest {
  private val bikes =
    listOf(
      BikeChoice(1, "Road bike", StatusTone.Critical, "2 min ago"),
      BikeChoice(2, "Gravel bike", StatusTone.Low, "3 days ago"),
      BikeChoice(3, "Commuter", StatusTone.Good, "Yesterday"),
    )

  @Test
  fun theBikeOnScreenDoesntCount() {
    // The road bike is the one showing, so only the gravel bike's low battery is news.
    val attention = bikes.attention(current = 1)!!
    assertEquals(StatusTone.Low, attention.tone)
    assertEquals(listOf("Gravel bike"), attention.names)
  }

  @Test
  fun chargeNowOutranksLow() {
    val attention = bikes.attention(current = 3)!!
    assertEquals(StatusTone.Critical, attention.tone)
    assertEquals(listOf("Road bike"), attention.names)
  }

  @Test
  fun nothingWhenTheOthersAreFine() {
    // Not seen for a while isn't a reason to switch.
    assertNull(bikes.map { it.copy(tone = StatusTone.Stale) }.attention(current = 1))
  }

  @Test
  fun theDescriptionNamesTheBikes() {
    assertEquals("3 bikes. Switch bike", bikesDescription(3, null))
    assertEquals(
      "3 bikes, Gravel bike is low. Switch bike",
      bikesDescription(3, BikesAttention(StatusTone.Low, listOf("Gravel bike"))),
    )
    assertEquals(
      "4 bikes, Road bike, Gravel bike and Commuter need charging now. Switch bike",
      bikesDescription(
        4,
        BikesAttention(StatusTone.Critical, listOf("Road bike", "Gravel bike", "Commuter")),
      ),
    )
  }

  @Test
  fun theKickerCountsThem() {
    assertEquals("3 bikes", bikesKicker(3, null))
    assertEquals(
      "3 bikes · 2 low",
      bikesKicker(3, BikesAttention(StatusTone.Low, listOf("Gravel bike", "Commuter"))),
    )
  }
}
