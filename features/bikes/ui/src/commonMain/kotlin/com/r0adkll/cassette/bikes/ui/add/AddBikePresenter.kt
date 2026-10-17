package com.r0adkll.cassette.bikes.ui.add

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.r0adkll.cassette.bikes.summary.code
import com.r0adkll.cassette.bikes.summary.tone
import com.r0adkll.cassette.capture.api.BikeDiscovery
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.Discovered
import com.r0adkll.cassette.capture.api.FoundHub
import com.r0adkll.cassette.capture.api.HubDetails
import com.r0adkll.cassette.common.screens.AddBikeScreen
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.core.BatteryReading
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.ComponentId
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.core.status
import com.r0adkll.cassette.core.valueText
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@CircuitInject(AddBikeScreen::class, AppScope::class)
@Inject
class AddBikePresenter(
  private val navigator: Navigator,
  private val discovery: BikeDiscovery,
  private val capture: CaptureController,
  private val clock: Clock,
) : Presenter<AddBikeUiState> {

  @Composable
  override fun present(): AddBikeUiState {
    val scope = rememberCoroutineScope()
    // One scan for the whole flow: it finds the hub, then the power meters nearby.
    val discovered by remember {
      discovery.discover().catch { emit(Discovered(emptyList(), emptyList())) }
    }
      .collectAsRetainedState(Discovered(emptyList(), emptyList()))

    var step by rememberRetained { mutableIntStateOf(1) }
    var showTips by rememberRetained { mutableStateOf(false) }
    var hub by rememberRetained { mutableStateOf<FoundHub?>(null) }
    var details by rememberRetained { mutableStateOf<HubDetails?>(null) }
    var readFailed by rememberRetained { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var name by rememberRetained { mutableStateOf("Road bike") }
    var type by rememberRetained { mutableStateOf(BikeType.Road) }
    var meter by rememberRetained { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(step) {
      seconds = 0
      while (step == 1) {
        delay(1.seconds)
        seconds++
      }
    }

    // Wake → Found as soon as a hub is heard.
    LaunchedEffect(step, discovered.hubs.firstOrNull()) {
      if (step == 1)
        discovered.hubs.firstOrNull()?.let {
          hub = it
          step = 2
        }
    }

    // Read the hub's table on arrival at Found (and on retry).
    LaunchedEffect(step, hub, readFailed) {
      val target = hub ?: return@LaunchedEffect
      if (step != 2 || details != null || readFailed) return@LaunchedEffect
      reading = true
      details = discovery.readHub(target)
      readFailed = details == null
      reading = false
    }

    val eventSink: (AddBikeEvent) -> Unit = { event ->
      when (event) {
        AddBikeEvent.Close -> navigator.pop()
        AddBikeEvent.ToggleTips -> showTips = !showTips
        AddBikeEvent.Retry -> readFailed = false
        is AddBikeEvent.NameChanged -> name = event.name
        is AddBikeEvent.TypeSelected -> {
          // Keep a typed name; replace only the default for the old type.
          if (name == defaultName(type)) name = defaultName(event.type)
          type = event.type
        }
        is AddBikeEvent.MeterSelected -> meter = event.name
        AddBikeEvent.Skip -> {
          meter = null
          step = 5
        }
        AddBikeEvent.Continue ->
          when (step) {
            2 -> if (details != null) step = 3
            3 -> if (name.isNotBlank()) step = 4
            4 -> step = 5
            5 ->
              if (!saving) {
                val hubDetails = details
                if (hubDetails != null) {
                  saving = true
                  scope.launch {
                    val chosen = discovered.powerMeters.firstOrNull { it.name == meter }
                    val id = discovery.addBike(name, type, hubDetails, chosen)
                    navigator.resetRoot(BikeScreen(id.value))
                  }
                }
              }
          }
      }
    }

    val state =
      when (step) {
        1 -> AddBikeStep.Wake(seconds, capture.isReady, showTips)
        2 ->
          AddBikeStep.Found(
            hubSerial = hub?.serial ?: 0,
            reading = reading || (details == null && !readFailed),
            components = details?.rows().orEmpty(),
            failed = readFailed,
          )
        3 -> AddBikeStep.Name(name, type)
        4 ->
          AddBikeStep.PowerMeter(
            meters =
              discovered.powerMeters.map {
                MeterRow(
                  it.name,
                  if (it.rssi > -70) "Strong signal" else "Weak signal",
                  bars(it.rssi),
                )
              },
            selected = meter,
          )
        else -> AddBikeStep.KeepFresh(capture.isReady, saving)
      }
    return AddBikeUiState(state, eventSink)
  }

  private fun HubDetails.rows(): List<FoundRow> {
    val now = clock.now()
    return components.map { c ->
      val reading =
        BatteryReading(
          ComponentId(0),
          now,
          c.millivolts,
          c.percent,
          c.status,
          ReadMethod.Hub,
        )
      FoundRow(
        code = c.kind.code,
        name = c.kind.label,
        source = if (c.serial == hub.serial) "Hub · reported" else "Reported by the hub",
        value = reading.valueText(),
        tone = reading.status.tone,
      )
    }
  }

  private fun bars(rssi: Int) =
    when {
      rssi > -60 -> 4
      rssi > -70 -> 3
      rssi > -80 -> 2
      else -> 1
    }
}

internal fun defaultName(type: BikeType) =
  when (type) {
    BikeType.Road -> "Road bike"
    BikeType.Gravel -> "Gravel bike"
    BikeType.Mtb -> "Mountain bike"
    BikeType.Other -> "My bike"
  }
