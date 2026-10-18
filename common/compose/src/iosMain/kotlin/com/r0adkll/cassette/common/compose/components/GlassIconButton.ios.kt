package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.cValue
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.Foundation.create
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UIView
import platform.UIKit.UIVisualEffectView

/**
 * Native glass, laid over the Compose canvas so the system samples the content scrolling under it
 * (`placedAsOverlay`; by default an interop view sits below the canvas and would only see the
 * window). iOS 26 gets UIKit's glass button; earlier versions a frosted material with a light rim.
 * The Lucide icon is drawn into a template image, which the button tints.
 *
 * Prototype (G1): only Diagnostics uses it so far. Overlay views sit above all Compose content and
 * don't follow Compose's alpha or transforms, so they don't fade with screen transitions.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
@Composable
actual fun GlassIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier,
) {
  val colors = CassetteTheme.colors
  val density = LocalDensity.current
  val painter = rememberVectorPainter(icon)
  val image =
    remember(painter, density) {
      val px = with(density) { IconSize.roundToPx() }
      val bitmap = ImageBitmap(px, px)
      CanvasDrawScope().draw(
        density,
        LayoutDirection.Ltr,
        Canvas(bitmap),
        Size(px.toFloat(), px.toFloat()),
      ) {
        with(painter) { draw(size, colorFilter = ColorFilter.tint(Color.Black)) }
      }
      bitmap.toTemplateImage(density.density.toDouble())
    }
  val tint = colors.text.toUIColor()
  val dark = colors.isDark
  // CMP 1.12 doesn't route touches to overlay interop views (OverlayInputView.hitTest gives up on
  // views outside its own subviews), so the glass only draws: Compose takes the tap and speaks the
  // label, and a press shrinks the glass a little, as iOS's own glass buttons do.
  val interactions = remember { MutableInteractionSource() }
  val pressed by interactions.collectIsPressedAsState()
  val shown = LocalTopBarShown.current
  Box(
    modifier
      .size(ButtonSize)
      .clip(CircleShape)
      .clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
      .semantics { this.contentDescription = contentDescription }
  ) {
    UIKitView(
      // Starts hidden: it fades in once its screen has slid in (below).
      factory = { glassButton(image, dark).apply { alpha = 0.0 } },
      modifier = Modifier.matchParentSize(),
      update = { view ->
        val button = view.button
        button.tintColor = tint
        button.configuration?.let { config ->
          config.image = image
          config.baseForegroundColor = tint
          button.configuration = config
        }
        val scale = if (pressed) 0.92 else 1.0
        view.transform = CGAffineTransformMakeScale(scale, scale)
        // Native views ignore Compose's transitions: hide at once on the way out, and fade in
        // as an incoming screen's slide ends.
        val target = if (shown) 1.0 else 0.0
        if (view.alpha != target)
          UIView.animateWithDuration(
            if (shown) FADE_IN else FADE_OUT,
            delay = if (shown) FADE_IN_DELAY else 0.0,
            options = 0uL,
            animations = { view.alpha = target },
            completion = null,
          )
      },
      properties =
        UIKitInteropProperties(
          interactionMode = null,
          isNativeAccessibilityEnabled = false,
          placedAsOverlay = true,
        ),
    )
  }
}

private val ButtonSize = TopBarButtonSize

/** Seconds. The delay roughly matches a push or pop; a fresh screen's glass lands as it settles. */
private const val FADE_IN = 0.18
private const val FADE_IN_DELAY = 0.28
private const val FADE_OUT = 0.08
private val IconSize = 20.dp

/** The view holding the button: the button itself on iOS 26, its frosted backing before. */
@OptIn(ExperimentalForeignApi::class)
private class GlassView(val button: UIButton) : UIView(frame = cValue {})

@OptIn(ExperimentalForeignApi::class)
private val UIView.button: UIButton
  get() = (this as? GlassView)?.button ?: this as UIButton

@OptIn(ExperimentalForeignApi::class)
private fun glassButton(image: UIImage, dark: Boolean): UIView {
  if (isIos26) {
    val config = UIButtonConfiguration.glassButtonConfiguration()
    config.image = image
    return UIButton.buttonWithConfiguration(config, primaryAction = null).apply {
      backgroundColor = UIColor.clearColor
    }
  }
  // iOS 17–25: the system's thinnest material, a bright rim and a soft shadow (the G1 mock).
  val button = UIButton.buttonWithType(0L)
  button.setImage(image, forState = 0uL)
  val material =
    UIVisualEffectView(
      effect =
        UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemUltraThinMaterial)
    )
  val half = ButtonSize.value.toDouble() / 2
  return GlassView(button).apply {
    backgroundColor = UIColor.clearColor
    layer.shadowColor = UIColor.blackColor.CGColor
    layer.shadowOpacity = if (dark) 0.45f else 0.16f
    layer.shadowRadius = 12.0
    layer.shadowOffset = CGSizeMake(0.0, 6.0)
    material.layer.cornerRadius = half
    material.clipsToBounds = true
    material.layer.borderWidth = 1.0
    material.layer.borderColor =
      UIColor.whiteColor.colorWithAlphaComponent(if (dark) 0.22 else 0.75).CGColor
    material.setTranslatesAutoresizingMaskIntoConstraints(true)
    material.autoresizingMask = FLEXIBLE_SIZE
    button.autoresizingMask = FLEXIBLE_SIZE
    addSubview(material)
    material.contentView.addSubview(button)
  }
}

/** UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight */
private const val FLEXIBLE_SIZE = 18uL

@OptIn(ExperimentalForeignApi::class)
private val isIos26: Boolean by lazy {
  NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
    cValue<NSOperatingSystemVersion> { majorVersion = 26 }
  )
}

@OptIn(ExperimentalForeignApi::class)
private fun ImageBitmap.toTemplateImage(scale: Double): UIImage {
  val png = Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
  val data = png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()) }
  return UIImage.imageWithData(data, scale)!!.imageWithRenderingMode(
    UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate
  )
}

private fun Color.toUIColor(): UIColor =
  UIColor.colorWithRed(red.toDouble(), green.toDouble(), blue.toDouble(), alpha.toDouble())
