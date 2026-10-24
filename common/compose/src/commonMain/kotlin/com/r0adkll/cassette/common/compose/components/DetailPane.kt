package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.ScreenLayout
import com.r0adkll.cassette.common.compose.theme.CassetteTheme

/**
 * True inside a list–detail screen's detail pane (Settings' open category, beside the list), so
 * screens shown there (Diagnostics and its tools) lay out as the pane's own content does: at most
 * [DetailPaneWidth] wide from the pane's start, [DetailPaneGutter] in from its sides, and the title
 * level with the list's ([DetailPaneTitle]).
 */
val LocalInDetailPane = staticCompositionLocalOf { false }

/** The widest a detail pane's content runs, so rows don't stretch across a tablet. */
val DetailPaneWidth = 640.dp

/** A detail pane's side padding. */
val DetailPaneGutter = 32.dp

/**
 * A detail pane's title, level with the list's: under the list's back button, or beside it on a
 * phone on its side, which is short on height (decision 0021).
 */
@Composable
fun DetailPaneTitle(text: String) {
  val colors = CassetteTheme.colors
  if (LocalScreenLayout.current == ScreenLayout.Landscape) {
    Box(Modifier.heightIn(min = TopBarButtonSize), contentAlignment = Alignment.CenterStart) {
      Text(text, style = CassetteTheme.type.title, color = colors.text)
    }
  } else {
    TopBarSpacer()
    Text(text, style = CassetteTheme.type.title, color = colors.text)
  }
}
