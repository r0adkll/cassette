package com.r0adkll.cassette.bikes.ui.add

import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.core.BikeType
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState

/** The add-bike flow from design iteration 5 (decision 0012): five steps on one screen. */
data class AddBikeUiState(val step: AddBikeStep, val eventSink: (AddBikeEvent) -> Unit) :
    CircuitUiState

sealed interface AddBikeStep {
    val number: Int

    /** Listening for a hub. */
    data class Wake(val seconds: Int, val bluetoothReady: Boolean, val showTips: Boolean) :
        AddBikeStep {
        override val number = 1
    }

    /** A hub was heard; its table is being read, was read, or the read failed. */
    data class Found(
        val hubSerial: Long,
        val reading: Boolean,
        val components: List<FoundRow>,
        val failed: Boolean,
    ) : AddBikeStep {
        override val number = 2
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

    data object Retry : AddBikeEvent

    data class NameChanged(val name: String) : AddBikeEvent

    data class TypeSelected(val type: BikeType) : AddBikeEvent

    data class MeterSelected(val name: String?) : AddBikeEvent

    data object Skip : AddBikeEvent
}

const val ADD_BIKE_STEPS = 5
