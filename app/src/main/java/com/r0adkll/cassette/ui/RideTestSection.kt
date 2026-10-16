package com.r0adkll.cassette.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.data.CaptureState
import com.r0adkll.cassette.ridetest.RideSummary
import com.r0adkll.cassette.scan.ScanProfile
import java.util.Date

private val LABEL_PRESETS = listOf("Pocket · no head unit", "Pocket · head unit connected", "Bag · head unit connected")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RideTestCard(
  state: RideTestState,
  enabled: Boolean,
  onStart: (label: String, profile: ScanProfile) -> Unit,
  onStop: () -> Unit,
) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Ride test", style = MaterialTheme.typography.titleMedium)
      val running = state.active?.let { active -> state.summaries.firstOrNull { it.startedAt == active.at } }
      if (running != null) {
        Text("Running: ${running.label} · ${running.profile.label}", style = MaterialTheme.typography.bodyMedium)
        Text(
          "${duration(running.durationMillis)} · ${running.wakes} wakes · last wake ${duration(running.silentTailMillis)} ago · " +
            "longest gap ${duration(running.longestGapMillis)} · reads ${running.gattOk}/${running.gattAttempts}",
          style = MaterialTheme.typography.bodySmall,
        )
        Text("Lock the phone and ride. Stop when you're back.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onStop) { Text("Stop test") }
      } else {
        var label by rememberSaveable { mutableStateOf(LABEL_PRESETS.first()) }
        var profile by rememberSaveable { mutableStateOf(ScanProfile.ALL_LOW_POWER) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          LABEL_PRESETS.forEach { preset -> FilterChip(selected = label == preset, onClick = { label = preset }, label = { Text(preset) }) }
        }
        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Setup") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Scan profile", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ScanProfile.entries.forEach { option -> FilterChip(selected = profile == option, onClick = { profile = option }, label = { Text(option.label) }) }
        }
        Text(profile.description, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { onStart(label, profile) }, enabled = enabled) { Text("Start test") }
      }
    }
  }
}

@Composable
fun RideSummaryCard(summary: RideSummary, capture: CaptureState) {
  val context = LocalContext.current
  fun clock(millis: Long) = DateFormat.getTimeFormat(context).format(Date(millis))
  fun componentName(key: String) = capture.labels[key] ?: capture.devices[key]?.latest?.kind?.label?.let { "$it $key" } ?: key

  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(summary.label, style = MaterialTheme.typography.titleSmall)
      val phone = summary.phoneBatteryUsed?.let { " · phone battery −$it%" } ?: ""
      Text(
        "${DateFormat.getDateFormat(context).format(Date(summary.startedAt))} ${clock(summary.startedAt)} · ${duration(summary.durationMillis)}" +
          (if (summary.endedAt == null) " (running)" else "") + phone,
        style = MaterialTheme.typography.bodySmall,
      )
      Text(summary.profile.label, style = MaterialTheme.typography.bodySmall)
      Text(
        "Wakes ${summary.wakes} (%.0f/h) · longest gap ${duration(summary.longestGapMillis)}".format(summary.wakesPerHour) +
          (summary.longestGapStartedAt?.let { " from ${clock(it)}" } ?: "") +
          " · quiet ${duration(summary.silentTailMillis)} at end",
        style = MaterialTheme.typography.bodySmall,
      )
      val average = summary.gattAverageMillis?.let { ", avg %.1f s".format(it / 1000f) } ?: ""
      Text(
        "Background reads ${summary.gattOk}/${summary.gattAttempts} ok$average · failed ${summary.gattFailed} · out of time ${summary.budgetExceeded}",
        style = MaterialTheme.typography.bodySmall,
      )
      if (summary.armFailures.isNotEmpty()) {
        Text("Scan arm failures: ${summary.armFailures.joinToString()}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
      }
      summary.components.forEach { component ->
        Text(
          "${componentName(component.key)}: in ${component.wakesHeardIn} wakes, ${clock(component.firstHeardAt)}–${clock(component.lastHeardAt)}, " +
            "longest silence ${duration(component.longestSilenceMillis)}" +
            (if (component.gattReadsOk > 0) " · ${component.gattReadsOk} reads" else ""),
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

@Composable
fun RideTestFooter(onClear: () -> Unit) {
  Row { OutlinedButton(onClick = onClear) { Text("Clear ride tests") } }
}

private fun duration(millis: Long): String {
  val seconds = millis / 1000
  return when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
  }
}
