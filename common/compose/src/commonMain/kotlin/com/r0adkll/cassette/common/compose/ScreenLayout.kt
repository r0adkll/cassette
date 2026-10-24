package com.r0adkll.cassette.common.compose

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.toSize
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_LARGE_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.computeWindowSizeClass

/**
 * Which layout the window gets (decision 0014), from its window size class ([SizeClasses]). It
 * follows the window, not the device, so split screen and resizing pick the right one.
 */
enum class ScreenLayout {
  /** Compact width: phones, a closed foldable, split screen. The phone layouts. */
  Column,

  /**
   * Compact height and expanded width: a phone on its side (decision 0021). Too short for the
   * dashboard, so screens split in two: what the screen says and its buttons on the left, what it
   * lists on the right. A short window of medium width is too narrow to split and keeps [Column]
   * ([isShortMediumWindow]).
   */
  Landscape,

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
    internal fun of(sizeClass: WindowSizeClass, classes: SizeClasses): ScreenLayout =
      when {
        !sizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> Column
        isShortMedium(sizeClass) -> Column
        isShort(sizeClass) -> Landscape
        sizeClass.isWidthAtLeastBreakpoint(classes.largeWidth) -> DashboardWithRail
        else -> Dashboard
      }
  }
}

/**
 * The window size classes layouts are picked from, as an `androidx.window` breakpoint set (decision
 * 0025). Android keeps [Material]'s. Apple's devices get [Apple]: the same, except that large width
 * starts lower, so every iPad in landscape is large and gets the bike rail.
 */
enum class SizeClasses(
  /** Where large width starts, in dp; compact, medium and expanded are Material's on both. */
  val largeWidth: Int
) {
  /** Material's breakpoints (`WindowSizeClass.BREAKPOINTS_V2`). */
  Material(WIDTH_DP_LARGE_LOWER_BOUND),

  /**
   * Large from 1100 pt: every iPad in landscape (the mini's 1133 pt and up) but not a 13-inch iPad
   * in portrait (1032) or the open iPhone Duo on its side (951), which keep the dashboard's chips.
   */
  Apple(1100);

  /** The classes as `computeWindowSizeClass` picks from them. */
  val breakpoints: Set<WindowSizeClass> =
    listOf(
        0,
        WIDTH_DP_MEDIUM_LOWER_BOUND,
        WIDTH_DP_EXPANDED_LOWER_BOUND,
        largeWidth,
        WIDTH_DP_EXTRA_LARGE_LOWER_BOUND,
      )
      .flatMap { width ->
        listOf(0, HEIGHT_DP_MEDIUM_LOWER_BOUND, HEIGHT_DP_EXPANDED_LOWER_BOUND).map { height ->
          WindowSizeClass(minWidthDp = width, minHeightDp = height)
        }
      }
      .toSet()
}

/** This platform's size classes: [SizeClasses.Apple] on iOS, [SizeClasses.Material] elsewhere. */
internal expect val platformSizeClasses: SizeClasses

/** The window's layout; the app's root provides it from [currentScreenLayout]. */
val LocalScreenLayout = staticCompositionLocalOf { ScreenLayout.Column }

/**
 * The layout for the current window's size class. Renders pass [classes] to show what iOS would
 * pick.
 */
@Composable
fun currentScreenLayout(classes: SizeClasses = platformSizeClasses): ScreenLayout =
  ScreenLayout.of(currentWindowSizeClass(classes), classes)

/**
 * The window's size class among [classes]. Material's adaptive library computes only its own
 * (`currentWindowAdaptiveInfoV2`), so this measures the window as it does.
 */
@Composable
internal fun currentWindowSizeClass(classes: SizeClasses = platformSizeClasses): WindowSizeClass {
  val size =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.toSize().toDpSize() }
  return classes.breakpoints.computeWindowSizeClass(size.width.value, size.height.value)
}

/**
 * Whether the window is too short for a footer that stays put: compact height, such as a phone on
 * its side. Screens with a footer then scroll it with everything else (`FooterColumn`, decision
 * 0014).
 */
@Composable fun isShortWindow(): Boolean = isShort(currentWindowSizeClass())

internal fun isShort(sizeClass: WindowSizeClass): Boolean =
  !sizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)

/**
 * A short window wider than a phone but too narrow to split (compact height, medium width): a
 * passport-shaped fold's outer screen on its side, as the iPhone Duo's closed, or a small phone on
 * its side. Split in two, each pane is narrower than a phone; screens keep the phone's column
 * instead, with their footers scrolling ([isShortWindow]), and the bike screen takes the dashboard,
 * whose status band holds Refresh (decision 0024).
 */
@Composable fun isShortMediumWindow(): Boolean = isShortMedium(currentWindowSizeClass())

internal fun isShortMedium(sizeClass: WindowSizeClass): Boolean =
  isShort(sizeClass) &&
    sizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) &&
    !sizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

/**
 * The tabletop fold as the platform reports it, where Compose's window posture can't see the hinge:
 * the iPhone Duo's on iOS 27.1 (decision 0017), which the iOS app provides. In window pixels; null
 * while the device isn't in tabletop.
 */
val LocalPlatformTabletopHinge = compositionLocalOf<Rect?> { null }

/**
 * Where a half-open foldable folds, in window pixels, while it stands in tabletop (hinge
 * horizontal); null otherwise, and always on phones and tablets. The bike screen splits there
 * (decision 0014).
 */
@Composable
fun currentTabletopHinge(): Rect? {
  LocalPlatformTabletopHinge.current?.let {
    return it
  }
  val posture = currentWindowAdaptiveInfoV2().windowPosture
  if (!posture.isTabletop) return null
  return posture.hingeList.firstOrNull { !it.isVertical }?.bounds
}

/**
 * Where an open foldable folds when the hinge runs top to bottom (the Fold held open as a book), in
 * window pixels; null on devices without one. Two-pane screens split there (decision 0014).
 */
@Composable
fun currentVerticalHinge(): Rect? =
  currentWindowAdaptiveInfoV2().windowPosture.hingeList.firstOrNull { it.isVertical }?.bounds
