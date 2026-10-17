package com.r0adkll.cassette.bikes.ui

import com.r0adkll.cassette.bikes.ui.add.AddBikeEvent
import com.r0adkll.cassette.bikes.ui.add.AddBikePresenter
import com.r0adkll.cassette.bikes.ui.add.AddBikeStep
import com.r0adkll.cassette.bikes.ui.add.AddBikeUiState
import com.r0adkll.cassette.capture.api.BikeDiscovery
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.Discovered
import com.r0adkll.cassette.capture.api.FoundHub
import com.r0adkll.cassette.capture.api.FoundPowerMeter
import com.r0adkll.cassette.capture.api.HubDetails
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.common.screens.AddBikeScreen
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.protocol.AdvertBattery
import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.protocol.SystemComponent
import com.r0adkll.cassette.protocol.SystemTable
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

class AddBikePresenterTest {
    private val hub =
        FoundHub(
            1405188718,
            "AA:BB",
            ComponentKind.REAR_DERAILLEUR,
            1066,
            -55,
            AdvertBattery(7_870, 78, BatteryStatus.OK),
            "2.55.8",
        )
    private val table =
        SystemTable(
            1405188718,
            listOf(
                SystemComponent(0x80, 1002, 1315137227, BatteryStatus.OK, 8_087, 0, 0, 0),
                SystemComponent(0x81, 1066, 1405188718, BatteryStatus.OK, 7_884, 0, 0, 12_324),
            ),
        )
    private val found = MutableStateFlow(Discovered(emptyList(), emptyList()))
    private var added: Triple<String, BikeType, FoundPowerMeter?>? = null
    private val discovery =
        object : BikeDiscovery {
            override fun discover(): Flow<Discovered> = found

            override suspend fun readHub(hub: FoundHub) = HubDetails(hub, table, null)

            override suspend fun addBike(
                name: String,
                type: BikeType,
                hub: HubDetails,
                powerMeter: FoundPowerMeter?,
            ): BikeId {
                added = Triple(name, type, powerMeter)
                return BikeId(42)
            }
        }
    private val capture =
        object : CaptureController {
            override val isReady = true

            override fun refresh(): Flow<RefreshProgress> = emptyFlow()

            override suspend fun ensureBackgroundCapture(reason: String) = Unit
        }
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(0)
        }

    @Test
    fun wakeFindNamePickFinish() = runTest {
        val navigator = FakeNavigator(AddBikeScreen)
        AddBikePresenter(navigator, discovery, capture, clock).test {
            assertIs<AddBikeStep.Wake>(awaitItem().step)

            found.value =
                Discovered(listOf(hub), listOf(FoundPowerMeter("Quarq AGN12387", "CC:DD", 7, -58)))
            val ready = awaitStep<AddBikeStep.Found> { !it.reading }
            assertEquals(
                listOf("RD", "FD"),
                ready.step.let { it as AddBikeStep.Found }.components.map { it.code },
            )
            assertEquals(
                listOf("78%", "≈97%"),
                (ready.step as AddBikeStep.Found).components.map { it.value },
            )

            ready.eventSink(AddBikeEvent.Continue)
            val name = awaitStep<AddBikeStep.Name>()
            name.eventSink(AddBikeEvent.TypeSelected(BikeType.Gravel))
            val gravel = awaitStep<AddBikeStep.Name> { it.type == BikeType.Gravel }
            assertEquals("Gravel bike", (gravel.step as AddBikeStep.Name).name)
            gravel.eventSink(AddBikeEvent.Continue)

            val meters = awaitStep<AddBikeStep.PowerMeter>()
            meters.eventSink(AddBikeEvent.MeterSelected("Quarq AGN12387"))
            awaitStep<AddBikeStep.PowerMeter> { it.selected != null }
                .eventSink(AddBikeEvent.Continue)

            awaitStep<AddBikeStep.KeepFresh>().eventSink(AddBikeEvent.Continue)
            assertEquals(
                listOf(BikeScreen(42)),
                navigator.awaitResetRoot().newRoot.let { listOf(it) },
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("Gravel bike", added?.first)
        assertEquals("Quarq AGN12387", added?.third?.name)
    }

    private suspend inline fun <
        reified T : AddBikeStep
    > com.slack.circuit.test.CircuitReceiveTurbine<AddBikeUiState>.awaitStep(
        crossinline matches: (T) -> Boolean = { true }
    ): AddBikeUiState {
        while (true) {
            val item = awaitItem()
            val step = item.step
            if (step is T && matches(step)) return item
        }
    }
}
