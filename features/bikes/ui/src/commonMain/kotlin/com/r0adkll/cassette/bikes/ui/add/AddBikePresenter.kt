package com.r0adkll.cassette.bikes.ui.add

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.summary.code
import com.r0adkll.cassette.bikes.summary.tone
import com.r0adkll.cassette.capture.api.BikeDiscovery
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.Discovered
import com.r0adkll.cassette.capture.api.FoundHub
import com.r0adkll.cassette.capture.api.HubDetails
import com.r0adkll.cassette.common.screens.AddBikeScreen
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.core.BatteryReading
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.ComponentId
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.core.status
import com.r0adkll.cassette.core.valueText
import com.r0adkll.cassette.protocol.ComponentKind
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@CircuitInject(AddBikeScreen::class, AppScope::class)
@Inject
class AddBikePresenter(
  private val screen: AddBikeScreen,
  private val navigator: Navigator,
  private val discovery: BikeDiscovery,
  private val capture: CaptureController,
  private val bikes: BikeRepository,
  private val log: CaptureLog,
  private val clock: Clock,
) : Presenter<AddBikeUiState> {

  /** A hub heard on the found step, and its tables once read. */
  private data class System(
    val hub: FoundHub,
    val details: HubDetails? = null,
    val failed: Boolean = false,
    val on: Boolean = true,
  )

  @Composable
  override fun present(): AddBikeUiState {
    val scope = rememberCoroutineScope()
    val toBike = screen.bikeId?.let(::BikeId)
    val bikeName by
      remember(toBike) {
          bikes.observeBikes().map { list -> list.firstOrNull { it.id == toBike }?.name }
        }
        .collectAsRetainedState(null)
    // One scan for the whole flow: it finds the hubs, then the power meters nearby.
    val discovered by remember {
      discovery.discover().catch { emit(Discovered(emptyList(), emptyList())) }
    }
      .collectAsRetainedState(Discovered(emptyList(), emptyList()))

    var step by rememberRetained { mutableIntStateOf(1) }
    var showTips by rememberRetained { mutableStateOf(false) }
    var systems by rememberRetained { mutableStateOf(emptyList<System>()) }
    var firstHeard by rememberRetained { mutableStateOf<Instant?>(null) }
    var name by rememberRetained { mutableStateOf("Road bike") }
    var type by rememberRetained { mutableStateOf(BikeType.Road) }
    var meter by rememberRetained { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    // Hubs logged as heard too late to be offered, so each is logged once.
    var late by rememberRetained { mutableStateOf(emptySet<Long>()) }
    var opened by rememberRetained { mutableStateOf(false) }

    // What Found heard and decided goes to the capture log (CLAUDE.md, diagnostics stay).
    LaunchedEffect(Unit) {
      if (opened) return@LaunchedEffect
      opened = true
      scope.note(
        if (toBike == null) "Add bike opened" else "Add a system opened for bike ${toBike.value}"
      )
    }

    // Counted from when waking started, so turning the phone doesn't restart the count.
    val wakeStart = rememberRetained(step) { clock.now() }
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(step, wakeStart) {
      while (step == 1) {
        seconds = (clock.now() - wakeStart).inWholeSeconds.toInt()
        delay(1.seconds)
      }
    }

    // After the first hub, a few seconds more for another (decision 0028).
    var listenSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(step, firstHeard) {
      val since = firstHeard ?: return@LaunchedEffect
      while (step == 2) {
        listenSeconds = (clock.now() - since).inWholeSeconds.toInt()
        if (listenSeconds >= LISTEN_FOR.inWholeSeconds) break
        delay(1.seconds)
      }
    }
    val listening = step == 2 && listenSeconds < LISTEN_FOR.inWholeSeconds

    // Wake → Found as soon as a hub is heard.
    LaunchedEffect(step, discovered.hubs.firstOrNull()) {
      if (step == 1)
        discovered.hubs.firstOrNull()?.let {
          systems = listOf(System(it))
          firstHeard = clock.now()
          listenSeconds = 0
          step = 2
          scope.note("${it.describe()} heard after $seconds s of waking: the first system")
        }
    }

    // Every other hub heard while listening is a system too, and starts on: signal strength can't
    // tell a bike's second hub from another bike's (decision 0028).
    LaunchedEffect(step, discovered.hubs, listening) {
      if (step != 2 || !listening) return@LaunchedEffect
      val first = systems.firstOrNull()?.hub ?: return@LaunchedEffect
      val new = discovered.hubs.filter { hub -> systems.none { it.hub.serial == hub.serial } }
      if (new.isEmpty()) return@LaunchedEffect
      systems = systems + new.map { System(it) }
      val after = firstHeard?.let { (clock.now() - it).inWholeSeconds } ?: 0
      new.forEach {
        scope.note("${it.describe()} heard $after s after the first (${first.rssi} dBm): starts on")
      }
    }

    // A hub heard once listening has stopped isn't offered.
    LaunchedEffect(step, discovered.hubs, listening) {
      if (step < 2 || listening) return@LaunchedEffect
      val missed =
        discovered.hubs.filter { hub ->
          hub.serial !in late && systems.none { it.hub.serial == hub.serial }
        }
      if (missed.isEmpty()) return@LaunchedEffect
      late = late + missed.map { it.serial }
      missed.forEach { scope.note("${it.describe()} heard after listening stopped: not offered") }
    }

    // Reads each hub's tables in turn, as they arrive (and on retry).
    val toRead = systems.firstOrNull { it.details == null && !it.failed }?.hub
    LaunchedEffect(step, toRead) {
      val hub = toRead ?: return@LaunchedEffect
      if (step != 2) return@LaunchedEffect
      val details = discovery.readHub(hub)
      systems = systems.map {
        if (it.hub.serial == hub.serial) it.copy(details = details, failed = details == null)
        else it
      }
    }

    val eventSink: (AddBikeEvent) -> Unit = { event ->
      when (event) {
        AddBikeEvent.Close -> navigator.pop()
        AddBikeEvent.ToggleTips -> showTips = !showTips
        AddBikeEvent.Retry -> {
          scope.note("Try again: ${systems.filter { it.failed }.describe()}")
          systems = systems.map { it.copy(failed = false) }
        }
        is AddBikeEvent.ToggleSystem -> {
          systems = systems.map {
            if (it.hub.serial == event.hubSerial) it.copy(on = !it.on) else it
          }
          val on = systems.firstOrNull { it.hub.serial == event.hubSerial }?.on
          scope.note("Hub ${event.hubSerial} switched ${if (on == true) "on" else "off"}")
        }
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
        AddBikeEvent.Continue -> {
          val ready = systems.filter { it.on && it.details != null }
          val busy = systems.any { it.on && it.details == null && !it.failed }
          when (step) {
            2 ->
              if (ready.isNotEmpty() && !busy) {
                val still = if (listening) ", still listening" else ""
                scope.note("Continue after $listenSeconds s$still: ${systems.describe()}")
                if (toBike == null) step = 3
                else if (!saving) {
                  saving = true
                  scope.launch {
                    discovery.addSystems(toBike, ready.mapNotNull { it.details })
                    navigator.pop()
                  }
                }
              }
            3 -> if (name.isNotBlank()) step = 4
            4 -> step = 5
            5 ->
              if (!saving && ready.isNotEmpty()) {
                saving = true
                scope.launch {
                  val chosen = discovered.powerMeters.firstOrNull { it.name == meter }
                  val id = discovery.addBike(name, type, ready.mapNotNull { it.details }, chosen)
                  navigator.resetRoot(BikeScreen(id.value))
                }
              }
          }
        }
      }
    }

    val state =
      when (step) {
        1 ->
          AddBikeStep.Wake(
            seconds,
            capture.isReady,
            showTips,
            known =
              discovered.known
                .filter { it.bike == toBike }
                .map { KnownSystem(it.hub.kind.code, it.hub.kind.hubName, it.hub.serial) },
          )
        2 ->
          AddBikeStep.Found(
            systems = systems.map { it.row() },
            listening = listening,
            listenSeconds = listenSeconds,
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
    return AddBikeUiState(state, toBike = if (toBike != null) bikeName ?: "" else null, eventSink)
  }

  private fun System.row(): FoundSystem {
    val now = clock.now()
    return FoundSystem(
      hubSerial = hub.serial,
      hubName = hub.kind.hubName,
      components =
        details?.components.orEmpty().map { c ->
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
        },
      reading = details == null && !failed,
      failed = failed,
      on = on,
    )
  }

  /**
   * Logs on the presenter's scope, not in the effect that decided: those restart with every advert.
   */
  private fun CoroutineScope.note(detail: String) {
    val at = clock.now().toEpochMilliseconds()
    launch { log.append(CaptureEvent.AddBike(at, detail)) }
  }

  private fun FoundHub.describe() = "Hub $serial (${kind.hubName}) at $rssi dBm"

  private fun List<System>.describe() = joinToString { s ->
    val state =
      when {
        s.failed -> "couldn't be read"
        s.details == null -> "still reading"
        !s.on -> "off"
        else -> "on"
      }
    "${s.hub.serial} $state"
  }

  private fun bars(rssi: Int) =
    when {
      rssi > -60 -> 4
      rssi > -70 -> 3
      rssi > -80 -> 2
      else -> 1
    }

  private companion object {
    /** How long Found keeps listening after the first hub (decision 0028). */
    val LISTEN_FOR = 10.seconds
  }
}

/** The hub's kind as a system's name: "Dropper post", or "AXS" when it's one we don't know. */
private val ComponentKind.hubName: String
  get() = if (this == ComponentKind.UNKNOWN) "AXS" else label

internal fun defaultName(type: BikeType) =
  when (type) {
    BikeType.Road -> "Road bike"
    BikeType.Gravel -> "Gravel bike"
    BikeType.Mtb -> "Mountain bike"
    BikeType.Other -> "My bike"
  }
