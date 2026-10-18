package com.r0adkll.cassette.common.compose

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_LARGE_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

/**
 * Which layout the window gets (decision 0014), from its Material window size class. It follows the
 * window, not the device, so split screen and resizing pick the right one.
 */
enum class ScreenLayout {
  /**
   * Compact width or compact height: phones, a closed foldable, and phones in landscape (wide but
   * too short). The phone layouts.
   */
  Column,

  /**
   * Medium or expanded width: an open foldable such as the Pixel 10 Pro Fold, or a tablet in
   * portrait. The dashboard with the bikes as chips, and a wide component sheet.
   */
  Dashboard,

  /**
   * Large width or wider: tablets in landscape. The bike rail beside the dashboard, and a narrower
   * component sheet. The open Fold is expanded, and the rail would leave its dashboard too little
   * room, so the rail waits for large.
   */
  DashboardWithRail;

  companion object {
    internal fun of(sizeClass: WindowSizeClass): ScreenLayout =
      when {
        !sizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) ||
          !sizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND) -> Column
        sizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_LARGE_LOWER_BOUND) -> DashboardWithRail
        else -> Dashboard
      }
  }
}

/** The window's layout; the app's root provides it from [currentScreenLayout]. */
val LocalScreenLayout = staticCompositionLocalOf { ScreenLayout.Column }

/** The layout for the current window's size class. */
@Composable
fun currentScreenLayout(): ScreenLayout =
  // V2 includes the large and extra-large width classes.
  ScreenLayout.of(currentWindowAdaptiveInfoV2().windowSizeClass)

/**
 * Where a half-open foldable folds, in window pixels, while it stands in tabletop (hinge
 * horizontal); null otherwise, and always on phones, tablets and iOS today. The bike screen splits
 * there (decision 0014).
 */
@Composable
fun currentTabletopHinge(): Rect? {
  val posture = currentWindowAdaptiveInfoV2().windowPosture
  if (!posture.isTabletop) return null
  return posture.hingeList.firstOrNull { !it.isVertical }?.bounds
}
