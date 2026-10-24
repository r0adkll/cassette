package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIImage

/**
 * Clear native glass ([NativeGlass]), as the unselected chips and the top bar's buttons. UIKit
 * doesn't have the app's fonts, so the pill's icons and words are drawn into an image in their own
 * colours.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformBikesPill(
  count: Int,
  attention: StatusTone?,
  attentionLabel: String?,
  description: String,
  onClick: () -> Unit,
  modifier: Modifier,
) {
  val colors = CassetteTheme.colors
  val density = LocalDensity.current
  val measurer = rememberTextMeasurer()
  val style = chipLabelStyle
  val countText = remember(count, style) { measurer.measure(pillCount(count), style, maxLines = 1) }
  val attentionText =
    remember(attention, attentionLabel, style) {
      attentionLabel
        ?.takeIf { attention != null }
        ?.let { measurer.measure(it, style, maxLines = 1) }
    }
  val bike = rememberVectorPainter(LucideIcons.Bike)
  val toneIcon = rememberVectorPainter((attention ?: StatusTone.Good).icon)
  val chevron = rememberVectorPainter(LucideIcons.ChevronDown)
  val inks =
    PillInks(
      text = colors.text,
      tone = attention?.let { colors.status(it) } ?: colors.text,
      line = colors.line,
      chevron = colors.muted,
    )
  val pill =
    remember(countText, attentionText, inks, density) {
      pillImage(countText, attentionText, bike, toneIcon, chevron, inks, density)
    }
  val image = pill.image
  val width = with(density) { pill.width.toDp() } + PillPaddingStart + PillPaddingEnd
  val dark = colors.isDark
  NativeGlass(
    factory = { glassCapsule(dark, TopBarButtonSize) },
    update = { view ->
      val button = view.button
      if (isIos26) {
        val config = UIButtonConfiguration.glassButtonConfiguration()
        config.image = image
        config.contentInsets = cValue {}
        button.configuration = config
      } else {
        button.setImage(image, forState = 0uL)
      }
    },
    role = Role.Button,
    onClick = onClick,
    semantics = { contentDescription = description },
    modifier = modifier.size(width, TopBarButtonSize),
  )
}

/** The pill's colours: its words and icons, the attention tone's, the divider and the chevron. */
private data class PillInks(val text: Color, val tone: Color, val line: Color, val chevron: Color)

/** The pill's drawn row and its [width] in pixels. */
private class PillImage(val image: UIImage, val width: Float)

/** The solid pill's row without its padding: bike, count, then any attention, then the chevron. */
private fun pillImage(
  count: TextLayoutResult,
  attention: TextLayoutResult?,
  bike: Painter,
  tone: Painter,
  chevron: Painter,
  inks: PillInks,
  density: Density,
): PillImage {
  val icon = with(density) { PillIcon.toPx() }
  val chevronSize = with(density) { PillChevron.toPx() }
  val gap = with(density) { PillGap.toPx() }
  val rule = density.density
  val divider = with(density) { PillDivider.toPx() }
  val width = density.pillRowWidth(count.size.width.toFloat(), attention?.size?.width?.toFloat())
  val height =
    max(max(icon, divider), max(count.size.height, attention?.size?.height ?: 0).toFloat())
  val bitmap = ImageBitmap(ceil(width).toInt(), ceil(height).toInt())
  CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(width, height)) {
    var x = 0f
    x = drawIcon(bike, x, icon, inks.text) + gap
    drawText(count, color = inks.text, topLeft = Offset(x, (height - count.size.height) / 2f))
    x += count.size.width + gap
    if (attention != null) {
      drawRect(inks.line, Offset(x, (height - divider) / 2f), Size(rule, divider))
      x += rule + gap
      x = drawIcon(tone, x, icon, inks.tone) + gap
      drawText(
        attention,
        color = inks.tone,
        topLeft = Offset(x, (height - attention.size.height) / 2f),
      )
      x += attention.size.width + gap
    }
    drawIcon(chevron, x, chevronSize, inks.chevron)
  }
  return PillImage(bitmap.toUIImage(density.density.toDouble(), template = false), width)
}

/** Draws [painter] [size] square at [x], centred down the image; returns where it ends. */
private fun DrawScope.drawIcon(painter: Painter, x: Float, size: Float, color: Color): Float {
  translate(x, (this.size.height - size) / 2f) {
    with(painter) { draw(Size(size, size), colorFilter = ColorFilter.tint(color)) }
  }
  return x + size
}
