package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.theme.CassetteRed
import com.r0adkll.cassette.common.compose.theme.CassetteTheme

/** The height a screen's first row keeps free for the [FloatingTopBar] over it. */
val TopBarButtonSize = 46.dp

/**
 * False while the screen is on its way in or out (the app's nav decoration provides it). iOS's
 * glass buttons are native views drawn above every Compose layer and don't follow its fades, so
 * they hide meanwhile instead of covering the screen sliding over them.
 */
val LocalTopBarShown = compositionLocalOf { true }

/**
 * A screen's controls (back, close, settings, add bike), floating over its scrolling content so
 * they never scroll away (iteration 14). Lay it over the scroll column with the same width and
 * padding as the column's first row, which keeps [TopBarButtonSize] of height for it and holds any
 * text that belongs beside the buttons (that text scrolls).
 */
@Composable
fun FloatingTopBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
  Row(
    modifier.fillMaxWidth().height(TopBarButtonSize),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    content = content,
  )
}

/**
 * A top-bar button: Liquid Glass on iOS, the solid circle elsewhere ([GlassIconButton]). [badge]
 * adds a red dot for something waiting behind it (an app update behind Settings); iOS has no app
 * updates, and its glass would cover the dot.
 */
@Composable
fun TopBarButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  badge: Boolean = false,
) {
  Box(modifier) {
    GlassIconButton(icon, contentDescription, onClick)
    if (badge)
      Box(
        Modifier.align(Alignment.TopEnd)
          .size(12.dp)
          .clip(CircleShape)
          .background(CassetteTheme.colors.background)
          .padding(2.dp)
          .clip(CircleShape)
          .background(CassetteRed)
      )
  }
}
