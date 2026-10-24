package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
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
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UIView
import platform.UIKit.UIVisualEffectView

/**
 * A native glass view laid over the Compose canvas (decision 0017), so the system samples the
 * content scrolling under it (`placedAsOverlay`; by default an interop view sits below the canvas
 * and would only see the window). [update] sets the view up from the composition's values.
 *
 * CMP 1.12 doesn't route touches to overlay interop views (OverlayInputView.hitTest gives up on
 * views outside its own subviews), so the glass only draws: Compose takes the tap and speaks the
 * label, and a press shrinks the glass a little, as iOS's own glass buttons do. Overlay views sit
 * above all Compose content and don't follow Compose's alpha or transforms, so the glass hides
 * while its screen is on its way in or out ([LocalTopBarShown]).
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
@Composable
internal fun NativeGlass(
  factory: () -> UIView,
  update: (UIView) -> Unit,
  role: Role,
  onClick: () -> Unit,
  semantics: SemanticsPropertyReceiver.() -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactions = remember { MutableInteractionSource() }
  val pressed by interactions.collectIsPressedAsState()
  val shown = LocalTopBarShown.current
  Box(
    modifier
      .clip(CircleShape)
      .clickable(interactions, indication = null, role = role, onClick = onClick)
      .semantics(properties = semantics)
  ) {
    UIKitView(
      // Starts hidden: it fades in once its screen has slid in (below).
      factory = { factory().apply { alpha = 0.0 } },
      modifier = Modifier.matchParentSize(),
      update = { view ->
        update(view)
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

/** Seconds. The delay roughly matches a push or pop; a fresh screen's glass lands as it settles. */
private const val FADE_IN = 0.18
private const val FADE_IN_DELAY = 0.28
private const val FADE_OUT = 0.08

/**
 * Before iOS 26: the button on the system's thinnest material, with a bright rim and a soft shadow
 * (the G1 mock). [material]'s content view takes a fill where the glass would be tinted.
 */
@OptIn(ExperimentalForeignApi::class)
internal class GlassView(val button: UIButton, val material: UIVisualEffectView) :
  UIView(frame = cValue {})

/** The view's button: the view itself on iOS 26, inside its [GlassView] before. */
@OptIn(ExperimentalForeignApi::class)
internal val UIView.button: UIButton
  get() = (this as? GlassView)?.button ?: this as UIButton

/** The frosted [GlassView] around [button], rounded to [height]'s half. */
@OptIn(ExperimentalForeignApi::class)
internal fun frostedGlass(button: UIButton, height: Dp, dark: Boolean): GlassView {
  val material =
    UIVisualEffectView(
      effect =
        UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemUltraThinMaterial)
    )
  return GlassView(button, material).apply {
    backgroundColor = UIColor.clearColor
    layer.shadowColor = UIColor.blackColor.CGColor
    layer.shadowOpacity = if (dark) 0.45f else 0.16f
    layer.shadowRadius = 12.0
    layer.shadowOffset = CGSizeMake(0.0, 6.0)
    material.layer.cornerRadius = height.value.toDouble() / 2
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
internal val isIos26: Boolean by lazy {
  NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
    cValue<NSOperatingSystemVersion> { majorVersion = 26 }
  )
}

/** [painter] (a Lucide icon) drawn into a template image of [iconSize], which UIKit tints. */
internal fun templateImage(painter: Painter, iconSize: Dp, density: Density): UIImage {
  val px = with(density) { iconSize.roundToPx() }
  val bitmap = ImageBitmap(px, px)
  CanvasDrawScope().draw(
    density,
    LayoutDirection.Ltr,
    Canvas(bitmap),
    Size(px.toFloat(), px.toFloat()),
  ) {
    with(painter) { draw(size, colorFilter = ColorFilter.tint(Color.Black)) }
  }
  return bitmap.toUIImage(density.density.toDouble(), template = true)
}

/** A template image takes the button's tint; otherwise it keeps its own colours. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ImageBitmap.toUIImage(scale: Double, template: Boolean): UIImage {
  val png = Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
  val data = png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()) }
  return UIImage.imageWithData(data, scale)!!.imageWithRenderingMode(
    if (template) UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate
    else UIImageRenderingMode.UIImageRenderingModeAlwaysOriginal
  )
}

internal fun Color.toUIColor(): UIColor =
  UIColor.colorWithRed(red.toDouble(), green.toDouble(), blue.toDouble(), alpha.toDouble())
