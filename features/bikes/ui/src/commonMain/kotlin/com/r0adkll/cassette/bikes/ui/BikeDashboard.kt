package com.r0adkll.cassette.bikes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.r0adkll.cassette.bikes.summary.ComponentRowModel
import com.r0adkll.cassette.bikes.summary.weakest
import com.r0adkll.cassette.bikes.ui.component.SheetGutter
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
import com.r0adkll.cassette.common.screens.ComponentScreen
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.foundation.NavEvent

/**
 * The bike screen on an open foldable or a tablet (decision 0014): the status block with Refresh
 * inside it, then the components as tiles, the one to charge next on a wide tile. With [rail]
 * (large windows) the bikes are listed on the left; otherwise they are chips in the top bar.
 *
 * Tapping a tile opens the component in a sheet over the dashboard, which never re-lays out.
 * [sheetContent] draws the component; renders pass their own, since the app's runs its presenter.
 */
@Composable
internal fun BikeDashboard(
  state: BikeUiState.Loaded,
  rail: Boolean,
  modifier: Modifier = Modifier,
  sheetContent: @Composable (componentId: Long) -> Unit = { id ->
    ComponentInSheet(id, state.eventSink)
  },
) {
  Box(modifier.fillMaxSize()) {
    Dashboard(state, rail)
    ComponentSheet(state, rail, sheetContent)
  }
}

@Composable
private fun Dashboard(state: BikeUiState.Loaded, rail: Boolean) {
  val update by LocalAppUpdates.current.status.collectAsState()
  val updateWaiting = update is UpdateStatus.Available
  val gutter = if (rail) 28.dp else 24.dp
  val sink = state.eventSink
  Row(Modifier.fillMaxSize()) {
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
              selected = state.sheet == component.id,
              onClick = { state.eventSink(BikeEvent.ShowComponent(component.id)) },
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
  selected: Boolean,
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
    selected = selected,
    onClick = onClick,
  )
}

/** Material's emphasized curves: the sheet decelerates in and accelerates out. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private const val SheetInMillis = 340
private const val SheetOutMillis = 220

private val SheetShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)

/**
 * The component sheet, sliding in from the right edge. On the open foldable it covers about three
 * quarters of the width and dims the dashboard strip left showing, which closes it when tapped.
 * With the rail it is 480 dp wide and not dimmed, so tapping another tile switches it. System back
 * closes it either way.
 */
@Composable
private fun BoxScope.ComponentSheet(
  state: BikeUiState.Loaded,
  rail: Boolean,
  content: @Composable (componentId: Long) -> Unit,
) {
  val colors = CassetteTheme.colors
  val sink = state.eventSink
  val open = state.sheet
  // Keeps the last component drawn while the sheet slides out.
  val shown = remember { Held<Long?>(null) }
  if (open != null) shown.value = open

  NavigationBackHandler(
    rememberNavigationEventState(NavigationEventInfo.None),
    isBackEnabled = open != null,
    onBackCompleted = { sink(BikeEvent.CloseSheet) },
  )

  // The app pads every screen inside the system bars; the scrim and the sheet run out under them,
  // and the sheet's content comes back inside. The screen's padding has consumed the insets, so the
  // sheet pads by the measured sizes rather than with windowInsetsPadding (which would add
  // nothing).
  val bars = WindowInsets.safeDrawing
  val density = LocalDensity.current
  val direction = LocalLayoutDirection.current
  val left = bars.getLeft(density, direction)
  val top = bars.getTop(density)
  val right = bars.getRight(density, direction)
  val bottom = bars.getBottom(density)
  val barPadding =
    with(density) {
      PaddingValues(
        top = top.toDp(),
        bottom = bottom.toDp(),
        // The sheet sits at the end edge, so only that side's bar reaches it.
        end = (if (direction == LayoutDirection.Ltr) right else left).toDp(),
      )
    }
  Box(Modifier.matchParentSize().expandBy(left, top, right, bottom)) {
    if (!rail) {
      AnimatedVisibility(
        open != null,
        enter = fadeIn(tween(SheetInMillis)),
        exit = fadeOut(tween(SheetOutMillis)),
      ) {
        Box(
          Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = if (colors.isDark) 0.45f else 0.22f))
            .clickable(
              interactionSource = null,
              indication = null,
              onClickLabel = "Close component",
              onClick = { sink(BikeEvent.CloseSheet) },
            )
        )
      }
    }

    AnimatedVisibility(
      open != null,
      modifier =
        Modifier.align(Alignment.CenterEnd)
          .fillMaxHeight()
          .then(if (rail) Modifier.width(480.dp) else Modifier.fillMaxWidth(0.76f)),
      enter = slideInHorizontally(tween(SheetInMillis, easing = EmphasizedDecelerate)) { it },
      exit = slideOutHorizontally(tween(SheetOutMillis, easing = EmphasizedAccelerate)) { it },
    ) {
      val id = shown.value ?: return@AnimatedVisibility
      // No border: it would frame the status block, which runs to the sheet's edges. The shadow
      // and the panel colour set the sheet apart.
      Column(
        Modifier.fillMaxSize()
          .shadow(16.dp, SheetShape)
          .clip(SheetShape)
          .background(colors.panel)
          .padding(barPadding)
          .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        SheetHeader(state, id, Modifier.padding(horizontal = SheetGutter))
        key(id) { Box(Modifier.weight(1f)) { content(id) } }
      }
    }
  }
}

/** Close, which component of how many, and previous and next to step through them. */
@Composable
private fun SheetHeader(state: BikeUiState.Loaded, id: Long, modifier: Modifier) {
  val sink = state.eventSink
  val components = state.components
  val index = components.indexOfFirst { it.id == id }.coerceAtLeast(0)
  val count = components.size
  Row(
    modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    IconCircleButton(LucideIcons.X, "Close", onClick = { sink(BikeEvent.CloseSheet) })
    Kicker(
      "${components.getOrNull(index)?.code.orEmpty()} · ${index + 1} of $count",
      Modifier.weight(1f).padding(start = 4.dp),
    )
    if (count > 1) {
      IconCircleButton(
        LucideIcons.ChevronLeft,
        "Previous component",
        onClick = { sink(BikeEvent.ShowComponent(components[(index + count - 1) % count].id)) },
      )
      IconCircleButton(
        LucideIcons.ChevronRight,
        "Next component",
        onClick = { sink(BikeEvent.ShowComponent(components[(index + 1) % count].id)) },
      )
    }
  }
}

/** The app's sheet body: the component screen itself, whose back closes the sheet. */
@Composable
private fun ComponentInSheet(id: Long, sink: (BikeEvent) -> Unit) {
  CircuitContent(
    ComponentScreen(id, inSheet = true),
    onNavEvent = { event ->
      if (event is NavEvent.Pop) sink(BikeEvent.CloseSheet)
      else sink(BikeEvent.SheetNavigation(event))
    },
  )
}

/** Lays the content out larger than its slot by these pixels on each side, centred on it. */
private fun Modifier.expandBy(left: Int, top: Int, right: Int, bottom: Int): Modifier =
  layout { measurable, constraints ->
    val placeable =
      measurable.measure(
        Constraints.fixed(
          constraints.maxWidth + left + right,
          constraints.maxHeight + top + bottom,
        )
      )
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-left, -top) }
  }

/** A value kept across recompositions without causing them. */
private class Held<T>(var value: T)

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
