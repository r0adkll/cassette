package com.r0adkll.cassette.diagnostics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.Diagnostics
import com.r0adkll.cassette.capture.api.DiagnosticsSharer
import com.r0adkll.cassette.capture.api.ShimanoProbe
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.Di2CheckScreen
import com.r0adkll.cassette.protocol.ShimanoAdvert
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

data class Di2CheckUiState(
  /** The derailleur the check reads: the strongest heard, until a read fixes it. */
  val derailleur: HeardDi2?,
  /** Bluetooth is off or not allowed, so nothing can be heard. */
  val failed: Boolean,
  val read: Di2Read?,
  /** The rider's answer: did it shift normally after the read? Null until they say. */
  val shifted: Boolean?,
  val sharing: Boolean,
  val eventSink: (Di2CheckEvent) -> Unit,
) : CircuitUiState

data class HeardDi2(val address: String, val name: String?, val rssi: Int, val others: Int)

sealed interface Di2Read {
  data object Reading : Di2Read

  data class Done(val probe: ShimanoProbe) : Di2Read
}

sealed interface Di2CheckEvent : CircuitUiEvent {
  data object Back : Di2CheckEvent

  data object ReadBattery : Di2CheckEvent

  data class Shifted(val normally: Boolean) : Di2CheckEvent

  data object Share : Di2CheckEvent
}

/**
 * A Di2 rider's check (decision 0029): listen for the rear derailleur, read its open battery and
 * device information, ask whether it still shifts, and share the export. Each step lands in the
 * capture log, so the export tells the story without the rider writing it up.
 */
@CircuitInject(Di2CheckScreen::class, AppScope::class)
@Inject
class Di2CheckPresenter(
  private val navigator: Navigator,
  private val diagnostics: Diagnostics,
  private val log: CaptureLog,
  private val sharer: DiagnosticsSharer,
  private val clock: Clock,
) : Presenter<Di2CheckUiState> {

  @Composable
  override fun present(): Di2CheckUiState {
    val scope = rememberCoroutineScope()
    var heard by rememberRetained { mutableStateOf(emptyMap<String, ShimanoAdvert>()) }
    var failed by remember { mutableStateOf(false) }
    var locked by rememberRetained { mutableStateOf<String?>(null) }
    var read by rememberRetained { mutableStateOf<Di2Read?>(null) }
    var shifted by rememberRetained { mutableStateOf<Boolean?>(null) }
    var sharing by remember { mutableStateOf(false) }
    var opened by rememberRetained { mutableStateOf(false) }

    fun note(detail: String) = scope.launch {
      log.append(CaptureEvent.Di2Check(clock.now().toEpochMilliseconds(), detail))
    }

    LaunchedEffect(Unit) {
      if (!opened) {
        opened = true
        note("opened")
      }
      diagnostics
        .liveAdverts()
        .catch { failed = true }
        .collect { advert ->
          if (advert !is ShimanoAdvert) return@collect
          if (advert.address !in heard)
            note("heard ${advert.name ?: "a derailleur"} ${advert.address}")
          heard = heard + (advert.address to advert)
        }
    }

    val target = locked?.let(heard::get) ?: heard.values.maxByOrNull { it.rssi }
    return Di2CheckUiState(
      derailleur = target?.let { HeardDi2(it.address, it.name, it.rssi, others = heard.size - 1) },
      failed = failed,
      read = read,
      shifted = shifted,
      sharing = sharing,
    ) { event ->
      when (event) {
        Di2CheckEvent.Back -> navigator.pop()
        Di2CheckEvent.ReadBattery -> {
          val address = target?.address
          if (address != null && read != Di2Read.Reading) {
            locked = address
            read = Di2Read.Reading
            shifted = null
            scope.launch { read = Di2Read.Done(diagnostics.probeShimano(address)) }
          }
        }
        is Di2CheckEvent.Shifted -> {
          shifted = event.normally
          note(
            if (event.normally) "shifted normally after the read"
            else "didn't shift normally after the read"
          )
        }
        Di2CheckEvent.Share ->
          if (!sharing) {
            sharing = true
            scope.launch {
              log.append(CaptureEvent.Di2Check(clock.now().toEpochMilliseconds(), "shared"))
              sharer.share()
              sharing = false
            }
          }
      }
    }
  }
}

