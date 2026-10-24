package com.r0adkll.cassette.app.ios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.r0adkll.cassette.common.compose.components.BarPlacement
import com.r0adkll.cassette.common.compose.components.SystemActionBar
import com.r0adkll.cassette.common.compose.components.SystemBarItem
import platform.UIKit.UIColor
import platform.UIKit.UIImage

/**
 * The iPhone Duo's vertical bar (iOS 27.1, decision 0017), which Swift implements
 * (`VerticalBarHost`): Kotlin's UIKit predates it. [show] replaces the bar's items, the screen's
 * back or close ([navigation]) at the top and its [actions] after them, empty lists clearing it;
 * [observe] reports whether the window has the bar, at once and whenever folding or opening the Duo
 * changes it.
 */
interface VerticalBar {
  fun show(navigation: List<VerticalBarItem>, actions: List<VerticalBarItem>)

  fun observe(onChange: (Boolean) -> Unit)
}

/**
 * A bar item: its accessibility label, its icon as a template image, any badge ([badgeText] on
 * [badgeBackground], or a dot where the text is null; no badge without a background), and the tap.
 */
class VerticalBarItem(
  val label: String,
  val image: UIImage,
  val badgeText: String?,
  val badgeBackground: UIColor?,
  val badgeForeground: UIColor?,
  val onClick: () -> Unit,
)

/** The screens' [SystemActionBar] over Swift's [VerticalBar]. */
internal class VerticalActionBar(private val bar: VerticalBar) : SystemActionBar {
  override var shown by mutableStateOf(false)
    private set

  /** Per placement, the screens that filled it, the one shown last. */
  private val stacks = BarPlacement.entries.associateWith { mutableListOf<Filled>() }

  private class Filled(val owner: Any, var items: List<SystemBarItem>)

  init {
    bar.observe { shown = it }
  }

  override fun show(owner: Any, placement: BarPlacement, items: List<SystemBarItem>) {
    val stack = stacks.getValue(placement)
    val filled = stack.find { it.owner === owner }
    if (filled != null) filled.items = items else stack += Filled(owner, items)
    update()
  }

  override fun clear(owner: Any) {
    var cleared = false
    stacks.values.forEach { stack -> if (stack.removeAll { it.owner === owner }) cleared = true }
    if (cleared) update()
  }

  private fun update() {
    fun at(placement: BarPlacement) =
      stacks.getValue(placement).lastOrNull()?.items.orEmpty().map {
        VerticalBarItem(
          it.label,
          it.image,
          it.badge?.text,
          it.badge?.background,
          it.badge?.foreground,
          it.onClick,
        )
      }
    bar.show(at(BarPlacement.Navigation), at(BarPlacement.Actions))
  }
}
