package com.r0adkll.cassette.ui

import android.Manifest
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.r0adkll.cassette.axs.BatteryStatus
import com.r0adkll.cassette.axs.FrameType
import com.r0adkll.cassette.axs.PACK_MIN_MILLIVOLTS
import com.r0adkll.cassette.axs.estimatePackPercent
import com.r0adkll.cassette.data.BatterySample
import com.r0adkll.cassette.data.CaptureState
import com.r0adkll.cassette.data.DeviceRecord
import com.r0adkll.cassette.data.HistoryPoint
import com.r0adkll.cassette.scan.NearbyCandidate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, hasPermissions: () -> Boolean, isBluetoothEnabled: () -> Boolean) {
  val capture by viewModel.capture.collectAsStateWithLifecycle()
  val liveScan by viewModel.liveScan.collectAsStateWithLifecycle()
  val candidates by viewModel.candidates.collectAsStateWithLifecycle()
  val rideTests by viewModel.rideTests.collectAsStateWithLifecycle()
  var labeling by remember { mutableStateOf<DeviceRecord?>(null) }

  var permissionGranted by remember { mutableStateOf(hasPermissions()) }
  var bluetoothOn by remember { mutableStateOf(isBluetoothEnabled()) }
  LifecycleResumeEffect(Unit) {
    permissionGranted = hasPermissions()
    bluetoothOn = isBluetoothEnabled()
    onPauseOrDispose {}
  }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      permissionGranted = hasPermissions()
      if (permissionGranted) viewModel.onPermissionGranted()
    }

  Scaffold(topBar = { TopAppBar(title = { Text("Cassette") }) }) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      when {
        !permissionGranted ->
          item {
            Notice("Nearby devices permission is needed to hear AXS broadcasts and read battery levels.") {
              Button(
                onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) }
              ) {
                Text("Grant")
              }
            }
          }
        !bluetoothOn -> item { Notice("Bluetooth is off.") }
      }
      val state = capture ?: return@LazyColumn
      item {
        Controls(
          state = state,
          liveScan = liveScan,
          enabled = permissionGranted && bluetoothOn,
          onToggleLive = viewModel::toggleLiveScan,
          onBackgroundChange = viewModel::setBackgroundCapture,
          onClear = viewModel::clearReadings,
        )
      }
      item {
        RideTestCard(
          state = rideTests,
          enabled = permissionGranted && bluetoothOn,
          onStart = viewModel::startRideTest,
          onStop = viewModel::stopRideTest,
        )
      }
      val devices = state.devices.values.sortedByDescending { it.latest.timestampMillis }
      if (devices.isEmpty()) {
        item {
          Text(
            "No components heard yet. Shake the bike to wake the derailleur, or press the AXS button on a controller or dropper.",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
      items(devices, key = { it.latest.key }) { record ->
        DeviceCard(
          record = record,
          label = state.labels[record.latest.key],
          history = state.history.filter { it.key == record.latest.key },
          onLabel = { labeling = record },
        )
      }
      if (candidates.isNotEmpty()) {
        item { Text("Other SRAM-looking devices (live scan only)", style = MaterialTheme.typography.titleSmall) }
        items(candidates.values.sortedByDescending { it.timestampMillis }, key = { "candidate-${it.address}" }) { CandidateCard(it) }
      }
      if (rideTests.summaries.isNotEmpty()) {
        item { Text("Ride tests", style = MaterialTheme.typography.titleSmall) }
        items(rideTests.summaries, key = { "ride-${it.startedAt}" }) { RideSummaryCard(it, state) }
        if (rideTests.active == null) item { RideTestFooter(onClear = viewModel::clearRideTests) }
      }
    }
  }

  labeling?.let { record ->
    LabelDialog(
      initial = capture?.labels?.get(record.latest.key),
      onDismiss = { labeling = null },
      onSave = { label ->
        viewModel.setLabel(record.latest.key, label)
        labeling = null
      },
    )
  }
}

@Composable
private fun Notice(message: String, action: @Composable () -> Unit = {}) {
  Card(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(message, Modifier.weight(1f))
      action()
    }
  }
}

@Composable
private fun Controls(
  state: CaptureState,
  liveScan: LiveScan,
  enabled: Boolean,
  onToggleLive: () -> Unit,
  onBackgroundChange: (Boolean) -> Unit,
  onClear: () -> Unit,
) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Background capture", style = MaterialTheme.typography.titleMedium)
          val armed =
            when {
              !state.backgroundEnabled -> "Off"
              state.backgroundArmError != null -> "Failed to arm (error ${state.backgroundArmError})"
              else -> "Armed ${relative(state.backgroundArmedAtMillis)} · ${state.backgroundProfile.label}"
            }
          Text(armed, style = MaterialTheme.typography.bodySmall)
          Text(
            "${state.backgroundDeliveries} deliveries, last ${relative(state.lastBackgroundDeliveryMillis)}",
            style = MaterialTheme.typography.bodySmall,
          )
        }
        Switch(checked = state.backgroundEnabled, onCheckedChange = onBackgroundChange, enabled = enabled)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onToggleLive, enabled = enabled) {
          Text(if (liveScan == LiveScan.Scanning) "Stop live scan" else "Live scan")
        }
        OutlinedButton(onClick = onClear) { Text("Clear") }
      }
      if (liveScan is LiveScan.Failed) Text(liveScan.message, color = MaterialTheme.colorScheme.error)
    }
  }
}

