package com.r0adkll.cassette.convention

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VersioningTest {
  @Test
  fun `release codes are the version then the form factor`() {
    assertEquals(1_020_300, releaseVersionCode("1.2.3", FormFactor.Phone))
    assertEquals(1_020_301, releaseVersionCode("1.2.3", FormFactor.Wear))
    assertEquals(10_200_001, releaseVersionCode("10.20.0", FormFactor.Wear))
  }

  @Test
  fun `every release code of one version is below every code of the next`() {
    val highest = releaseVersionCode("0.1.99", FormFactor.Wear)
    val lowest = releaseVersionCode("0.2.0", FormFactor.Phone)
    assertTrue(highest < lowest)
  }

  @Test
  fun `refuses versions it can't encode`() {
    assertFailsWith<IllegalStateException> { releaseVersionCode("1.0", FormFactor.Phone) }
    assertFailsWith<IllegalStateException> { releaseVersionCode("1.0.0-rc1", FormFactor.Phone) }
    assertFailsWith<IllegalArgumentException> { releaseVersionCode("1.100.0", FormFactor.Phone) }
  }

  @Test
  fun `drop codes are the commit count then the build type and form factor`() {
    assertEquals(5_810, dropVersionCode(58, "internal", FormFactor.Phone))
    assertEquals(5_811, dropVersionCode(58, "internal", FormFactor.Wear))
    assertEquals(5_820, dropVersionCode(58, "debug", FormFactor.Phone))
  }

  @Test
  fun `every drop code of one commit is below every code of the next`() {
    assertTrue(
      dropVersionCode(58, "debug", FormFactor.Wear) <
        dropVersionCode(59, "internal", FormFactor.Phone)
    )
  }

  @Test
  fun `drop codes only cover drop builds`() {
    assertFailsWith<IllegalStateException> { dropVersionCode(58, "release", FormFactor.Phone) }
  }

  @Test
  fun `baseline profile build types take the code of the build type they extend`() {
    assertEquals("release", extendedBuildType("nonMinifiedRelease"))
    assertEquals("release", extendedBuildType("benchmarkRelease"))
    assertEquals("internal", extendedBuildType("benchmarkInternal"))
    assertEquals("internal", extendedBuildType("internal"))
    assertEquals("debug", extendedBuildType("debug"))
  }
}
