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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.components.HistoryChart
import com.r0adkll.cassette.common.compose.components.IconCircleButton
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.StatCell
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
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      Modifier.widthIn(max = 480.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(
          LucideIcons.ArrowLeft,
          "Back",
          onClick = { state.eventSink(ComponentEvent.Back) },
        )
        Spacer(Modifier.weight(1f))
        if (model != null) Kicker(model.bikeName)
      }
      if (model != null) Detail(model, state.eventSink)
    }
  }
}

@Composable
private fun Detail(model: ComponentDetailModel, sink: (ComponentEvent) -> Unit) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  Text(
    model.name,
    style = type.title,
    color = colors.text,
    modifier = Modifier.padding(horizontal = 20.dp),
  )
  ValueStatusBlock(
    value = model.value,
    headline = model.headline,
    source = model.source,
    level = model.level,
    tone = model.tone,
  )
  Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
