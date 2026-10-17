package com.r0adkll.cassette.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.slack.circuit.foundation.NavDecoration
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.navigation.NavArgument
import com.slack.circuit.runtime.navigation.NavStackList

/**
 * Gives every screen its own opaque background, edge to edge, so during a transition the outgoing
 * screen's content never shows through the incoming one. Content stays inside the system bars.
 */
class OpaqueScreens(private val delegate: NavDecoration, private val background: Color) :
  NavDecoration {
  @Composable
  override fun <T : NavArgument> DecoratedContent(
    args: NavStackList<T>,
    navigator: Navigator,
    modifier: Modifier,
    content: @Composable (T) -> Unit,
  ) {
    delegate.DecoratedContent(args, navigator, modifier) { arg ->
      Box(Modifier.fillMaxSize().background(background).safeDrawingPadding()) { content(arg) }
    }
  }
}
