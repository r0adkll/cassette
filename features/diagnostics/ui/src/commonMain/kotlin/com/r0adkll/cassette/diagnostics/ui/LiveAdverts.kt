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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.bikes.summary.code
import com.r0adkll.cassette.capture.api.Diagnostics
import com.r0adkll.cassette.common.compose.components.ChoiceChips
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.LiveAdvertsScreen
import com.r0adkll.cassette.protocol.Advert
import com.r0adkll.cassette.protocol.AxsReading
import com.r0adkll.cassette.protocol.FrameType
import com.r0adkll.cassette.protocol.ShimanoAdvert
import com.r0adkll.cassette.protocol.shortUuid
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
import kotlinx.coroutines.launch

/** What a row heard: an AXS frame, or a Shimano advert (decision 0029). */
enum class AdvertTag(val label: String) {
  Status("STATUS"),
  Beacon("BEACON"),
  PowerMeter("POWER METER"),
  HubEntry("HUB ENTRY"),
  Shimano("SHIMANO"),
}

enum class AdvertFilter(val label: String, val tag: AdvertTag?) {
  All("All", null),
  Status("Status", AdvertTag.Status),
  Beacon("Beacon", AdvertTag.Beacon),
  Power("Power", AdvertTag.PowerMeter),
  Shimano("Shimano", AdvertTag.Shimano),
}

data class LiveAdvertsUiState(
  val adverts: List<AdvertRow>,
  val filter: AdvertFilter,
  val paused: Boolean,
  val failed: Boolean,
  val probes: Map<String, ProbeState> = emptyMap(),
  val eventSink: (LiveAdvertsEvent) -> Unit,
) : CircuitUiState

data class AdvertRow(
  val id: Long,
  val seconds: String,
  val code: String,
  val tag: AdvertTag,
  val rssi: Int,
  val lines: List<String>,
  /** Shimano rows: the address [LiveAdvertsEvent.ReadShimano] reads. */
  val probeAddress: String? = null,
)

/** A Shimano probe's state, by address: reading, or what it found. */
sealed interface ProbeState {
  data object Reading : ProbeState

  data class Done(val lines: List<String>) : ProbeState
}

sealed interface LiveAdvertsEvent : CircuitUiEvent {
  data object Back : LiveAdvertsEvent

  data object TogglePause : LiveAdvertsEvent

  data class Filter(val filter: AdvertFilter) : LiveAdvertsEvent

  /** Reads a Shimano derailleur's battery and device information (decision 0029). */
  data class ReadShimano(val address: String) : LiveAdvertsEvent
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
    var probes by rememberRetained { mutableStateOf(emptyMap<String, ProbeState>()) }
    val scope = rememberCoroutineScope()
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
      adverts = rows.filter { filter.tag == null || it.tag == filter.tag },
      filter = filter,
      paused = paused,
      failed = failed,
      probes = probes,
    ) { event ->
      when (event) {
        LiveAdvertsEvent.Back -> navigator.pop()
        LiveAdvertsEvent.TogglePause -> paused = !paused
        is LiveAdvertsEvent.Filter -> filter = event.filter
        is LiveAdvertsEvent.ReadShimano ->
          if (probes[event.address] != ProbeState.Reading) {
            probes = probes + (event.address to ProbeState.Reading)
            scope.launch {
              val probe = diagnostics.probeShimano(event.address)
              probes = probes + (event.address to ProbeState.Done(probe.lines))
            }
          }
      }
    }
  }

  private fun Advert.toRow(id: Long, started: Long): AdvertRow {
    val elapsed = (timestampMillis - started).coerceAtLeast(0)
    val seconds = "+${elapsed / 1000}.${(elapsed % 1000 / 100)} s"
    return when (this) {
      is AxsReading ->
        AdvertRow(
          id = id,
          seconds = seconds,
          code = kind.code,
          tag = frame.tag,
          rssi = rssi,
          lines =
            listOfNotNull(
              name?.let { "name $it" },
              serial?.let { "serial $it" },
              manufacturerHex?.let { "mfg $it" },
              serviceDataHex?.let { "svc $it" },
            ),
        )
      is ShimanoAdvert ->
        AdvertRow(
          id = id,
          seconds = seconds,
          code = "Di2",
          tag = AdvertTag.Shimano,
          rssi = rssi,
          lines =
            listOfNotNull(
              name?.let { "name $it" },
              "address $address",
              manufacturerHex?.let { "mfg $it" },
              shortServices.takeIf { it.isNotEmpty() }?.joinToString(prefix = "services "),
            ) + serviceData.map { (uuid, hex) -> "svc ${shortUuid(uuid)} $hex" },
          probeAddress = address,
        )
    }
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
            FrameTag(row.tag)
            Spacer(Modifier.weight(1f))
            Text("${row.rssi} dBm", style = CassetteTheme.type.mono, color = colors.muted)
          }
          row.lines.forEach { Text(it, style = CassetteTheme.type.mono, color = colors.text) }
          row.probeAddress?.let { address ->
            ProbeAction(state.probes[address]) { sink(LiveAdvertsEvent.ReadShimano(address)) }
          }
        }
        HorizontalDivider(color = colors.line)
      }
    }
  }
}

private val FrameType.tag: AdvertTag
  get() =
    when (this) {
      FrameType.STATUS -> AdvertTag.Status
      FrameType.BEACON -> AdvertTag.Beacon
      FrameType.POWER_METER -> AdvertTag.PowerMeter
      FrameType.HUB_ENTRY -> AdvertTag.HubEntry
    }

/** A Shimano row's read: the button, then what it found (decision 0029). */
@Composable
private fun ProbeAction(state: ProbeState?, onRead: () -> Unit) {
  val colors = CassetteTheme.colors
  when (state) {
    is ProbeState.Done ->
      state.lines.forEach { Text(it, style = CassetteTheme.type.mono, color = colors.text) }
    else ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          if (state == ProbeState.Reading) "Reading…" else "Read battery",
          style = CassetteTheme.type.label,
          color = colors.text,
          modifier =
            Modifier.clip(RoundedCornerShape(14.dp))
              .border(1.dp, colors.line, RoundedCornerShape(14.dp))
              .clickable(enabled = state == null, onClick = onRead)
              .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Text(
          "Connects briefly. Not while riding.",
          style = CassetteTheme.type.bodySmall,
          color = colors.muted,
        )
      }
  }
}

@Composable
private fun FrameTag(tag: AdvertTag) {
  val colors = CassetteTheme.colors
  val color =
    when (tag) {
      AdvertTag.Status -> colors.good
      AdvertTag.PowerMeter -> colors.low
      AdvertTag.Shimano -> colors.text
      else -> colors.muted
    }
  Text(
    tag.label,
    style = CassetteTheme.type.mono.copy(fontSize = CassetteTheme.type.bodySmall.fontSize * 0.85f),
    color = color,
    modifier =
      Modifier.border(1.dp, color, RoundedCornerShape(4.dp))
        .padding(horizontal = 6.dp, vertical = 1.dp),
  )
}
