package com.r0adkll.cassette.common.compose

import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenLayoutTest {
  /** The size class a window of this size gets, as [currentScreenLayout] computes it. */
  private fun layout(width: Int, height: Int) =
    ScreenLayout.of(
      WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(width.toFloat(), height.toFloat())
    )

  @Test
  fun phonesGetTheColumn() {
    assertEquals(ScreenLayout.Column, layout(412, 892))
  }

  @Test
  fun phonesInLandscapeKeepTheColumn() {
    assertEquals(ScreenLayout.Column, layout(892, 412))
  }

  @Test
  fun anOpenFoldGetsTheDashboard() {
    // The Pixel 10 Pro Fold is expanded width, either way up.
    assertEquals(ScreenLayout.Dashboard, layout(852, 883))
    assertEquals(ScreenLayout.Dashboard, layout(883, 852))
  }

  @Test
  fun tabletsInPortraitGetTheDashboard() {
    assertEquals(ScreenLayout.Dashboard, layout(800, 1280))
  }

  @Test
  fun tabletsInLandscapeGetTheRail() {
    assertEquals(ScreenLayout.DashboardWithRail, layout(1280, 800))
  }
}
