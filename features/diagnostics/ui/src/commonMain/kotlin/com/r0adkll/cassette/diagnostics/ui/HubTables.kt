package com.r0adkll.cassette.diagnostics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.capture.api.Diagnostics
import com.r0adkll.cassette.capture.api.HubDump
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.HubTablesScreen
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.protocol.SystemBatteryTable
import com.r0adkll.cassette.protocol.toHex
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject

data class HubTablesUiState(
  val reading: Boolean,
  /** One per hub of the bike (decision 0028); empty until the first read. */
  val dumps: List<HubDump>,
  /** No hub answered. */
  val failed: Boolean,
  val eventSink: (HubTablesEvent) -> Unit,
) : CircuitUiState

sealed interface HubTablesEvent : CircuitUiEvent {
  data object Back : HubTablesEvent

  data object ReadAgain : HubTablesEvent
}

@CircuitInject(HubTablesScreen::class, AppScope::class)
@Inject
class HubTablesPresenter(
  private val screen: HubTablesScreen,
  private val navigator: Navigator,
  private val diagnostics: Diagnostics,
) : Presenter<HubTablesUiState> {

  @Composable
  override fun present(): HubTablesUiState {
    var attempt by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf(true) }
    var dumps by remember { mutableStateOf(emptyList<HubDump>()) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
      reading = true
      val result = diagnostics.readHubTables(BikeId(screen.bikeId))
      if (result.any { it.answered }) dumps = result
      failed = result.none { it.answered }
      reading = false
    }

    return HubTablesUiState(reading, dumps, failed) { event ->
      when (event) {
        HubTablesEvent.Back -> navigator.pop()
        HubTablesEvent.ReadAgain -> if (!reading) attempt++
      }
    }
  }
}

@CircuitInject(HubTablesScreen::class, AppScope::class)
@Composable
fun HubTablesUi(state: HubTablesUiState, modifier: Modifier = Modifier) {
  val colors = CassetteTheme.colors
  DiagnosticsScaffold("Hub tables.", onBack = { state.eventSink(HubTablesEvent.Back) }, modifier) {
    when {
      state.reading && state.dumps.isEmpty() ->
        Text(
          "Reading the hub… wake the bike if this takes long.",
          style = CassetteTheme.type.body,
          color = colors.muted,
        )
      state.failed && state.dumps.isEmpty() ->
        Text(
          "No answer: the hub may be asleep, or Cassette hasn't heard it yet. Wake the bike and read again.",
          style = CassetteTheme.type.body,
          color = colors.low,
        )
    }
    state.dumps.forEach { dump ->
      if (dump.answered) Dump(dump)
      else
        Panel {
          Kicker("hub ${dump.hubSerial}")
          Text(
            if (dump.address == null) "Not heard yet." else "No answer.",
            style = CassetteTheme.type.mono,
            color = colors.low,
          )
        }
    }
    CassetteButton(
      if (state.reading) "Reading…" else "Read again",
      onClick = { state.eventSink(HubTablesEvent.ReadAgain) },
      primary = false,
      icon = LucideIcons.RefreshCw,
    )
  }
}

@Composable
private fun Dump(dump: HubDump) {
  val colors = CassetteTheme.colors
  Panel {
    Row {
      Kicker("Battery · d9050003", Modifier.weight(1f))
      Text("hub ${dump.hubSerial}", style = CassetteTheme.type.mono, color = colors.muted)
    }
    val rows = dump.battery?.rows.orEmpty()
    if (rows.isEmpty())
      Text("No battery table.", style = CassetteTheme.type.mono, color = colors.low)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(
          "type" to 40,
          "" to 24,
          "model" to 40,
          "serial" to 88,
          "soc" to 30,
          "mV" to 40,
          "acts" to 50,
        )
        .forEach { (h, w) ->
          Text(
            h,
            style = CassetteTheme.type.mono,
            color = colors.muted,
            modifier = Modifier.width(w.dp),
          )
        }
    }
    rows.forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "0x" + row.deviceType.toString(16).padStart(2, '0') to 40,
            row.kind.short to 24,
            row.modelId.toString() to 40,
            row.serial.toString() to 88,
            (row.reportedStatus?.name?.take(4) ?: "?") to 30,
            row.millivolts.toString() to 40,
            row.actuations.toString() to 50,
          )
          .forEach { (v, w) ->
            Text(
              v,
              style = CassetteTheme.type.mono,
              color = colors.text,
              modifier = Modifier.width(w.dp),
            )
          }
      }
    }
  }
  Panel {
    Kicker("Raw · ${SystemBatteryTable.CHARACTERISTIC.take(8)}")
    Text(dump.batteryBytes?.toHex() ?: "—", style = CassetteTheme.type.mono, color = colors.text)
  }
  Panel {
    Kicker("Firmware · ${SystemBatteryTable.VERSIONS_CHARACTERISTIC.take(8)}")
    dump.versions?.rows?.forEach {
      MonoLine(ComponentKind.fromDeviceType(it.deviceType).short + " " + it.serial, it.firmware)
    } ?: Text("Not read.", style = CassetteTheme.type.mono, color = colors.muted)
    Text(dump.versionBytes?.toHex() ?: "", style = CassetteTheme.type.mono, color = colors.muted)
  }
}
