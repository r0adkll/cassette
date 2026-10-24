package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIView

/**
 * Native glass ([NativeGlass]): UIKit's glass button on iOS 26, the frosted material before. The
 * Lucide icon is drawn into a template image, which the button tints.
 */
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
  val image = remember(painter, density) { templateImage(painter, IconSize, density) }
  val tint = colors.text.toUIColor()
  val dark = colors.isDark
  NativeGlass(
    factory = { glassButton(image, dark) },
    update = { view ->
      val button = view.button
      button.tintColor = tint
      button.configuration?.let { config ->
        config.image = image
        config.baseForegroundColor = tint
        button.configuration = config
      }
    },
    role = Role.Button,
    onClick = onClick,
    semantics = { this.contentDescription = contentDescription },
    modifier = modifier.size(ButtonSize),
  )
}

private val ButtonSize = TopBarButtonSize
private val IconSize = 20.dp

@OptIn(ExperimentalForeignApi::class)
private fun glassButton(image: UIImage, dark: Boolean): UIView {
  if (isIos26) {
    val config = UIButtonConfiguration.glassButtonConfiguration()
    config.image = image
    return UIButton.buttonWithConfiguration(config, primaryAction = null).apply {
      backgroundColor = UIColor.clearColor
    }
  }
  val button = UIButton.buttonWithType(0L)
  button.setImage(image, forState = 0uL)
  return frostedGlass(button, ButtonSize, dark)
}
