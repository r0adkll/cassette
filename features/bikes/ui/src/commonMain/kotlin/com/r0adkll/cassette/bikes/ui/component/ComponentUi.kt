package com.r0adkll.cassette.bikes.ui.component

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.ScreenLayout
import com.r0adkll.cassette.common.compose.components.FloatingTopBar
import com.r0adkll.cassette.common.compose.components.HistoryChart
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.ScrollColumn
import com.r0adkll.cassette.common.compose.components.SplitPanes
import com.r0adkll.cassette.common.compose.components.StatCell
import com.r0adkll.cassette.common.compose.components.TopBarButtonSize
import com.r0adkll.cassette.common.compose.components.TopBarNavigation
import com.r0adkll.cassette.common.compose.components.ValueStatusBlock
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.ComponentScreen
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope

@CircuitInject(ComponentScreen::class, AppScope::class)
@Composable
fun ComponentUi(state: ComponentUiState, modifier: Modifier = Modifier) {
  val model = state.loaded
  if (state.inSheet) {
    // The dashboard's sheet draws the header (close, previous, next) above this.
    Column(
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .safeDrawingPadding()
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (model != null) Detail(model, state.eventSink, gutter = SheetGutter)
    }
    return
  }
  if (LocalScreenLayout.current == ScreenLayout.Landscape) {
    LandscapeComponent(state, modifier)
    return
  }
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      Modifier.widthIn(max = 480.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .safeDrawingPadding()
        .padding(vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // The bike's name scrolls; Back floats over this row (iteration 14).
      Row(
        Modifier.padding(horizontal = 20.dp).height(TopBarButtonSize),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Spacer(Modifier.weight(1f))
        if (model != null) Kicker(model.bikeName)
      }
      if (model != null) Detail(model, state.eventSink)
    }
    BackBar(
      state.eventSink,
      Modifier.widthIn(max = 480.dp).safeDrawingPadding().padding(vertical = 24.dp),
    )
  }
}

/**
 * A phone on its side (decision 0021): the name and status block on the left, the stats and history
 * on the right.
 */
@Composable
private fun LandscapeComponent(state: ComponentUiState, modifier: Modifier = Modifier) {
  val model = state.loaded
  SplitPanes(
    start = {
      Box {
        ScrollColumn(
          Modifier.fillMaxSize(),
          padding = Modifier.safeDrawingPadding().padding(vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Row(
            Modifier.padding(horizontal = 20.dp).height(TopBarButtonSize),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Spacer(Modifier.weight(1f))
            if (model != null) Kicker(model.bikeName)
          }
          if (model != null) DetailHeader(model)
        }
        BackBar(state.eventSink, Modifier.safeDrawingPadding().padding(vertical = 16.dp))
      }
    },
    end = {
      Column(
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .safeDrawingPadding()
          .padding(vertical = 16.dp)
      ) {
        if (model != null) DetailBody(model, state.eventSink)
      }
    },
    modifier = modifier.fillMaxSize(),
    divider = true,
  )
}

@Composable
private fun BackBar(sink: (ComponentEvent) -> Unit, modifier: Modifier) {
  FloatingTopBar(modifier.padding(horizontal = 20.dp)) {
    TopBarNavigation(LucideIcons.ArrowLeft, "Back", onClick = { sink(ComponentEvent.Back) })
  }
}

/** The sheet's side padding; its header lines up with it. */
internal val SheetGutter = 24.dp

@Composable
private fun Detail(
  model: ComponentDetailModel,
  sink: (ComponentEvent) -> Unit,
  gutter: Dp = 20.dp,
) {
  DetailHeader(model, gutter)
  DetailBody(model, sink, gutter)
}

/** The component's name and its status block. */
@Composable
private fun DetailHeader(model: ComponentDetailModel, gutter: Dp = 20.dp) {
  Text(
    model.name,
    style = CassetteTheme.type.title,
    color = CassetteTheme.colors.text,
    modifier = Modifier.padding(horizontal = gutter),
  )
  ValueStatusBlock(
    value = model.value,
    headline = model.headline,
    source = model.source,
    level = model.level,
    tone = model.tone,
    horizontalPadding = gutter,
  )
}

/** The stats and the history chart. */
@Composable
private fun DetailBody(
  model: ComponentDetailModel,
  sink: (ComponentEvent) -> Unit,
  gutter: Dp = 20.dp,
) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    model.stats.chunked(3).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        row.forEach { (label, value) -> StatCell(label, value, Modifier.weight(1f)) }
      }
    }
    Panel {
      Row(verticalAlignment = Alignment.CenterVertically) {
        RangePicker(model.range, onPick = { sink(ComponentEvent.SelectRange(it)) })
      }
      if (model.chart.size >= 2) HistoryChart(model.chart)
      Text(
        model.chartCaption,
        style = type.body,
        color = if (model.chart.size >= 2) colors.text else colors.muted,
      )
    }
  }
}

@Composable
private fun RangePicker(selected: HistoryRange, onPick: (HistoryRange) -> Unit) {
  val colors = CassetteTheme.colors
  Row(
    Modifier.clip(RoundedCornerShape(16.dp)).background(colors.track).padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    HistoryRange.entries.forEach { range ->
      val on = range == selected
      Text(
        range.label,
        style = CassetteTheme.type.label.copy(fontSize = CassetteTheme.type.label.fontSize * 0.92f),
        color = if (on) colors.onInk else colors.muted,
        modifier =
          Modifier.clip(RoundedCornerShape(13.dp))
            .then(if (on) Modifier.background(colors.ink) else Modifier)
            .clickable { onPick(range) }
            .padding(horizontal = 12.dp, vertical = 5.dp),
      )
    }
  }
}
