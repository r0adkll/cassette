package com.r0adkll.cassette.ui

import android.text.format.DateUtils
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.cdm.AssociationFilter
import com.r0adkll.cassette.cdm.EVENT_BLE_APPEARED
import com.r0adkll.cassette.cdm.PresenceRow
import com.r0adkll.cassette.ridetest.PresenceReadPath

/** CDM spike: associations, observing state and recent presence events with their hub read latency. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CdmSpikeCard(
  state: CdmSpikeState?,
  onLink: (AssociationFilter) -> Unit,
  onObserve: (associationId: Int, observe: Boolean) -> Unit,
  onUnlink: (associationId: Int) -> Unit,
  onReadPath: (PresenceReadPath) -> Unit,
  onClear: () -> Unit,
) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("CDM spike", style = MaterialTheme.typography.titleMedium)
      if (state == null) {
        Text("Needs Android 16 (API 36) for the request-based presence API.", style = MaterialTheme.typography.bodySmall)
        return@Column
      }

      var filter by rememberSaveable { mutableStateOf(AssociationFilter.UUID_AND_NAME) }
      Text("Chooser filter", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssociationFilter.entries.forEach { option -> FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.label) }) }
      }
      Button(onClick = { onLink(filter) }) { Text("Link bike hub (CDM)") }

      Text("Hub read on appeared", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PresenceReadPath.entries.forEach { path ->
          FilterChip(selected = state.readPath == path, onClick = { onReadPath(path) }, label = { Text(path.label) })
        }
      }

      Text("Associations", style = MaterialTheme.typography.labelLarge)
      if (state.associations.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodySmall)
      state.associations.forEach { association ->
        val observing = association.id in state.observing
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("#${association.id} ${association.displayName ?: "Unnamed"}", style = MaterialTheme.typography.bodyMedium)
            Text("${association.address ?: "no address"} · ${if (observing) "observing" else "not observing"}", style = MaterialTheme.typography.bodySmall)
          }
          TextButton(onClick = { onObserve(association.id, !observing) }) { Text(if (observing) "Stop" else "Observe") }
          TextButton(onClick = { onUnlink(association.id) }) { Text("Unlink") }
        }
      }

      if (state.hubAddresses.isNotEmpty()) {
        Text("Scanned hub addresses", style = MaterialTheme.typography.labelLarge)
        state.hubAddresses.forEach { hub ->
          val match = hub.matchedAssociationId?.let { "matches #$it" } ?: if (hub.associated.isEmpty()) "no associations" else "no association match"
          val changed = hub.previousAddress?.let { " · was $it" } ?: ""
          Mono("${hub.key} ${hub.scannedAddress} · $match$changed · ${since(hub.at)}")
        }
      }

      Text("Presence events", style = MaterialTheme.typography.labelLarge)
      if (state.timeline.isEmpty()) Text("None yet. Wake the hub, or simulate one with adb.", style = MaterialTheme.typography.bodySmall)
      state.timeline.forEach { row -> Mono(row.describe()) }

      if (state.recentActions.isNotEmpty()) {
        Text("CDM actions", style = MaterialTheme.typography.labelLarge)
        state.recentActions.forEach { action ->
          Mono("${since(action.at)} ${action.action}${action.associationId?.let { " #$it" } ?: ""}${action.detail?.let { " · $it" } ?: ""}")
        }
      }
      Row { OutlinedButton(onClick = onClear) { Text("Clear CDM log") } }
    }
  }
}

private val PresenceReadPath.label: String
  get() =
    when (this) {
      PresenceReadPath.BOUND_SERVICE -> "Bound service"
      PresenceReadPath.FOREGROUND_SERVICE -> "Foreground service"
    }

private fun PresenceRow.describe(): String {
  val head = "${since(event.at)} ${event.eventName} #${event.associationId}" + (event.processAgeMillis?.let { " · process ${seconds(it)}" } ?: "")
  if (event.event != EVENT_BLE_APPEARED) return head
  val read = read ?: return "$head · no read yet"
  return "$head · read ${read.outcome.name.lowercase()} after ${seconds(latencyMillis ?: 0)} (gatt ${seconds(read.durationMillis)}, ${read.path.label.lowercase()})"
}

private fun seconds(millis: Long): String = "%.1f s".format(millis / 1000f)

private fun since(millis: Long): String = DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS).toString()

@Composable
private fun Mono(text: String) {
  Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
}
