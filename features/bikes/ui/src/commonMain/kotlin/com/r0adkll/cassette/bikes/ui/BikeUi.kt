package com.r0adkll.cassette.bikes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.LocalAppUpdates
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.ScreenLayout
import com.r0adkll.cassette.common.compose.UpdateStatus
import com.r0adkll.cassette.common.compose.components.BarAction
import com.r0adkll.cassette.common.compose.components.BarCount
import com.r0adkll.cassette.common.compose.components.BikesPill
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.ComponentRow
import com.r0adkll.cassette.common.compose.components.FloatingTopBar
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.LocalTopBarShown
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.SplitPanes
import com.r0adkll.cassette.common.compose.components.StatusBlock
import com.r0adkll.cassette.common.compose.components.StatusBlockMeta
import com.r0adkll.cassette.common.compose.components.StatusBlockText
import com.r0adkll.cassette.common.compose.components.TopBarActions
import com.r0adkll.cassette.common.compose.components.TopBarButtonSize
import com.r0adkll.cassette.common.compose.components.hasSystemActionBar
import com.r0adkll.cassette.common.compose.currentTabletopHinge
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.isShortMediumWindow
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.settings.api.Tip
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope

/** The phone layout's width (decision 0014). */
private val MaxContentWidth = 480.dp

@CircuitInject(BikeScreen::class, AppScope::class)
@Composable
fun BikeUi(state: BikeUiState, modifier: Modifier = Modifier) {
  val layout = LocalScreenLayout.current
  // Too narrow to split and too short for the phone's column to show more than the status: the
  // dashboard keeps Refresh in its status band, and its components open in its sheet (0024).
  val shortMedium = isShortMediumWindow()
  // Folding the phone with a sheet open carries the component over as its own screen.
  val openSheet = (state as? BikeUiState.Loaded)?.sheet
  LaunchedEffect(layout, shortMedium, openSheet) {
    if (layout.opensComponents && !shortMedium && openSheet != null) {
      state.eventSink(BikeEvent.CloseSheet)
      state.eventSink(BikeEvent.OpenComponent(openSheet))
    }
  }
  // The phone's bikes sheet (decision 0026). Wider windows list the bikes themselves, so it closes
  // as the window grows into one, and when a bike removed elsewhere leaves just one.
  var bikesOpen by rememberSaveable { mutableStateOf(false) }
  val listsBikes =
    (layout != ScreenLayout.Column && layout != ScreenLayout.Landscape) || shortMedium
  val switches = state is BikeUiState.Loaded && state.bikes.size > 1
  LaunchedEffect(listsBikes, switches) { if (listsBikes || !switches) bikesOpen = false }
  val bikes = BikesControl(bikesOpen, onOpenChange = { bikesOpen = it })
  // Half open with the hinge horizontal: readings above the fold, the refresh below.
  val hinge = currentTabletopHinge()
  if (state is BikeUiState.Loaded && hinge != null) {
    TabletopBike(state, hinge, modifier)
    return
  }
  if (state is BikeUiState.Loaded && layout == ScreenLayout.Landscape) {
    LandscapeBike(state, bikes, modifier)
    return
  }
  if (state is BikeUiState.Empty && layout != ScreenLayout.Column) {
    WideEmptyBike(state.eventSink, modifier)
    return
  }
  if (state is BikeUiState.Loaded && (layout != ScreenLayout.Column || shortMedium)) {
    BikeDashboard(state, rail = layout == ScreenLayout.DashboardWithRail, modifier)
    return
  }
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      Modifier.widthIn(max = MaxContentWidth)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .safeDrawingPadding()
        .padding(vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Wordmark(state)
      when (state) {
        is BikeUiState.Loading -> Unit
        is BikeUiState.Empty -> EmptyBike(state.eventSink)
        is BikeUiState.Loaded -> LoadedBike(state)
      }
    }
    TopBar(
      state,
      bikes,
      Modifier.widthIn(max = MaxContentWidth).safeDrawingPadding().padding(vertical = 24.dp),
    )
    if (state is BikeUiState.Loaded) BikesSheet(state, bikes.open, onClose = bikes::close)
  }
}

/** Whether the bikes sheet is open (decision 0026), and the way to open or close it. */
private class BikesControl(val open: Boolean, private val onOpenChange: (Boolean) -> Unit) {
  fun show() = onOpenChange(true)

  fun close() = onOpenChange(false)
}

/**
 * The wordmark, which scrolls while the top bar's buttons float over its row (iteration 14). The
 * bikes pill takes its place when there's more than one bike (decision 0026).
 */
