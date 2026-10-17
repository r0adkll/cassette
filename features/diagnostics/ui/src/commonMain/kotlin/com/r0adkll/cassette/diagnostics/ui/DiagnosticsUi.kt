package com.r0adkll.cassette.diagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.components.ChoiceChips
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.SettingsRow
import com.r0adkll.cassette.common.compose.components.SettingsSection
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.DiagnosticsScreen
import com.r0adkll.cassette.data.ble.ScanProfile
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope

@CircuitInject(DiagnosticsScreen::class, AppScope::class)
@Composable
fun DiagnosticsUi(state: DiagnosticsUiState, modifier: Modifier = Modifier) {
  val sink = state.eventSink
  val colors = CassetteTheme.colors
  DiagnosticsScaffold("Diagnostics.", onBack = { sink(DiagnosticsEvent.Back) }, modifier) {
    val healthy = state.bluetoothReady && state.health.armResult?.startsWith("Armed") == true
    Panel {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Kicker("Capture now", Modifier.weight(1f))
        Box(
          Modifier.size(7.dp).clip(CircleShape).background(if (healthy) colors.good else colors.low)
        )
        Text(
          if (healthy) "  HEALTHY" else "  CHECK",
          style = CassetteTheme.type.status,
          color = if (healthy) colors.good else colors.low,
        )
      }
      MonoLine("mode", "scan (PendingIntent)")
      MonoLine("background", state.health.armResult ?: "never armed")
      MonoLine("armed", state.health.armedAt?.let { agoShort(state.now - it) } ?: "—")
      MonoLine(
        "last wake",
        state.health.lastDeliveryAt?.let { agoShort(state.now - it) } ?: "none yet",
      )
      MonoLine("wakes", state.health.deliveries.toString())
      MonoLine("hub read", state.bike?.bike?.hubReadAt?.let { agoShort(state.now - it) } ?: "—")
      MonoLine("bluetooth", if (state.bluetoothReady) "ready" else "off or not allowed")
    }
    SettingsSection("Tools") {
      SettingsRow(
        "Live adverts",
        subtitle = "Every AXS advert heard, with raw bytes",
        icon = LucideIcons.Radio,
        onClick = { sink(DiagnosticsEvent.OpenLiveAdverts) },
      )
      SettingsRow(
        "Hub tables",
        subtitle =
          state.bike?.let { "Battery and firmware tables of ${it.bike.name}'s hub" }
            ?: "Add a bike first",
        icon = LucideIcons.Cpu,
        onClick = if (state.bike != null) ({ sink(DiagnosticsEvent.OpenHubTables) }) else null,
        divider = false,
      )
    }
    SettingsSection("Scan profile") {
      Text(
        "How the background scan listens; ride tests compare them (decision 0009).",
        style = CassetteTheme.type.bodySmall,
        color = colors.muted,
      )
      ChoiceChips(
        options = ScanProfile.entries,
        selected = state.profile,
        label = { it.label },
        onSelect = { sink(DiagnosticsEvent.SetProfile(it)) },
      )
    }
    SettingsSection("Developer") {
      SettingsRow(
        "Load sample bike",
        subtitle = "The validation bike's readings from 16 Oct",
        icon = LucideIcons.Bike,
        onClick = { sink(DiagnosticsEvent.LoadSampleBike) },
      )
      SettingsRow(
        if (state.confirmRemove) "Tap again to remove" else "Remove bike",
        subtitle = state.bike?.let { "${it.bike.name}, with its history" } ?: "No bike",
        icon = LucideIcons.X,
        onClick = if (state.bike != null) ({ sink(DiagnosticsEvent.RemoveBike) }) else null,
        divider = false,
      )
    }
    Text(
      "Share diagnostics (a zip of raw captures) and ride tests are still in the proof-of-concept app.",
      style = CassetteTheme.type.bodySmall,
      color = colors.muted,
    )
  }
}

private val ScanProfile.label: String
  get() =
    when (this) {
      ScanProfile.AllLowPower -> "Low power"
      ScanProfile.AllBalanced -> "Balanced"
      ScanProfile.Batched30s -> "Batched 30 s"
      ScanProfile.FirstAndLost -> "First + lost"
    }
