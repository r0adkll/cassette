package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.r0adkll.cassette.common.compose.isShortWindow

/**
 * The column of a screen with a footer (Get started, Continue, add bike's buttons): [body] scrolls
 * between [top] and [footer], so the footer always sits above the navigation bar (decision 0016). A
 * short window, such as a phone on its side, has no room for that, so the whole column scrolls
 * instead, the footer last and still at the foot when everything fits (decision 0014).
 *
 * [modifier] sizes the column; [padding] (the insets, then any margins) goes inside the scroll so
 * content runs under the bars. [body] is laid out with [verticalArrangement] as well.
 */
@Composable
fun FooterColumn(
  footer: @Composable ColumnScope.() -> Unit,
  modifier: Modifier = Modifier,
  padding: Modifier = Modifier,
  verticalArrangement: Arrangement.Vertical = Arrangement.Top,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  top: @Composable ColumnScope.() -> Unit = {},
  body: @Composable ColumnScope.() -> Unit,
) {
  if (!isShortWindow()) {
    Column(modifier.then(padding), verticalArrangement, horizontalAlignment) {
      top()
      Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement,
        horizontalAlignment,
      ) {
        body()
      }
      footer()
    }
    return
  }
  ScrollColumn(modifier, padding, verticalArrangement, horizontalAlignment) {
    top()
    Column(verticalArrangement = verticalArrangement, horizontalAlignment = horizontalAlignment) {
      body()
    }
    // Pushes the footer down when there's room.
    Spacer(Modifier.weight(1f))
    footer()
  }
}

/**
 * A column that scrolls but is at least as tall as its space, so [verticalArrangement] and weights
 * still place things when the content fits: a centred pulse, a footer at the foot. Its pane on a
 * phone on its side (decision 0021), or a short window's whole screen. [padding] (the insets, then
 * any margins) goes inside the scroll so content runs under the bars.
 */
@Composable
fun ScrollColumn(
  modifier: Modifier = Modifier,
  padding: Modifier = Modifier,
  verticalArrangement: Arrangement.Vertical = Arrangement.Top,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  content: @Composable ColumnScope.() -> Unit,
) {
  BoxWithConstraints(modifier) {
    Column(
      Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).then(padding),
      verticalArrangement,
      horizontalAlignment,
      content,
    )
  }
}