@CircuitInject(Di2CheckScreen::class, AppScope::class)
@Composable
fun Di2CheckUi(state: Di2CheckUiState, modifier: Modifier = Modifier) {
  val sink = state.eventSink
  val colors = CassetteTheme.colors
  val derailleur = state.derailleur
  val done = (state.read as? Di2Read.Done)?.probe
  DiagnosticsScaffold("Di2 check.", onBack = { sink(Di2CheckEvent.Back) }, modifier) {
    Text(
      "For Shimano 12-speed Di2. Cassette can't show Di2 batteries yet: this records what your " +
        "rear derailleur sends, so it can learn to.",
      style = CassetteTheme.type.body,
      color = colors.muted,
    )
    Panel {
      Check(
        when {
          state.failed -> "Can't listen"
          derailleur == null -> "Listening for Di2…"
          else -> "${derailleur.name ?: "Derailleur"} heard"
        },
        when {
          state.failed -> "Bluetooth is off or not allowed for Cassette"
          derailleur == null -> "Shake the bike or press a shift button to wake it"
          derailleur.others > 0 ->
            "${derailleur.rssi} dBm · ${derailleur.others} more nearby; the closest is read"
          else -> "${derailleur.rssi} dBm"
        },
        LucideIcons.BluetoothSearching,
        ok = derailleur != null,
      )
      Check(
        when {
          state.read == Di2Read.Reading -> "Reading the battery…"
          done == null -> "Battery not read yet"
          done.failure != null -> "The read didn't work"
          done.batteryPercent != null -> "Battery ${done.batteryPercent} %"
          else -> "Answered without a battery level"
        },
        when {
          done?.failure != null -> done.failure!!
          done != null -> done.deviceInfo["model"]?.let { "Model $it" } ?: "Saved for the export"
          else -> "Connects for a few seconds. Not while riding"
        },
        LucideIcons.BatteryMedium,
        ok = done?.batteryPercent != null,
      )
      Check(
        when (state.shifted) {
          true -> "Shifted normally after the read"
          false -> "Didn't shift normally after the read"
          null -> "Shifting not checked"
        },
        if (state.shifted == false)
          "It's in the export. If it still won't shift, turn the phone's Bluetooth off for a " +
            "moment, then shift again"
        else "Shift through a few gears once the battery's been read",
        LucideIcons.Bike,
        ok = state.shifted == true,
        divider = false,
      )
    }
    when {
      done == null ->
        CassetteButton(
          if (state.read == Di2Read.Reading) "Reading…" else "Read battery",
          onClick = { sink(Di2CheckEvent.ReadBattery) },
          icon = LucideIcons.BatteryMedium,
          enabled = derailleur != null && state.read != Di2Read.Reading,
        )
      state.shifted == null -> {
        Kicker("Now shift a few gears", Modifier.padding(top = 4.dp))
        Text(
          "Up and down, front and rear. Did the bike shift as it always does?",
          style = CassetteTheme.type.body,
          color = colors.text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          CassetteButton(
            "It shifted",
            onClick = { sink(Di2CheckEvent.Shifted(true)) },
            modifier = Modifier.weight(1f),
          )
          CassetteButton(
            "It didn't",
            onClick = { sink(Di2CheckEvent.Shifted(false)) },
            modifier = Modifier.weight(1f),
            primary = false,
          )
        }
      }
    }
    CassetteButton(
      if (state.sharing) "Preparing…" else "Share diagnostics",
      onClick = { sink(Di2CheckEvent.Share) },
      icon = LucideIcons.Share,
      primary = done != null && state.shifted != null,
    )
    Text(
      "The export holds the capture log, a copy of Cassette's database and device info. Nothing " +
        "is uploaded: send it to whoever asked you to run the check.",
      style = CassetteTheme.type.bodySmall,
      color = colors.muted,
    )
  }
}
