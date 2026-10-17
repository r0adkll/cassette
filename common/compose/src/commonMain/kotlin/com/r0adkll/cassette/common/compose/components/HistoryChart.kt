package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.theme.CassetteTheme

/**
 * Battery history: a 4dp line over a filled area, a dashed line at [lowThreshold], and a dot at
 * each charge (soft-modern chart style from decision 0012). [levels] are 0 to 1, oldest first.
 */
@Composable
fun HistoryChart(
  levels: List<Float>,
  modifier: Modifier = Modifier,
  lowThreshold: Float = 0.2f,
  chargeJump: Float = 0.3f,
) {
  val colors = CassetteTheme.colors
  Canvas(modifier.fillMaxWidth().height(120.dp)) {
    if (levels.size < 2) return@Canvas
    val inset = 6.dp.toPx()
    val w = size.width - inset * 2
    val h = size.height - inset * 2
    fun point(i: Int) =
      Offset(inset + w * i / (levels.size - 1), inset + h * (1f - levels[i].coerceIn(0f, 1f)))

    val lowY = inset + h * (1f - lowThreshold)
    drawLine(
      colors.muted,
      Offset(0f, lowY),
      Offset(size.width, lowY),
      strokeWidth = 1.5.dp.toPx(),
      cap = StrokeCap.Round,
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())),
    )

    val line =
      Path().apply {
        moveTo(point(0).x, point(0).y)
        for (i in 1 until levels.size) lineTo(point(i).x, point(i).y)
      }
    val area =
      Path().apply {
        addPath(line)
        lineTo(point(levels.lastIndex).x, size.height)
        lineTo(point(0).x, size.height)
        close()
      }
    drawPath(area, colors.text.copy(alpha = if (colors.isDark) 0.10f else 0.08f))
    drawPath(
      line,
      colors.text,
      style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    for (i in 1 until levels.size) {
      if (levels[i] - levels[i - 1] > chargeJump) {
        drawCircle(colors.panel, radius = 8.dp.toPx(), center = point(i))
        drawCircle(colors.good, radius = 5.5.dp.toPx(), center = point(i))
      }
    }
    drawCircle(colors.text, radius = 5.5.dp.toPx(), center = point(levels.lastIndex))
  }
}
