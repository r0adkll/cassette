package com.r0adkll.cassette.bikes.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.ScreenLayout
import com.r0adkll.cassette.common.compose.components.FloatingTopBar
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.SplitPanes
import com.r0adkll.cassette.common.compose.components.TopBarButtonSize
import com.r0adkll.cassette.common.compose.components.TopBarNavigation
import com.r0adkll.cassette.common.compose.theme.CassetteTheme

/**
 * A widget's settings question: the kicker, title and why on the left, the choices on the right;
 * one column on a phone. [navigation] (close, or back to the previous question) floats over the
 * title.
 */
@Composable
internal fun PickerLayout(
  kicker: String,
  title: String,
  body: String,
  navigation: Pair<ImageVector, String>,
  onNavigation: () -> Unit,
  modifier: Modifier = Modifier,
  options: @Composable ColumnScope.() -> Unit,
) {
  val intro: @Composable ColumnScope.() -> Unit = {
    // The navigation button floats over this row (iteration 14).
    Row(Modifier.height(TopBarButtonSize), verticalAlignment = Alignment.CenterVertically) {
      Spacer(Modifier.weight(1f))
      Kicker(kicker)
    }
    Text(title, style = CassetteTheme.type.display, color = CassetteTheme.colors.text)
    Text(body, style = CassetteTheme.type.body, color = CassetteTheme.colors.muted)
  }
  val bar: @Composable () -> Unit = {
    FloatingTopBar(
      Modifier.widthIn(max = 480.dp)
        .safeDrawingPadding()
        .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
      TopBarNavigation(navigation.first, navigation.second, onClick = onNavigation)
    }
  }
  val layout = LocalScreenLayout.current
  if (layout == ScreenLayout.Column) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      PaneColumn {
        intro()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = options)
      }
      bar()
    }
  } else {
    SplitPanes(
      start = {
        // Each pane keeps the phone's width against the split, so a tablet reads as one page.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
          PaneColumn(content = intro)
          bar()
        }
      },
      end = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
          PaneColumn(spacing = 10.dp, content = options)
        }
      },
      modifier = modifier.fillMaxSize(),
      divider = layout == ScreenLayout.Landscape,
    )
  }
}

@Composable
private fun PaneColumn(
  spacing: Dp = 16.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    Modifier.widthIn(max = 480.dp)
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .safeDrawingPadding()
      .padding(horizontal = 20.dp, vertical = 24.dp),
    verticalArrangement = Arrangement.spacedBy(spacing),
    content = content,
  )
}
