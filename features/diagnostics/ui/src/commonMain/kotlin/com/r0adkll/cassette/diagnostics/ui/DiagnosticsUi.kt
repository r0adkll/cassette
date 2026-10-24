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
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.common.compose.AppPlatform
import com.r0adkll.cassette.common.compose.LocalAppPlatform
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.SettingsRow
import com.r0adkll.cassette.common.compose.components.SettingsSection
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.DiagnosticsScreen
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope
import kotlin.time.Instant

@CircuitInject(DiagnosticsScreen::class, AppScope::class)
@Composable
fun DiagnosticsUi(state: DiagnosticsUiState, modifier: Modifier = Modifier) {
  val sink = state.eventSink
  val colors = CassetteTheme.colors
  val ios = LocalAppPlatform.current == AppPlatform.Ios
  DiagnosticsScaffold(
    "Diagnostics.",
    onBack = { sink(DiagnosticsEvent.Back) }.takeUnless { state.inPane },
    modifier,
  ) {
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
      MonoLine("mode", if (ios) "scan (Core Bluetooth, FE51)" else "scan (PendingIntent)")
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
    // The Apple Watch on iOS (decision 0017).
    WearPanel(state.wear, state.now, ios)
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
      )
      SettingsRow(
        "Ride tests",
        subtitle = "Capture log: ${state.logEvents} events",
        icon = LucideIcons.Route,
        onClick = { sink(DiagnosticsEvent.OpenRideTests) },
        divider = false,
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
        "Add a random bike",
        subtitle = "Made up, with random levels and some low: for testing several bikes",
        icon = LucideIcons.BikePlus,
        onClick = { sink(DiagnosticsEvent.AddRandomBike) },
      )
      SettingsRow(
        "Demo refresh",
        subtitle =
          "The sample bike answers over 30 s, no Bluetooth needed: shows the Live Activity",
        icon = LucideIcons.RefreshCw,
        onClick = { sink(DiagnosticsEvent.DemoRefresh) },
      )
      SettingsRow(
        "Simulate a low battery",
        subtitle = "Front derailleur reads low, as if the bike just woke: tests notifications",
        icon = LucideIcons.BellRing,
        onClick = if (state.bike != null) ({ sink(DiagnosticsEvent.SimulateLowBattery) }) else null,
      )
      SettingsRow(
        if (state.confirmRemove) "Tap again to remove" else "Remove bike",
        subtitle = state.bike?.let { "${it.bike.name}, with its history" } ?: "No bike",
        icon = LucideIcons.X,
        onClick = if (state.bike != null) ({ sink(DiagnosticsEvent.RemoveBike) }) else null,
        divider = false,
      )
    }
    CassetteButton(
      if (state.sharing) "Preparing…" else "Share diagnostics",
      onClick = { sink(DiagnosticsEvent.Share) },
      icon = LucideIcons.Share,
    )
    Text(
      if (ios)
        "Three files through the share sheet: the capture log, a copy of the database and " +
          "device info. Nothing is uploaded."
      else
        "A zip through the share sheet: the capture log, a copy of the database and device " +
          "info. Nothing is uploaded.",
      style = CassetteTheme.type.bodySmall,
      color = colors.muted,
    )
  }
}

/**
 * Watch sync (decisions 0013 and 0017): the watches, the last snapshot sent and the last refresh
 * asked. On iOS, the paired Apple Watch.
 */
@Composable
private fun WearPanel(wear: WearDiagnostics, now: Instant, ios: Boolean) {
  Panel {
    Kicker(if (ios) "Apple Watch" else "Wear")
    MonoLine(
      "watches",
      when (val check = wear.watches) {
        null -> "checking…"
        WatchCheck.Unavailable ->
          if (ios) "no Apple Watch support on this device" else "no Wear OS app on this phone"
        is WatchCheck.Found ->
          check.watches
            .joinToString { "${it.name}${if (it.hasCassette) "" else " (no Cassette)"}" }
            .ifEmpty { if (ios) "none paired" else "none connected" }
      },
    )
    MonoLine(
      "published",
      wear.lastPublish?.let { "${it.detail} · ${agoShort(now - it.instant)}" } ?: "not yet",
    )
    MonoLine(
      "refresh",
      wear.lastRequest?.let { "asked by ${it.detail} · ${agoShort(now - it.instant)}" }
        ?: "never asked",
    )
  }
}

private val CaptureEvent.Wear.instant: Instant
  get() = Instant.fromEpochMilliseconds(at)