@Composable
private fun Wordmark(state: BikeUiState) {
  Row(
    Modifier.padding(horizontal = 20.dp).height(TopBarButtonSize),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (!showsBikesPill(state)) Kicker("Cassette")
  }
}

/** Two bikes or more, and no system bar to put the bikes button in (the Duo's has it). */
@Composable
private fun showsBikesPill(state: BikeUiState): Boolean =
  state is BikeUiState.Loaded && state.bikes.size > 1 && !hasSystemActionBar()

/**
 * The phone's top bar: the bikes pill when there are several, then Add bike and Settings. On the
 * iPhone Duo they all go in the vertical bar, the bikes first with a count of those that need
 * charging. It hides while the bikes sheet is open, which has its own.
 */
@Composable
private fun TopBar(state: BikeUiState, bikes: BikesControl, modifier: Modifier) {
  val update by LocalAppUpdates.current.status.collectAsState()
  val updateWaiting = update is UpdateStatus.Available
  val loaded = state as? BikeUiState.Loaded
  val choices = loaded?.bikes.orEmpty()
  val attention = loaded?.let { choices.attention(it.bikeId) }
  val description = bikesDescription(choices.size, attention)
  val pill = showsBikesPill(state)
  CompositionLocalProvider(LocalTopBarShown provides (LocalTopBarShown.current && !bikes.open)) {
    FloatingTopBar(modifier.padding(horizontal = 20.dp)) {
      // The pill takes what the buttons leave, and shortens its label to fit it.
      if (pill)
        Box(Modifier.weight(1f)) {
          BikesPill(
            choices.size,
            attention?.tone,
            attention?.names?.size ?: 0,
            description,
            onClick = bikes::show,
          )
        }
      else Spacer(Modifier.weight(1f))
      TopBarActions(
        listOfNotNull(
          BarAction(
              LucideIcons.Bike,
              description,
              count = attention?.let { BarCount(it.names.size, it.tone) },
              onClick = bikes::show,
            )
            .takeIf { choices.size > 1 && !pill },
          // The empty state has its own Add bike button.
          BarAction(LucideIcons.BikePlus, "Add bike") { state.eventSink(BikeEvent.AddBike) }
            .takeIf { loaded != null },
          BarAction(
            LucideIcons.Settings,
            if (updateWaiting) "Settings, app update available" else "Settings",
            badge = updateWaiting,
            onClick = { state.eventSink(BikeEvent.OpenSettings) },
          ),
        )
      )
    }
  }
}

/** Where tapping a component opens its own screen rather than the dashboard's sheet. */
private val ScreenLayout.opensComponents: Boolean
  get() = this == ScreenLayout.Column || this == ScreenLayout.Landscape

/**
 * A phone on its side (decision 0021, direction A): the bike's name, status and Refresh on the
 * left, its components (and a tip) scrolling on the right.
 */
@Composable
private fun LandscapeBike(
  state: BikeUiState.Loaded,
  bikes: BikesControl,
  modifier: Modifier = Modifier,
) {
  Box(modifier.fillMaxSize()) {
    LandscapePanes(state, bikes)
    BikesSheet(state, bikes.open, onClose = bikes::close)
  }
}

@Composable
private fun LandscapePanes(state: BikeUiState.Loaded, bikes: BikesControl) {
  SplitPanes(
    start = {
      Box {
        // Refresh stays at the foot (decision 0016's fixed footer); a long status scrolls above it.
        Column(
          Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Wordmark(state)
            BikeName(state)
            BikeStatus(state)
          }
          Box(Modifier.padding(horizontal = 20.dp)) {
            RefreshButton(state.refresh, onClick = { state.eventSink(BikeEvent.Refresh) })
          }
        }
        TopBar(state, bikes, Modifier.safeDrawingPadding().padding(vertical = 16.dp))
      }
    },
    end = {
      Column(
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .safeDrawingPadding()
          .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        // Lined up with the wordmark across the panes.
        Box(Modifier.height(TopBarButtonSize), contentAlignment = Alignment.CenterStart) {
          Kicker("Components · ${state.components.size}")
        }
        Components(state)
        state.tip?.let { tip ->
          Spacer(Modifier.height(10.dp))
          TipPanel(
            tip,
            onAccept = { state.eventSink(BikeEvent.AcceptTip(tip)) },
            onDismiss = { state.eventSink(BikeEvent.DismissTip(tip)) },
          )
        }
      }
    },
    modifier = Modifier.fillMaxSize(),
    divider = true,
  )
}

