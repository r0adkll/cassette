package com.r0adkll.cassette.common.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinksTest {
  @Test
  fun bikeOpensThatBike() {
    assertEquals(listOf(BikeScreen(3)), DeepLinks.parse(DeepLinks.bike(3)))
  }

  @Test
  fun componentOpensOverItsBike() {
    assertEquals(
      listOf(BikeScreen(3), ComponentScreen(12)),
      DeepLinks.parse(DeepLinks.component(3, 12)),
    )
  }

  @Test
  fun unknownLinksAreIgnored() {
    listOf(
        "https://bike/3",
        "cassette://bike",
        "cassette://bike/x",
        "cassette://bike/3/component",
        "cassette://bike/3/settings/1",
        "cassette://settings",
      )
      .forEach { assertNull(DeepLinks.parse(it), it) }
  }
}
