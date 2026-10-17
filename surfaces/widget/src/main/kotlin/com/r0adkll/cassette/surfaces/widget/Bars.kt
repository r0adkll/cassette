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

/**
 * Display text in Barlow Condensed Bold. RemoteViews can only use system fonts, so the big type the
 * design leans on (headlines, bike names) is drawn into a bitmap; callers pass the text as the
 * image's content description.
 */
internal fun displayTextBitmap(
  context: android.content.Context,
  text: String,
  sizeSp: Float,
  color: Color,
  maxWidth: Dp,
  maxLines: Int = 1,
): Bitmap {
  val density = context.resources.displayMetrics.density
  val paint =
    android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      typeface = context.resources.getFont(R.font.barlow_condensed_bold)
      textSize = sizeSp * context.resources.displayMetrics.scaledDensity
      this.color = color.toArgb()
      letterSpacing = -0.02f
    }
  val width = (maxWidth.value * density).toInt().coerceAtLeast(1)
  val layout =
    android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
      .setMaxLines(maxLines)
      .setEllipsize(android.text.TextUtils.TruncateAt.END)
      .setLineSpacing(0f, 0.92f)
      .setIncludePad(false)
      .build()
  val lineWidth =
    (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }.toInt().coerceAtLeast(1)
  val bitmap =
    Bitmap.createBitmap(lineWidth, layout.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
  layout.draw(Canvas(bitmap))
  return bitmap
}
