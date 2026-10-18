package com.r0adkll.cassette.bikes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.bikes.summary.ComponentRowModel
import com.r0adkll.cassette.bikes.summary.weakest
import com.r0adkll.cassette.common.compose.LocalAppUpdates
import com.r0adkll.cassette.common.compose.UpdateStatus
import com.r0adkll.cassette.common.compose.components.BikeChip
import com.r0adkll.cassette.common.compose.components.BikeRailItem
import com.r0adkll.cassette.common.compose.components.ComponentTile
import com.r0adkll.cassette.common.compose.components.IconCircleButton
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.RailAddButton
import com.r0adkll.cassette.common.compose.components.RailLink
import com.r0adkll.cassette.common.compose.components.StatusBlockButton
import com.r0adkll.cassette.common.compose.components.StatusBlockMeta
import com.r0adkll.cassette.common.compose.components.StatusBlockText
import com.r0adkll.cassette.common.compose.components.WideStatusBlock
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme

/**
 * The bike screen on an open foldable or a tablet (decision 0014): the status block with Refresh
 * inside it, then the components as tiles, the one to charge next on a wide tile. With [rail]
 * (large windows) the bikes are listed on the left; otherwise they are chips in the top bar.
 */
@Composable
internal fun BikeDashboard(
  state: BikeUiState.Loaded,
  rail: Boolean,
  modifier: Modifier = Modifier,
) {
  val update by LocalAppUpdates.current.status.collectAsState()
  val updateWaiting = update is UpdateStatus.Available
  val gutter = if (rail) 28.dp else 24.dp
  val sink = state.eventSink
  Row(modifier.fillMaxSize()) {
    if (rail) {
      BikeRail(state, updateWaiting, Modifier.width(240.dp).fillMaxHeight())
      Box(Modifier.width(1.dp).fillMaxHeight().background(CassetteTheme.colors.line))
    }
    Column(
      Modifier.weight(1f)
        .fillMaxHeight()
        .verticalScroll(rememberScrollState())
        .padding(vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (rail) {
        Text(
          state.name,
          style = CassetteTheme.type.display,
          color = CassetteTheme.colors.text,
          modifier = Modifier.padding(horizontal = gutter),
        )
      } else {
        ChipBar(state, updateWaiting, Modifier.padding(horizontal = gutter))
      }
      WideStatusBlock(
        state.status.headline,
        state.status.tone,
        kicker = state.name.takeUnless { rail },
        horizontalPadding = gutter,
        asideWidth = if (rail) 260.dp else 220.dp,
        content = { StatusBlockText(state.status.summary) },
        aside = {
          StatusBlockMeta(state.status.meta)
          BlockRefresh(state.refresh, onClick = { sink(BikeEvent.Refresh) })
        },
      )
      ComponentGrid(
        state,
        columns = if (rail) 3 else 2,
        gap = if (rail) 12.dp else 10.dp,
        modifier = Modifier.padding(horizontal = gutter),
      )
      state.tip?.let { tip ->
        Box(Modifier.padding(horizontal = gutter)) {
          TipPanel(
            tip,
            onAccept = { sink(BikeEvent.AcceptTip(tip)) },
            onDismiss = { sink(BikeEvent.DismissTip(tip)) },
          )
        }
      }
    }
  }
}

/** The bikes, then Add bike, then Settings at the foot. */
@Composable
private fun BikeRail(state: BikeUiState.Loaded, updateWaiting: Boolean, modifier: Modifier) {
  val sink = state.eventSink
  Column(
    modifier.padding(horizontal = 14.dp, vertical = 24.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
      Modifier.height(44.dp).padding(horizontal = 8.dp),
      contentAlignment = Alignment.CenterStart,
    ) {
      Kicker("Cassette")
    }
    Kicker("Bikes", Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp))
    Column(
      Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      state.choices().forEach { bike ->
        BikeRailItem(
          bike.name,
          bike.tone,
          bike.seen,
          selected = bike.id == state.bikeId,
          onClick = { sink(BikeEvent.SelectBike(bike.id)) },
        )
      }
    }
    RailAddButton("Add bike", onClick = { sink(BikeEvent.AddBike) }, Modifier.padding(top = 6.dp))
    Spacer(Modifier.weight(1f))
    RailLink(
      LucideIcons.Settings,
      if (updateWaiting) "Settings, app update available" else "Settings",
      onClick = { sink(BikeEvent.OpenSettings) },
      badge = updateWaiting,
    )
  }
}

