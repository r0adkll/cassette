package com.r0adkll.cassette.common.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.theme.CassetteRed
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import platform.UIKit.UIColor
import platform.UIKit.UIImage

/**
 * The host's bar for a screen's actions, which the iOS app provides ([LocalSystemActionBar]): the
 * iPhone Duo's vertical bar, whose items only Swift can make (Kotlin's UIKit predates iOS 27.1).
 * [shown] is snapshot state. Each placement shows the screen that filled it last; when that screen
 * clears it, the one before shows again (Settings' list once a tool in its pane closes), and a
 * screen leaving clears only its own.
 */
interface SystemActionBar {
  val shown: Boolean

  fun show(owner: Any, placement: BarPlacement, items: List<SystemBarItem>)

  fun clear(owner: Any)
}

/**
 * An action as the system bar takes it: its label, its icon as a template image, any badge, the
 * tap.
 */
class SystemBarItem(
  val label: String,
  val image: UIImage,
  val badge: SystemBarBadge?,
  val onClick: () -> Unit,
)

/** A badge on a bar item: [text] on [background], or a dot where [text] is null. */
class SystemBarBadge(val text: String?, val background: UIColor, val foreground: UIColor)

val LocalSystemActionBar = staticCompositionLocalOf<SystemActionBar?> { null }

@Composable actual fun hasSystemActionBar(): Boolean = LocalSystemActionBar.current?.shown == true

/**
 * While its screen is the active one ([LocalTopBarShown]): a screen on its way out clears its
 * items, and the incoming one shows its own.
 */
@Composable
actual fun SystemBarActions(actions: List<BarAction>, placement: BarPlacement) {
  val bar = LocalSystemActionBar.current ?: return
  val density = LocalDensity.current
  val colors = CassetteTheme.colors
  val painters = actions.map { rememberVectorPainter(it.icon) }
  // Taps reach the latest actions, so a recomposition doesn't rebuild the bar's items.
  val latest by rememberUpdatedState(actions)
  val labels = actions.map { it.label }
  val badges = actions.map { action ->
    val count = action.count
    when {
      count != null -> {
        val block = colors.block(count.tone)
        Triple(count.count.toString(), block.container, block.content)
      }
      action.badge -> Triple(null, CassetteRed, Color.White)
      else -> null
    }
  }
  val items =
    remember(labels, badges, painters, density) {
      actions.indices.map { i ->
        val badge =
          badges[i]?.let { (text, background, foreground) ->
            SystemBarBadge(text, background.toUIColor(), foreground.toUIColor())
          }
        SystemBarItem(labels[i], templateImage(painters[i], IconSize, density), badge) {
          latest.getOrNull(i)?.onClick?.invoke()
        }
      }
    }
  val shown = LocalTopBarShown.current
  val owner = remember { Any() }
  DisposableEffect(bar, placement, items, shown) {
    if (shown) bar.show(owner, placement, items) else bar.clear(owner)
    onDispose { bar.clear(owner) }
  }
}

private val IconSize = 20.dp
