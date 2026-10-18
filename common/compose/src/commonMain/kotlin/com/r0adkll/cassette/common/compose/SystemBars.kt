package com.r0adkll.cassette.common.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * Screens draw edge to edge (decision 0016): backgrounds, panes and dividers run under the system
 * bars, and each screen pads its content back inside them with safeDrawingPadding, placed after
 * its verticalScroll so the content scrolls under the bars. These keep a pane that doesn't reach
 * an edge from padding by that edge's bar.
 */

/** A pane at the start edge (a list, a rail): its content doesn't pad by the end bar. */
@Composable
fun Modifier.startPane(): Modifier =
  consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.End))

/** A pane at the end edge (a detail, a sheet): its content doesn't pad by the start bar. */
@Composable
fun Modifier.endPane(): Modifier =
  consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
