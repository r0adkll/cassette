package com.r0adkll.cassette.convention

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VersioningTest {
  @Test
  fun `bakes the build type and form factor into the last two digits`() {
    assertEquals(102_039_900, versionCode("1.2.3", "release", FormFactor.Phone))
    assertEquals(102_039_901, versionCode("1.2.3", "release", FormFactor.Wear))
    assertEquals(102_039_911, versionCode("1.2.3", "internal", FormFactor.Wear))
    assertEquals(102_039_920, versionCode("1.2.3", "debug", FormFactor.Phone))
  }

  @Test
  fun `release candidates come after the last release and before their own`() {
    val previous = versionCode("0.9.0", "release", FormFactor.Wear)
    val rc3 = versionCode("1.0.0-rc3", "release", FormFactor.Wear)
    val final = versionCode("1.0.0", "release", FormFactor.Phone)
    assertTrue(previous < rc3 && rc3 < final)
  }

  @Test
  fun `every code of one version is below every code of the next`() {
    val highest = versionCode("0.1.0", "debug", FormFactor.Wear)
    val lowest = versionCode("0.1.1-rc1", "release", FormFactor.Phone)
    assertTrue(highest < lowest)
  }

  @Test
  fun `fits Play's ceiling`() {
    assertTrue(versionCode("20.99.99", "debug", FormFactor.Wear) <= 2_100_000_000)
    assertFailsWith<IllegalArgumentException> {
      versionCode("21.0.0", "release", FormFactor.Phone)
    }
  }

  @Test
  fun `refuses what it can't encode`() {
    assertFailsWith<IllegalStateException> { versionCode("1.0", "release", FormFactor.Phone) }
    assertFailsWith<IllegalStateException> {
      versionCode("1.0.0-alpha", "release", FormFactor.Phone)
    }
    assertFailsWith<IllegalStateException> {
      versionCode("1.0.0", "benchmark", FormFactor.Phone)
    }
  }
}
