package com.r0adkll.cassette.wear.ui.bike

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.r0adkll.cassette.bikes.summary.BikeStatus
import com.r0adkll.cassette.bikes.summary.ComponentRowModel
import com.r0adkll.cassette.common.compose.components.LevelBar
import com.r0adkll.cassette.common.compose.components.icon
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.wear.WatchRefresh
import com.r0adkll.cassette.wear.ui.BikeScreen
import com.r0adkll.cassette.wear.ui.WearTheme
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope

/**
 * The bike: the phone's status block, then one bar per component (iteration 5), and Refresh, which
 * asks the phone to scan.
 */
@CircuitInject(BikeScreen::class, AppScope::class)
@Composable
fun BikeUi(state: BikeUiState, modifier: Modifier = Modifier) {
  val listState = rememberTransformingLazyColumnState()
  when (state) {
    BikeUiState.Loading -> ScreenScaffold(listState, modifier) {}
    BikeUiState.Gone ->
      ScreenScaffold(listState, modifier) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding) {
          item { Message("Removed on the phone.") }
        }
      }
    is BikeUiState.Loaded ->
      ScreenScaffold(
        scrollState = listState,
        modifier = modifier,
        edgeButton = { RefreshButton(state.refresh) { state.eventSink(BikeEvent.Refresh) } },
      ) { padding ->
        TransformingLazyColumn(
          state = listState,
          // The status block runs edge to edge, like the phone's.
          contentPadding =
            PaddingValues(
              top = padding.calculateTopPadding(),
              bottom = padding.calculateBottomPadding(),
            ),
        ) {
          item { StatusBand(state.name, state.status) }
          items(state.components, key = { it.id }) { ComponentBar(it) }
          refreshMessage(state.refresh)?.let { message -> item { Message(message) } }
        }
      }
  }
}

@Composable
private fun StatusBand(name: String, status: BikeStatus) {
  val block = CassetteTheme.colors.block(status.tone)
  val type = WearTheme.type
  Column(
    Modifier.fillMaxWidth()
      .padding(bottom = 6.dp)
      .background(block.container)
      .let { if (block.outline != null) it.border(1.dp, block.outline!!) else it }
      .padding(top = 8.dp, bottom = 7.dp, start = 24.dp, end = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      name.uppercase(),
      style = type.kicker.copy(fontSize = type.kicker.fontSize * 0.92f),
      color = block.content,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        status.tone.icon,
        contentDescription = null,
        tint = block.content,
        modifier = Modifier.size(20.dp),
      )
      // One line, like the mockup: longer headlines ("Not seen in 20 days.") shrink to fit.
      BasicText(
        status.headline,
        style = type.headline.copy(color = block.content),
        maxLines = 1,
        autoSize =
          TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = type.headline.fontSize),
        modifier = Modifier.padding(start = 5.dp),
      )
    }
  }
}

@Composable
private fun ComponentBar(row: ComponentRowModel) {
  val colors = CassetteTheme.colors
  val type = WearTheme.type
  Row(
    Modifier.fillMaxWidth(0.68f).padding(vertical = 1.dp).semantics(mergeDescendants = true) {
      contentDescription = "${row.name} ${row.value}"
    },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(row.code, style = type.code, color = colors.muted, modifier = Modifier.width(24.dp))
    LevelBar(row.level, row.tone, Modifier.weight(1f), height = 5.dp)
    Text(
      row.value.replace(" V", "V"),
      style = type.value,
      color = colors.text,
      textAlign = TextAlign.End,
      maxLines = 1,
      modifier = Modifier.width(46.dp),
    )
  }
}

@Composable
private fun RefreshButton(refresh: WatchRefresh, onClick: () -> Unit) {
  val colors = CassetteTheme.colors
  EdgeButton(
    onClick = onClick,
    enabled = refresh == WatchRefresh.Idle || refresh is WatchRefresh.Scanning,
    colors =
      ButtonDefaults.buttonColors(
        containerColor = colors.track,
        contentColor = colors.text,
        iconColor = colors.text,
      ),
  ) {
    when (refresh) {
      is WatchRefresh.Scanning -> {
        val seconds = refresh.secondsLeft.toString().padStart(2, '0')
        Text("0:$seconds", style = WearTheme.type.button)
      }
      WatchRefresh.Asking -> Text("ASKING…", style = WearTheme.type.button)
      else -> {
        Icon(LucideIcons.RefreshCw, contentDescription = null, modifier = Modifier.size(16.dp))
        Text("REFRESH", style = WearTheme.type.button, modifier = Modifier.padding(start = 6.dp))
      }
    }
  }
}

private fun refreshMessage(refresh: WatchRefresh): String? =
  when (refresh) {
    WatchRefresh.Idle -> null
    WatchRefresh.Asking -> "Asking the phone…"
    is WatchRefresh.Scanning -> "Wake the bike: the phone is listening."
    WatchRefresh.PhoneNotReady -> "Turn on Bluetooth on the phone."
    WatchRefresh.NoPhone -> "Can't reach the phone."
  }

@Composable
private fun Message(text: String) {
  Text(
    text,
    style = WearTheme.type.body,
    color = CassetteTheme.colors.muted,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth(0.8f).padding(top = 6.dp),
  )
}
