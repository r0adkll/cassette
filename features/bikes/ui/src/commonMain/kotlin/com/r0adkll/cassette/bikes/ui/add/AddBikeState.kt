package com.r0adkll.cassette.bikes.ui.add

import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.core.BikeType
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState

/**
 * The add-bike flow from design iteration 5 (decision 0012): five steps on one screen. Adding a
 * system to a bike (decision 0028) is its first two, waking and found.
 */
data class AddBikeUiState(
  val step: AddBikeStep,
  /** The bike a system is being added to; null when adding a bike. */
  val toBike: String? = null,
  val eventSink: (AddBikeEvent) -> Unit,
) : CircuitUiState {
  val steps: Int
    get() = if (toBike != null) ADD_SYSTEM_STEPS else ADD_BIKE_STEPS
}

sealed interface AddBikeStep {
  val number: Int

  /**
   * Listening for a hub. [known] are hubs heard that are the bike's systems already (adding a
   * system).
   */
  data class Wake(
    val seconds: Int,
    val bluetoothReady: Boolean,
    val showTips: Boolean,
    val known: List<KnownSystem> = emptyList(),
  ) : AddBikeStep {
    override val number = 1
  }

  /**
   * One or more hubs were heard, each a system (decision 0028), and their tables are being read,
   * were read, or the read failed. While [listening], another hub can still join.
   */
  data class Found(
    val systems: List<FoundSystem>,
    val listening: Boolean = false,
    /** Seconds since the first hub was heard. */
    val listenSeconds: Int = 0,
  ) : AddBikeStep {
    override val number = 2

    /** Every system's read failed: nothing to save. */
    val failed: Boolean
      get() = systems.isNotEmpty() && systems.all { it.failed }

    /** A system that's on is still being read. */
    val reading: Boolean
      get() = systems.any { it.on && it.reading }

    /** Some system that's on didn't answer; the others can be saved without it. */
    val partlyFailed: Boolean
      get() = !failed && systems.any { it.on && it.failed }

    /** The systems the bike will get, read and switched on. */
    val saving: List<FoundSystem>
      get() = systems.filter { it.on && !it.reading && !it.failed }

    /** Switches show once there's a choice. */
    val choosing: Boolean
      get() = systems.size > 1
  }

  data class Name(val name: String, val type: BikeType) : AddBikeStep {
    override val number = 3
  }

  data class PowerMeter(val meters: List<MeterRow>, val selected: String?) : AddBikeStep {
    override val number = 4
  }

  data class KeepFresh(val bluetoothReady: Boolean, val saving: Boolean) : AddBikeStep {
    override val number = 5
  }
}

/** A hub that answered and the components its table lists (decision 0028). */
data class FoundSystem(
  val hubSerial: Long,
  /** The hub's kind, "Dropper post"; the system is named after it. */
  val hubName: String,
  val components: List<FoundRow>,
  val reading: Boolean = false,
  val failed: Boolean = false,
  /** Switched on, it joins the bike; off, its components stay someone else's. */
  val on: Boolean = true,
  /** Heard much more weakly than the first hub, so it started off. */
  val faint: Boolean = false,
)

/** A hub that is one of the bike's systems already. */
data class KnownSystem(val code: String, val hubName: String, val hubSerial: Long)

data class FoundRow(
  val code: String,
  val name: String,
  val source: String,
  val value: String,
  val tone: StatusTone,
)

data class MeterRow(val name: String, val detail: String, val bars: Int)

sealed interface AddBikeEvent : CircuitUiEvent {
  data object Close : AddBikeEvent

  data object ToggleTips : AddBikeEvent

  data object Continue : AddBikeEvent

  /** Reads every system that didn't answer again. */
  data object Retry : AddBikeEvent

  /** Switches a system on or off (decision 0028). */
  data class ToggleSystem(val hubSerial: Long) : AddBikeEvent

  data class NameChanged(val name: String) : AddBikeEvent

  data class TypeSelected(val type: BikeType) : AddBikeEvent

  data class MeterSelected(val name: String?) : AddBikeEvent

  data object Skip : AddBikeEvent
}

const val ADD_BIKE_STEPS = 5

/** Adding a system: waking and found. */
const val ADD_SYSTEM_STEPS = 2