/** The open foldable's top bar: the wordmark, a chip per bike, Add bike and Settings. */
@Composable
private fun ChipBar(state: BikeUiState.Loaded, updateWaiting: Boolean, modifier: Modifier) {
  val sink = state.eventSink
  Row(
    modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Kicker("Cassette")
    Row(
      Modifier.weight(1f).horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      state.choices().forEach { bike ->
        BikeChip(
          bike.name,
          bike.tone,
          selected = bike.id == state.bikeId,
          onClick = { sink(BikeEvent.SelectBike(bike.id)) },
        )
      }
    }
    IconCircleButton(LucideIcons.BikePlus, "Add bike", onClick = { sink(BikeEvent.AddBike) })
    IconCircleButton(
      LucideIcons.Settings,
      if (updateWaiting) "Settings, app update available" else "Settings",
      onClick = { sink(BikeEvent.OpenSettings) },
      badge = updateWaiting,
    )
  }
}

/**
 * The tiles: the component to charge next first, on a tile two columns wide with a taller trend,
 * then the rest in [columns] columns.
 */
@Composable
private fun ComponentGrid(
  state: BikeUiState.Loaded,
  columns: Int,
  gap: Dp,
  modifier: Modifier = Modifier,
) {
  val next = state.components.weakest()
  val ordered = listOfNotNull(next) + state.components.filter { it != next }
  BoxWithConstraints(modifier) {
    val cell = (maxWidth - gap * (columns - 1)) / columns
    val wide = cell * 2 + gap
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
      // The wide tile fills two columns, so the first row holds one fewer.
      val firstRow = ordered.take(columns - 1)
      val rest = ordered.drop(columns - 1).chunked(columns)
      (listOf(firstRow) + rest).forEachIndexed { rowIndex, row ->
        Row(
          Modifier.height(IntrinsicSize.Min),
          horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
          row.forEachIndexed { i, component ->
            val isWide = rowIndex == 0 && i == 0
            Tile(
              component,
              trend = state.trends[component.id].orEmpty(),
              nextToCharge = isWide && component == next,
              trendHeight = if (isWide) 64.dp else 44.dp,
              modifier = Modifier.width(if (isWide) wide else cell).fillMaxHeight(),
              onClick = { state.eventSink(BikeEvent.OpenComponent(component.id)) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun Tile(
  component: ComponentRowModel,
  trend: List<Float>,
  nextToCharge: Boolean,
  trendHeight: Dp,
  modifier: Modifier,
  onClick: () -> Unit,
) {
  ComponentTile(
    code = component.code,
    name = component.name,
    source = if (nextToCharge) "Next to charge · ${component.source}" else component.source,
    value = component.value,
    level = component.level,
    tone = component.tone,
    trend = trend,
    trendHeight = trendHeight,
    modifier = modifier,
    onClick = onClick,
  )
}

/** Refresh inside the status block: the same three states as the phone's button. */
@Composable
private fun BlockRefresh(refresh: RefreshButton, onClick: () -> Unit) {
  when (refresh) {
    RefreshButton.Idle -> StatusBlockButton("Refresh now", onClick, trailing = "60 s")
    is RefreshButton.Scanning ->
      StatusBlockButton(
        "Wake the bike",
        onClick = {},
        icon = LucideIcons.BluetoothSearching,
        trailing = "0:${refresh.secondsLeft.toString().padStart(2, '0')}",
      )
    RefreshButton.Unavailable ->
      StatusBlockButton("Turn on Bluetooth", onClick, icon = LucideIcons.Bluetooth)
  }
}

/** The bikes to switch between; just this one until the list has loaded. */
private fun BikeUiState.Loaded.choices(): List<BikeChoice> = bikes.ifEmpty {
  listOf(BikeChoice(bikeId, name, status.tone, status.meta.removePrefix("Seen ")))
}
