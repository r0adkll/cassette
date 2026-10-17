package com.r0adkll.cassette.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.SwipeToDismissBox
import com.slack.circuit.foundation.NavDecoration
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.navigation.NavArgument
import com.slack.circuit.runtime.navigation.NavStackList

/**
 * Wear's swipe-to-dismiss for Circuit: swiping right reveals the screen underneath and pops. The
 * root screen is left to the system, which closes the app.
 */
object SwipeDismissDecoration : NavDecoration {
  @Composable
  override fun <T : NavArgument> DecoratedContent(
    args: NavStackList<T>,
    navigator: Navigator,
    modifier: Modifier,
    content: @Composable (T) -> Unit,
  ) {
    val current = args.active
    val previous = args.backwardItems.firstOrNull()
    SwipeToDismissBox(
      onDismissed = { navigator.pop() },
      modifier = modifier,
      backgroundKey = previous?.key ?: "none",
      contentKey = current.key,
      userSwipeEnabled = previous != null,
    ) { isBackground ->
      if (isBackground) previous?.let { content(it) } else content(current)
    }
  }
}
