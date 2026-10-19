package com.r0adkll.cassette.convention

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VersioningTest {
  @Test
  fun `bakes the build type and form factor into the last two digits`() {
    assertEquals(1_020_300, versionCode("1.2.3", "release", FormFactor.Phone))
    assertEquals(1_020_301, versionCode("1.2.3", "release", FormFactor.Wear))
    assertEquals(1_020_311, versionCode("1.2.3", "internal", FormFactor.Wear))
    assertEquals(1_020_320, versionCode("1.2.3", "debug", FormFactor.Phone))
  }

  @Test
  fun `every code of one version is below every code of the next`() {
    val highest = versionCode("0.1.99", "debug", FormFactor.Wear)
    val lowest = versionCode("0.2.0", "release", FormFactor.Phone)
    assertTrue(highest < lowest)
  }

  @Test
  fun `refuses what it can't encode`() {
    assertFailsWith<IllegalStateException> { versionCode("1.0", "release", FormFactor.Phone) }
    assertFailsWith<IllegalStateException> {
      versionCode("1.0.0-rc1", "release", FormFactor.Phone)
    }
    assertFailsWith<IllegalArgumentException> {
      versionCode("1.100.0", "release", FormFactor.Phone)
    }
    assertFailsWith<IllegalStateException> {
      versionCode("1.0.0", "benchmark", FormFactor.Phone)
    }
  }
}
