package com.r0adkll.cassette.common.compose

import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenLayoutTest {
  /** The layout a window of this size gets, as [currentScreenLayout] computes it. */
  private fun layout(width: Int, height: Int, classes: SizeClasses = SizeClasses.Material) =
    ScreenLayout.of(sizeClass(width, height, classes), classes)

  private fun sizeClass(width: Int, height: Int, classes: SizeClasses = SizeClasses.Material) =
    classes.breakpoints.computeWindowSizeClass(width.toFloat(), height.toFloat())

  @Test
  fun androidKeepsMaterialsSizeClasses() {
    assertEquals(WindowSizeClass.BREAKPOINTS_V2, SizeClasses.Material.breakpoints)
  }

  @Test
  fun everyIpadInLandscapeGetsTheRail() {
    // iPad mini, iPad and iPad Air 11-inch, iPad Pro 11-inch, iPad Pro 13-inch (pt).
    for ((width, height) in listOf(1133 to 744, 1180 to 820, 1210 to 834, 1376 to 1032)) {
      assertEquals(ScreenLayout.DashboardWithRail, layout(width, height, SizeClasses.Apple))
    }
    // Material's large starts at 1200: the 11-inch iPads would keep the chips.
    assertEquals(ScreenLayout.Dashboard, layout(1180, 820))
  }

  @Test
  fun ipadsInPortraitAndTheOpenDuoKeepTheChips() {
    for ((width, height) in
      listOf(744 to 1133, 820 to 1180, 1032 to 1376, 951 to 669, 669 to 951)) {
      assertEquals(ScreenLayout.Dashboard, layout(width, height, SizeClasses.Apple))
    }
  }

  @Test
  fun iphonesGetTheSameLayoutsAsAndroidPhones() {
    // An iPhone 17 Pro upright and on its side, an iPhone SE on its side, the Duo closed.
    for ((width, height) in listOf(402 to 874, 874 to 402, 667 to 375, 466 to 678, 678 to 466)) {
      assertEquals(layout(width, height), layout(width, height, SizeClasses.Apple))
    }
  }

  @Test
  fun phonesGetTheColumn() {
    assertEquals(ScreenLayout.Column, layout(412, 892))
  }

  @Test
  fun phonesInLandscapeSplit() {
    assertEquals(ScreenLayout.Landscape, layout(892, 412))
    // Narrower than a phone on its side (split screen) keeps the column.
    assertEquals(ScreenLayout.Column, layout(480, 412))
  }

  @Test
  fun shortWindowsTooNarrowToSplitKeepTheColumn() {
    // The iPhone Duo closed, on its side, and an iPhone SE on its side: medium width.
    assertEquals(ScreenLayout.Column, layout(678, 466))
    assertEquals(ScreenLayout.Column, layout(667, 375))
    assertTrue(isShortMedium(sizeClass(678, 466)))
    assertTrue(isShortMedium(sizeClass(667, 375)))
    // A phone on its side is expanded, and splits; split screen is compact; a fold isn't short.
    assertFalse(isShortMedium(sizeClass(892, 412)))
    assertFalse(isShortMedium(sizeClass(480, 412)))
    assertFalse(isShortMedium(sizeClass(852, 883)))
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

  @Test
  fun onlyShortWindowsScrollTheirFooter() {
    assertTrue(isShort(sizeClass(892, 412)))
    assertFalse(isShort(sizeClass(412, 892)))
    // The open Fold on its side is still tall enough.
    assertFalse(isShort(sizeClass(883, 852)))
    assertFalse(isShort(sizeClass(1280, 800)))
  }
}
