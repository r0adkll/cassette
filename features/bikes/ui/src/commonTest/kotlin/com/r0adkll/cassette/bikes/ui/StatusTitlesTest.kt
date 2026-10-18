package com.r0adkll.cassette.bikes.ui

import com.r0adkll.cassette.bikes.summary.BikeStatus
import com.r0adkll.cassette.common.compose.theme.StatusTone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StatusTitlesTest {
  private val good = BikeStatus(StatusTone.Good, "All good.", "Lowest: rear derailleur, 78%.", "")

  @Test
  fun aTitleStaysForTheWholeSession() {
    val titles = StatusTitles(Random(1))
    val first = titles.title(good)
    repeat(20) { assertEquals(first, titles.title(good)) }
  }

  @Test
  fun eachSessionPicksAgainButNeverTheSameTitleTwiceInARow() {
    val titles = StatusTitles(Random(7))
    val seen = mutableListOf(titles.title(good))
    repeat(200) {
      titles.appBackgrounded()
      val title = titles.title(good)
      assertNotEquals(seen.last(), title)
      seen += title
    }
    // The plain title is on equal terms with the playful ones.
    assertEquals(setOf("All good.") + PlayfulTitles.getValue(StatusTone.Good), seen.toSet())
  }

  @Test
  fun statusesThatAskTheUserToActStayPlain() {
    val titles = StatusTitles(Random(3))
    val unknown =
      BikeStatus(
        StatusTone.Unknown,
        "No readings yet.",
        "Wake the bike so Cassette can read it.",
        "",
      )
    repeat(20) {
      titles.appBackgrounded()
      assertEquals("No readings yet.", titles.title(unknown))
    }
  }

  @Test
  fun staleKeepsTheCurrentDayCountInItsPlainTitle() {
    val titles = StatusTitles(Random(5))
    repeat(50) { day ->
      titles.appBackgrounded()
      val stale = BikeStatus(StatusTone.Stale, "Not seen in ${14 + day} days.", "", "")
      val title = titles.title(stale)
      if (title.startsWith("Not seen")) assertEquals(stale.headline, title)
    }
  }

  @Test
  fun pickIndexSkipsThePreviousPick() {
    val random = Random(11)
    repeat(500) {
      val previous = it % 4
      assertNotEquals(previous, pickIndex(4, previous, random))
    }
    assertEquals(0, pickIndex(1, previous = 0, random))
  }
}
