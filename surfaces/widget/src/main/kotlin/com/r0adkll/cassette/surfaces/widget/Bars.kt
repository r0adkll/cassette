package com.r0adkll.cassette.surfaces.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp

/**
 * Glance can't size a box to a fraction, so bars are drawn as bitmaps at the widget's size: a
 * rounded track with a rounded fill of [level].
 */
internal fun barBitmap(
  width: Dp,
  height: Dp,
  density: Float,
  level: Float,
  track: Color,
  fill: Color,
  vertical: Boolean = false,
): Bitmap {
  val w = (width.value * density).toInt().coerceAtLeast(1)
  val h = (height.value * density).toInt().coerceAtLeast(1)
  val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  // Thick bars are pill-ish; vertical bars use the design's 10dp corners.
  val radius = if (vertical) minOf(10 * density, w / 2f) else h / 2.6f
  val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  paint.color = track.toArgb()
  canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)
  val fraction = level.coerceIn(0.06f, 1f)
  paint.color = fill.toArgb()
  val fillRect =
    if (vertical) RectF(0f, h * (1 - fraction), w.toFloat(), h.toFloat())
    else RectF(0f, 0f, w * fraction, h.toFloat())
  canvas.drawRoundRect(fillRect, radius, radius, paint)
  return bitmap
}
