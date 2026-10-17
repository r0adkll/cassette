package com.r0adkll.cassette.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import com.r0adkll.cassette.common.compose.theme.CassetteTheme

/** Text sizes for a watch, from the design system's styles (iteration 5's Wear row). */
@Immutable
data class WatchType(
  /** "CASSETTE", "ROAD BIKE". */
  val kicker: TextStyle,
  /** A bike's name in the list. */
  val name: TextStyle,
  /** "ALL GOOD", "FD LOW" under a name. */
  val label: TextStyle,
  /** The status block's headline. */
  val headline: TextStyle,
  /** A component's code beside its bar. */
  val code: TextStyle,
  /** A component's value beside its bar. */
  val value: TextStyle,
  val body: TextStyle,
  val button: TextStyle,
)

private val LocalWatchType = staticCompositionLocalOf<WatchType> { error("WearTheme not applied") }

/**
 * Cassette on the watch: always dark on black, Barlow, and Wear Material 3 mapped from our colours.
 */
@Composable
fun WearTheme(content: @Composable () -> Unit) {
  CassetteTheme(darkTheme = true) {
    val colors = CassetteTheme.colors
    val type = CassetteTheme.type
    val watchType =
      WatchType(
        kicker = type.label.copy(fontSize = 12.sp),
        name = type.numeral.copy(fontSize = 17.sp),
        label = type.status.copy(fontSize = 11.sp),
        headline = type.display.copy(fontSize = 26.sp),
        code = type.numeral.copy(fontSize = 13.sp),
        value = type.numeral.copy(fontSize = 15.sp),
        body = type.body.copy(fontSize = 13.sp),
        button = type.button.copy(fontSize = 14.sp),
      )
    val barlow = type.body.fontFamily
    MaterialTheme(
      colorScheme =
        ColorScheme(
          primary = colors.ink,
          onPrimary = colors.onInk,
          primaryContainer = colors.track,
          onPrimaryContainer = colors.text,
          surfaceContainerLow = colors.panel,
          surfaceContainer = colors.track,
          surfaceContainerHigh = colors.line,
          onSurface = colors.text,
          onSurfaceVariant = colors.muted,
          outline = colors.line,
          background = Color.Black,
          onBackground = colors.text,
          error = colors.critical,
        ),
      typography = if (barlow != null) Typography(defaultFontFamily = barlow) else Typography(),
    ) {
      CompositionLocalProvider(LocalWatchType provides watchType, content = content)
    }
  }
}

object WearTheme {
  val type: WatchType
    @Composable @ReadOnlyComposable get() = LocalWatchType.current
}
