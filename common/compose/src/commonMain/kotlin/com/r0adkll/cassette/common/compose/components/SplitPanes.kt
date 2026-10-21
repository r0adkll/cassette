package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.currentVerticalHinge
import com.r0adkll.cassette.common.compose.endPane
import com.r0adkll.cassette.common.compose.startPane
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import kotlin.math.roundToInt

/**
 * Two panes side by side (decision 0014): split where an open foldable folds, so nothing sits on
 * the hinge, or down the middle where there's no hinge (a tablet). [hinge] is in window pixels;
 * renders pass their own. Each pane runs under the top and bottom bars and sees only its own side's
 * bar (decision 0016). [divider] draws a line between them, as on a phone on its side (decision
 * 0021), where the panes share a background.
 */
@Composable
fun SplitPanes(
  start: @Composable () -> Unit,
  end: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  hinge: Rect? = currentVerticalHinge(),
  divider: Boolean = false,
) {
  val line = CassetteTheme.colors.line
  var left by remember { mutableFloatStateOf(0f) }
  Layout(
    contents =
      listOf(
        { Box(Modifier.startPane(), propagateMinConstraints = true) { start() } },
        {
          Box(
            Modifier.endPane()
              .then(
                if (divider)
                  Modifier.drawWithContent {
                    drawContent()
                    drawLine(line, Offset.Zero, Offset(0f, size.height), 1.dp.toPx())
                  }
                else Modifier
              ),
            propagateMinConstraints = true,
          ) {
            end()
          }
        },
      ),
    modifier = modifier.onGloballyPositioned { left = it.positionInWindow().x },
  ) { (startPane, endPane), constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    // The hinge in this layout's own coordinates; a hinge outside it (split screen) is ignored.
    val fold =
      hinge
        ?.let { (it.left - left) to (it.right - left) }
        ?.takeIf { (from, to) ->
          from > 0 && to < width
        }
    val split = fold?.first?.roundToInt() ?: (width / 2)
    val endStart = fold?.second?.roundToInt() ?: split
    val startPlaceable = startPane.first().measure(Constraints.fixed(split, height))
    val endPlaceable = endPane.first().measure(Constraints.fixed(width - endStart, height))
    layout(width, height) {
      startPlaceable.place(0, 0)
      endPlaceable.place(endStart, 0)
    }
  }
}
