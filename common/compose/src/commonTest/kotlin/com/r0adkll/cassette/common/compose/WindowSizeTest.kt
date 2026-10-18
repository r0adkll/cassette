package com.r0adkll.cassette.common.compose

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowSizeTest {
  @Test
  fun phonesAreNarrow() {
    assertEquals(WindowSize.Narrow, WindowSize.of(412.dp, 892.dp))
  }

  @Test
  fun phonesInLandscapeStayNarrow() {
    assertEquals(WindowSize.Narrow, WindowSize.of(892.dp, 412.dp))
  }

  @Test
  fun anOpenFoldIsMedium() {
    assertEquals(WindowSize.Medium, WindowSize.of(790.dp, 820.dp))
  }

  @Test
  fun tabletsInLandscapeAreExpanded() {
    assertEquals(WindowSize.Expanded, WindowSize.of(1280.dp, 800.dp))
  }

  @Test
  fun tabletsInPortraitAreMedium() {
    assertEquals(WindowSize.Medium, WindowSize.of(800.dp, 1280.dp))
  }

  @Test
  fun breakpointsAreInclusive() {
    assertEquals(WindowSize.Medium, WindowSize.of(600.dp, 480.dp))
    assertEquals(WindowSize.Expanded, WindowSize.of(840.dp, 480.dp))
  }
}
