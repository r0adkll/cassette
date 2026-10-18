package com.r0adkll.cassette.common.compose

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much room the window gives the app (decision 0014). It follows the window, not the device, so
 * split screen and resizing pick the right layout.
 */
enum class WindowSize {
  /** Phones, a closed foldable, and phones in landscape (wide but too short): the phone layouts. */
  Narrow,
  /**
   * An open foldable such as the Pixel 10 Pro Fold (about 850 dp wide either way up), or a tablet
   * in portrait: the dashboard and a wide component sheet.
   */
  Medium,
  /** Tablets in landscape: the bike rail, the dashboard and a narrower component sheet. */
  Expanded;

  companion object {
    /**
     * Expanded starts at 1000 dp, not Material's 840: the open Fold is just over 840, and the 240
     * dp rail would leave its dashboard too little room.
     */
    fun of(width: Dp, height: Dp): WindowSize =
      when {
        width < 600.dp || height < 480.dp -> Narrow
        width < 1000.dp -> Medium
        else -> Expanded
      }
  }
}

val LocalWindowSize = staticCompositionLocalOf { WindowSize.Narrow }

/**
 * Measures the space it is given and provides it as [LocalWindowSize]; put it at the app's root.
 */
@Composable
fun ProvideWindowSize(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  BoxWithConstraints(modifier) {
    CompositionLocalProvider(LocalWindowSize provides WindowSize.of(maxWidth, maxHeight), content)
  }
}
