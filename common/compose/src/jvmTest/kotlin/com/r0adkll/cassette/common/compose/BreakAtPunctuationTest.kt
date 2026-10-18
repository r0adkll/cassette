package com.r0adkll.cassette.common.compose

import com.r0adkll.cassette.common.compose.components.breakAtPunctuation
import kotlin.test.Test
import kotlin.test.assertEquals

class BreakAtPunctuationTest {
  private val doesNotFit = { _: String -> false }
  private val fits = { _: String -> true }

  @Test
  fun aLongTitleBreaksAfterItsInnerPunctuation() {
    assertEquals(
      "Full tape.\nPress play.",
      breakAtPunctuation("Full tape. Press play.", doesNotFit),
    )
    assertEquals(
      "Wireless,\nnot careless.",
      breakAtPunctuation("Wireless, not careless.", doesNotFit),
    )
  }

  @Test
  fun aTitleThatFitsStaysOnOneLine() {
    assertEquals("Full tape. Press play.", breakAtPunctuation("Full tape. Press play.", fits))
  }

  @Test
  fun aTitleWithoutInnerPunctuationWrapsNormally() {
    assertEquals("No single-speed today.", breakAtPunctuation("No single-speed today.", doesNotFit))
  }
}