@Composable
private fun DeviceCard(record: DeviceRecord, label: String?, history: List<HistoryPoint>, onLabel: () -> Unit) {
  val reading = record.latest
  val battery = record.battery
  var expanded by remember { mutableStateOf(false) }
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(label ?: reading.kind.label, style = MaterialTheme.typography.titleMedium)
          val identity =
            listOfNotNull(
              (reading.serial ?: reading.address).toString(),
              reading.modelId?.let { "model $it" },
              reading.classByte?.let { "type 0x%02x".format(it) } ?: "${reading.frame.name.lowercase()} frame",
            )
          Text(identity.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        Text(battery.headline(), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(8.dp))
        StatusChip(battery?.status ?: BatteryStatus.UNKNOWN)
      }
      val batteryText =
        battery?.let {
          "Battery ${it.method.name.lowercase()} ${relative(it.timestampMillis)}" +
            (it.millivolts?.let { mv -> " · $mv mV" } ?: "") +
            (if (it.reported != null) " · status reported" else " · status estimated")
        } ?: "Battery not read yet"
      Text(batteryText, style = MaterialTheme.typography.bodySmall)
      Text(
        listOfNotNull(
            (reading.firmware ?: record.hubFirmware)?.let { "fw $it" },
            record.actuations?.let { "%,d shifts".format(it) },
            reading.rssi.takeIf { reading.frame != FrameType.HUB_ENTRY }?.let { "$it dBm" },
          )
          .joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
      )
      Text(
        "Seen ${relative(reading.timestampMillis)} (${record.lastSource.name.lowercase()}) · ${record.sightings} sightings · first ${relative(record.firstSeenMillis)}",
        style = MaterialTheme.typography.bodySmall,
      )
      Row {
        TextButton(onClick = onLabel) { Text(if (label == null) "Label" else "Rename") }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide details" else "Raw + history (${history.size})") }
      }
      if (expanded) {
        Mono("mfg ${reading.manufacturerHex ?: "—"}")
        Mono("svc ${reading.serviceDataHex ?: "—"}")
        Mono("addr ${reading.address.ifEmpty { "—" }} name ${reading.name ?: "—"}")
        Mono("system ${record.systemSerial ?: "—"}${if (reading.bridge) " · bridge" else ""}")
        history.takeLast(10).asReversed().forEach { point ->
          val values = listOfNotNull(point.millivolts?.let { "$it mV" }, point.percent?.let { "$it%" }).joinToString("  ")
          Mono("${relative(point.timestampMillis)}  $values  ${point.method.name.lowercase()}/${point.source.name.lowercase()}")
        }
      }
    }
  }
}

@Composable
private fun CandidateCard(candidate: NearbyCandidate) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(candidate.name ?: "Unnamed", style = MaterialTheme.typography.titleMedium)
      Text(
        "${candidate.address} · ${candidate.rssi} dBm · ${candidate.reasons.joinToString()} · ${relative(candidate.timestampMillis)}",
        style = MaterialTheme.typography.bodySmall,
      )
      Mono(candidate.rawHex)
    }
  }
}

private val LABEL_PRESETS = listOf("Front derailleur", "Rear derailleur", "Left shifter", "Right shifter", "Power meter", "Dropper post")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelDialog(initial: String?, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
  var text by remember { mutableStateOf(initial.orEmpty()) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Label component") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          LABEL_PRESETS.forEach { preset ->
            FilterChip(selected = text == preset, onClick = { text = preset }, label = { Text(preset) })
          }
        }
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Name") }, singleLine = true)
      }
    },
    confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
    dismissButton = {
      Row {
        if (initial != null) TextButton(onClick = { onSave(null) }) { Text("Remove") }
        TextButton(onClick = onDismiss) { Text("Cancel") }
      }
    },
  )
}

@Composable
private fun StatusChip(status: BatteryStatus) {
  val color =
    when (status) {
      BatteryStatus.OK -> Color(0xFF2E7D32)
      BatteryStatus.LOW -> Color(0xFFF9A825)
      BatteryStatus.CRITICAL -> MaterialTheme.colorScheme.error
      BatteryStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
  AssistChip(
    onClick = {},
    label = {
      Text(
        when (status) {
          BatteryStatus.OK -> "good"
          BatteryStatus.LOW -> "low"
          BatteryStatus.CRITICAL -> "charge now"
          BatteryStatus.UNKNOWN -> "unknown"
        }
      )
    },
    colors = AssistChipDefaults.assistChipColors(labelColor = color),
  )
}

@Composable
private fun Mono(text: String) {
  Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
}

private fun BatterySample?.headline(): String =
  when {
    this == null -> "—"
    percent != null -> "$percent%"
    millivolts != null && millivolts >= PACK_MIN_MILLIVOLTS -> "≈${estimatePackPercent(millivolts)}%"
    millivolts != null -> "%.2f V".format(millivolts / 1000f)
    else -> "—"
  }

private fun relative(millis: Long?): String =
  millis?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS).toString() } ?: "never"
