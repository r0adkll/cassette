package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIView

/**
 * Native glass ([NativeGlass]): the selected bike in glass smoked with the ink colour, dark enough
 * for its text over any status colour and clear enough to stay glass, and the others in clear
 * glass. Before iOS 26, the smoke tints the frosted material. UIKit doesn't have the app's fonts,
 * so the dot and name are drawn into an image in their own colours.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun BikeChip(
  name: String,
  tone: StatusTone,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier,
) {
  val colors = CassetteTheme.colors
  val density = LocalDensity.current
  val measurer = rememberTextMeasurer()
  val style = chipLabelStyle
  val label = remember(name, style) { measurer.measure(name.uppercase(), style, maxLines = 1) }
  val text = if (selected) colors.onInk else colors.text
  val dot = colors.status(tone)
  val image = remember(label, text, dot, density) { chipImage(label, text, dot, density) }
  val smoke = colors.ink.copy(alpha = SMOKE).toUIColor()
  val dark = colors.isDark
  val width = with(density) { label.size.width.toDp() } + ChipDot + ChipGap + ChipPadding * 2
  NativeGlass(
    factory = { glassCapsule(dark, ChipHeight) },
    update = { view ->
      val button = view.button
      if (isIos26) {
        val config =
          if (selected) UIButtonConfiguration.prominentGlassButtonConfiguration()
          else UIButtonConfiguration.glassButtonConfiguration()
        config.image = image
        config.contentInsets = cValue {}
        button.tintColor = smoke
        button.configuration = config
      } else {
        button.setImage(image, forState = 0uL)
        (view as? GlassView)?.material?.contentView?.backgroundColor =
          if (selected) smoke else UIColor.clearColor
      }
    },
    role = Role.Tab,
    onClick = onClick,
    semantics = {
      contentDescription = name
      this.selected = selected
    },
    modifier = modifier.size(width, ChipHeight),
  )
}

/** How much of the ink colour smokes the selected chip's glass. */
private const val SMOKE = 0.45f

/** A clear glass button [height] tall, rounded at the ends (the chips, the bikes pill). */
@OptIn(ExperimentalForeignApi::class)
internal fun glassCapsule(dark: Boolean, height: Dp): UIView {
  if (isIos26)
    return UIButton.buttonWithConfiguration(
        UIButtonConfiguration.glassButtonConfiguration(),
        primaryAction = null,
      )
      .apply { backgroundColor = UIColor.clearColor }
  return frostedGlass(UIButton.buttonWithType(0L), height, dark)
}

/** The dot, then the name: the solid chip's row, without its padding. */
private fun chipImage(label: TextLayoutResult, text: Color, dot: Color, density: Density): UIImage {
  val dotPx = with(density) { ChipDot.toPx() }
  val gapPx = with(density) { ChipGap.toPx() }
  val width = ceil(dotPx + gapPx + label.size.width).toInt()
  val height = max(label.size.height, ceil(dotPx).toInt())
  val bitmap = ImageBitmap(width, height)
  CanvasDrawScope().draw(
    density,
    LayoutDirection.Ltr,
    Canvas(bitmap),
    Size(width.toFloat(), height.toFloat()),
  ) {
    drawCircle(dot, radius = dotPx / 2, center = Offset(dotPx / 2, height / 2f))
    drawText(
      label,
      color = text,
      topLeft = Offset(dotPx + gapPx, (height - label.size.height) / 2f),
    )
  }
  return bitmap.toUIImage(density.density.toDouble(), template = false)
}
