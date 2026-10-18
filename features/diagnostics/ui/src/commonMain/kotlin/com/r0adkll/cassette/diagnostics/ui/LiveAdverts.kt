package com.r0adkll.cassette.diagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.capture.api.Diagnostics
import com.r0adkll.cassette.common.compose.components.ChoiceChips
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.LiveAdvertsScreen
import com.r0adkll.cassette.protocol.AxsReading
import com.r0adkll.cassette.protocol.FrameType
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.catch

enum class AdvertFilter(val label: String, val frame: FrameType?) {
  All("All", null),
  Status("Status", FrameType.STATUS),
  Beacon("Beacon", FrameType.BEACON),
  Power("Power", FrameType.POWER_METER),
}

data class LiveAdvertsUiState(
  val adverts: List<AdvertRow>,
  val filter: AdvertFilter,
  val paused: Boolean,
  val failed: Boolean,
  val eventSink: (LiveAdvertsEvent) -> Unit,
) : CircuitUiState

data class AdvertRow(
  val id: Long,
  val seconds: String,
  val code: String,
  val frame: FrameType,
  val rssi: Int,
  val lines: List<String>,
)

sealed interface LiveAdvertsEvent : CircuitUiEvent {
  data object Back : LiveAdvertsEvent

  data object TogglePause : LiveAdvertsEvent

  data class Filter(val filter: AdvertFilter) : LiveAdvertsEvent
}

@CircuitInject(LiveAdvertsScreen::class, AppScope::class)
@Inject
class LiveAdvertsPresenter(
  private val navigator: Navigator,
  private val diagnostics: Diagnostics,
  private val clock: Clock,
) : Presenter<LiveAdvertsUiState> {

  @Composable
  override fun present(): LiveAdvertsUiState {
    var rows by rememberRetained { mutableStateOf(emptyList<AdvertRow>()) }
    var filter by rememberRetained { mutableStateOf(AdvertFilter.All) }
    var paused by rememberRetained { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val started = remember { clock.now().toEpochMilliseconds() }

    LaunchedEffect(paused) {
      if (paused) return@LaunchedEffect
      var next = rows.firstOrNull()?.id?.plus(1) ?: 0
      diagnostics
        .liveAdverts()
        .catch { failed = true }
        .collect { reading ->
          rows = (listOf(reading.toRow(next++, started)) + rows).take(MAX_ROWS)
        }
    }

    return LiveAdvertsUiState(
      adverts = rows.filter { filter.frame == null || it.frame == filter.frame },
      filter = filter,
      paused = paused,
      failed = failed,
    ) { event ->
      when (event) {
        LiveAdvertsEvent.Back -> navigator.pop()
        LiveAdvertsEvent.TogglePause -> paused = !paused
        is LiveAdvertsEvent.Filter -> filter = event.filter
      }
    }
  }

  private fun AxsReading.toRow(id: Long, started: Long): AdvertRow {
    val elapsed = (timestampMillis - started).coerceAtLeast(0)
    return AdvertRow(
      id = id,
      seconds = "+${elapsed / 1000}.${(elapsed % 1000 / 100)} s",
      code = kind.short,
      frame = frame,
      rssi = rssi,
      lines =
        listOfNotNull(
          name?.let { "name $it" },
          serial?.let { "serial $it" },
          manufacturerHex?.let { "mfg $it" },
          serviceDataHex?.let { "svc $it" },
        ),
    )
  }

  private companion object {
    const val MAX_ROWS = 200
  }
}

@CircuitInject(LiveAdvertsScreen::class, AppScope::class)
@Composable
fun LiveAdvertsUi(state: LiveAdvertsUiState, modifier: Modifier = Modifier) {
  val colors = CassetteTheme.colors
  val sink = state.eventSink
  DiagnosticsScaffold(
    "Live adverts.",
    onBack = { sink(LiveAdvertsEvent.Back) },
    modifier,
    action = {
      Row(
        Modifier.clip(RoundedCornerShape(18.dp))
          .border(1.dp, colors.line, RoundedCornerShape(18.dp))
          .clickable { sink(LiveAdvertsEvent.TogglePause) }
          .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          Modifier.size(7.dp)
            .clip(CircleShape)
            .background(if (state.paused) colors.muted else colors.brand)
        )
        Spacer(Modifier.width(6.dp))
        Text(
          if (state.paused) "PAUSED" else "LIVE",
          style = CassetteTheme.type.label,
          color = colors.text,
        )
      }
    },
  ) {
    ChoiceChips(
      AdvertFilter.entries,
      state.filter,
      { it.label },
      { sink(LiveAdvertsEvent.Filter(it)) },
    )
    if (state.failed) {
      Text(
        "Bluetooth is off or not allowed for Cassette.",
        style = CassetteTheme.type.body,
        color = colors.low,
      )
    } else if (state.adverts.isEmpty()) {
      Text(
        "Listening… wake the bike to see its adverts.",
        style = CassetteTheme.type.body,
        color = colors.muted,
      )
    }
    Column {
      state.adverts.forEach { row ->
        Column(
          Modifier.padding(vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(row.seconds, style = CassetteTheme.type.mono, color = colors.muted)
            Text(row.code, style = CassetteTheme.type.label, color = colors.text)
            FrameTag(row.frame)
            Spacer(Modifier.weight(1f))
            Text("${row.rssi} dBm", style = CassetteTheme.type.mono, color = colors.muted)
          }
          row.lines.forEach { Text(it, style = CassetteTheme.type.mono, color = colors.text) }
        }
        HorizontalDivider(color = colors.line)
      }
    }
  }
}

@Composable
private fun FrameTag(frame: FrameType) {
  val colors = CassetteTheme.colors
  val color =
    when (frame) {
      FrameType.STATUS -> colors.good
      FrameType.POWER_METER -> colors.low
      else -> colors.muted
    }
  Text(
    frame.name.replace('_', ' '),
    style = CassetteTheme.type.mono.copy(fontSize = CassetteTheme.type.bodySmall.fontSize * 0.85f),
    color = color,
    modifier =
      Modifier.border(1.dp, color, RoundedCornerShape(4.dp))
        .padding(horizontal = 6.dp, vertical = 1.dp),
  )
}
