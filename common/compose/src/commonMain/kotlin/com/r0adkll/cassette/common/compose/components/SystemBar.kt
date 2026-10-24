package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.r0adkll.cassette.common.compose.theme.StatusTone

/**
 * A screen action for its top bar, or for the system's own bar where there is one. [badge] is a dot
 * for something waiting behind it; [count] is a number on it in the system's bar (the Duo's).
 */
class BarAction(
  val icon: ImageVector,
  val label: String,
  val badge: Boolean = false,
  val count: BarCount? = null,
  val onClick: () -> Unit,
)

/** A number on a bar action, in the status block's colours for [tone] ("1" bike low, in amber). */
class BarCount(val count: Int, val tone: StatusTone)

/**
 * Where items go in the system's action bar: the screen's back or close at the top, its actions
 * after them (Apple's guidance for the iPhone Duo).
 */
enum class BarPlacement {
  Navigation,
  Actions,
}

/**
 * Whether the window has a system bar for a screen's top-bar buttons, which screens then use
 * instead of floating their own: on iOS 27.1, the iPhone Duo's vertical bar under the status bar
 * (decision 0017). It comes and goes as the Duo folds and opens; false elsewhere.
 */
@Composable expect fun hasSystemActionBar(): Boolean

/**
 * Shows [actions] at [placement] in the system's action bar while composed, where
 * [hasSystemActionBar].
 */
@Composable expect fun SystemBarActions(actions: List<BarAction>, placement: BarPlacement)

/**
 * A screen's back or close: first in the system's action bar where the window has one, otherwise a
 * [TopBarButton] at the start of its [FloatingTopBar].
 */
@Composable
fun TopBarNavigation(icon: ImageVector, label: String, onClick: () -> Unit) {
  if (hasSystemActionBar()) {
    SystemBarActions(listOf(BarAction(icon, label, onClick = onClick)), BarPlacement.Navigation)
  } else {
    TopBarButton(icon, label, onClick)
  }
}

/**
 * A screen's top-bar actions: in the system's action bar where the window has one, after its
 * [TopBarNavigation], otherwise as [TopBarButton]s in its [FloatingTopBar].
 */
@Composable
fun TopBarActions(actions: List<BarAction>) {
  if (hasSystemActionBar()) {
    SystemBarActions(actions, BarPlacement.Actions)
  } else {
    actions.forEach { TopBarButton(it.icon, it.label, it.onClick, badge = it.badge) }
  }
}

/**
 * The room a scroll column keeps at its top for a [FloatingTopBar] that holds only the screen's
 * [TopBarNavigation]. None where the system's bar takes that button, so the title moves up.
 */
@Composable
fun TopBarSpacer() {
  if (!hasSystemActionBar()) Spacer(Modifier.height(TopBarButtonSize))
}
