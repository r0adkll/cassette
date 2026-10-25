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
        "cassette://drop/Ab12Cd34",
      )
      .forEach { assertNull(DeepLinks.parse(it), it) }
  }

  @Test
  fun dropLinkCarriesItsBuild() {
    assertEquals("Ab12Cd34", DeepLinks.dropBuild("cassette://drop/Ab12Cd34"))
    assertEquals("Ab12Cd34", DeepLinks.dropBuild("cassette://drop/Ab12Cd34/"))
  }

  @Test
  fun otherLinksCarryNoBuild() {
    listOf(
        "cassette://drop",
        "cassette://drop/",
        "cassette://drop/Ab12Cd34/install",
        "cassette://bike/3",
        "drop-sample://drop/Ab12Cd34",
        "https://drop.thescavengers.software/drop/Ab12Cd34",
      )
      .forEach { assertNull(DeepLinks.dropBuild(it), it) }
  }
}