@Composable
private fun LoadedBike(state: BikeUiState.Loaded) {
  BikeName(state)
  BikeStatus(state)
  Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Components(state)
    Spacer(Modifier.height(10.dp))
    RefreshButton(state.refresh, onClick = { state.eventSink(BikeEvent.Refresh) })
    state.tip?.let { tip ->
      Spacer(Modifier.height(10.dp))
      TipPanel(
        tip,
        onAccept = { state.eventSink(BikeEvent.AcceptTip(tip)) },
        onDismiss = { state.eventSink(BikeEvent.DismissTip(tip)) },
      )
    }
  }
}

/** The bike's name; the bikes pill in the top bar switches it (decision 0026). */
@Composable
private fun BikeName(state: BikeUiState.Loaded) {
  Text(
    state.name,
    style = CassetteTheme.type.display.copy(fontSize = CassetteTheme.type.display.fontSize * 1.07f),
    color = CassetteTheme.colors.text,
    modifier = Modifier.padding(horizontal = 20.dp),
  )
}

@Composable
private fun BikeStatus(state: BikeUiState.Loaded) {
  // Screen readers get the plain title (decision 0015).
  StatusBlock(state.title, state.status.tone, titleDescription = state.status.headline) {
    StatusBlockText(state.status.summary)
    StatusBlockMeta(state.status.meta)
  }
}

@Composable
private fun Components(state: BikeUiState.Loaded) {
  state.components.forEach { row ->
    ComponentRow(
      code = row.code,
      name = row.name,
      source = row.source,
      value = row.value,
      level = row.level,
      tone = row.tone,
      onClick = { state.eventSink(BikeEvent.OpenComponent(row.id)) },
    )
  }
}

/** One quiet suggestion: what it is, why, and a way to say no. */
@Composable
internal fun TipPanel(tip: Tip, onAccept: () -> Unit, onDismiss: () -> Unit) {
  val colors = CassetteTheme.colors
  val (icon, title, body, action) =
    when (tip) {
      Tip.Widget ->
        TipCopy(
          LucideIcons.LayoutGrid,
          "Add a widget",
          "See your batteries on the home screen without opening the app.",
          "Add widget",
        )
      Tip.QuickSettingsTile ->
        TipCopy(
          LucideIcons.PanelTop,
          "Add the Quick Settings tile",
          "Check the weakest battery from the shade, and tap it to refresh.",
          "Add tile",
        )
    }
  Panel {
    Row(
      Modifier.padding(top = 4.dp),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        Modifier.size(40.dp).clip(CircleShape).background(colors.track),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, null, tint = colors.text, modifier = Modifier.size(20.dp))
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Kicker("Tip")
        Text(title, style = CassetteTheme.type.itemTitle, color = colors.text)
        Text(body, style = CassetteTheme.type.bodySmall, color = colors.muted)
      }
      Icon(
        LucideIcons.X,
        contentDescription = "Dismiss",
        tint = colors.muted,
        modifier =
          Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(7.dp),
      )
    }
    CassetteButton(
      action,
      onClick = onAccept,
      primary = false,
      modifier = Modifier.padding(bottom = 4.dp),
    )
  }
}

private data class TipCopy(
  val icon: ImageVector,
  val title: String,
  val body: String,
  val action: String,
)

@Composable
private fun RefreshButton(refresh: RefreshButton, onClick: () -> Unit) {
  when (refresh) {
    RefreshButton.Idle,
    is RefreshButton.Done ->
      CassetteButton("Refresh now", onClick = onClick, trailing = "60 s scan")
    is RefreshButton.Scanning -> {
      val seconds = refresh.secondsLeft
      CassetteButton(
        "Wake the bike",
        onClick = {},
        primary = false,
        icon = LucideIcons.BluetoothSearching,
        trailing = "0:${seconds.toString().padStart(2, '0')}",
      )
    }
    RefreshButton.Unavailable ->
      CassetteButton(
        "Turn on Bluetooth",
        onClick = onClick,
        primary = false,
        icon = LucideIcons.Bluetooth,
      )
  }
}

@Composable
private fun EmptyBike(eventSink: (BikeEvent) -> Unit) {
  val colors = CassetteTheme.colors
  Text(
    "No bike yet.",
    style = CassetteTheme.type.display,
    color = colors.text,
    modifier = Modifier.padding(horizontal = 20.dp),
  )
  Text(
    "Add your AXS bike: wake it, and Cassette finds the hub and every component.",
    style = CassetteTheme.type.body,
    color = colors.muted,
    modifier = Modifier.padding(horizontal = 20.dp),
  )
  Column(
    Modifier.padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    CassetteButton("Add bike", onClick = { eventSink(BikeEvent.AddBike) })
    Text(
      "Developers: Settings → Diagnostics can load the validation bike's readings.",
      style = CassetteTheme.type.bodySmall,
      color = colors.muted,
    )
  }
}
