package com.r0adkll.cassette.bikes.ui

import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.api.ComponentDetail
import com.r0adkll.cassette.bikes.ui.add.ADD_SYSTEM_STEPS
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
import com.r0adkll.cassette.capture.api.KnownHub
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.common.screens.AddBikeScreen
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.ComponentId
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
import kotlinx.coroutines.flow.flowOf
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
  private var addedHubs: List<Long> = emptyList()
  private var addedTo: BikeId? = null
  private val discovery =
    object : BikeDiscovery {
      override fun discover(): Flow<Discovered> = found

      override suspend fun readHub(hub: FoundHub) =
        HubDetails(hub, tables[hub.serial] ?: table, null)

      override suspend fun addBike(
        name: String,
        type: BikeType,
        hubs: List<HubDetails>,
        powerMeter: FoundPowerMeter?,
      ): BikeId {
        added = Triple(name, type, powerMeter)
        addedHubs = hubs.map { it.hub.serial }
        return BikeId(42)
      }

      override suspend fun addSystems(bike: BikeId, hubs: List<HubDetails>) {
        addedTo = bike
        addedHubs = hubs.map { it.hub.serial }
      }
    }

  // The tester's MTB (decision 0028): the dropper's system and the rear shock's.
  private val dropper = hubOf(DROPPER, ComponentKind.DROPPER_POST, rssi = -56)
  private val shock = hubOf(SHOCK, ComponentKind.REAR_SHOCK, rssi = -58)
  private val tables =
    mapOf(
      DROPPER to
        SystemTable(
          DROPPER,
          listOf(
            SystemComponent(0x84, 1019, DROPPER, BatteryStatus.OK, 7_579, 0, 0, 0),
            SystemComponent(0x82, 1086, 1151498210, BatteryStatus.OK, 7_718, 0, 0, 0),
            SystemComponent(115, 1073, 1346282619, BatteryStatus.OK, 2_701, 0, 0, 0),
          ),
        ),
      SHOCK to
        SystemTable(
          SHOCK,
          listOf(
            SystemComponent(0x83, 1085, SHOCK, BatteryStatus.OK, 8_096, 0, 0, 0),
            SystemComponent(0x81, 1013, 1224168767, BatteryStatus.OK, 8_249, 0, 0, 0),
            SystemComponent(4, 1016, 1224155901, BatteryStatus.OK, 2_996, 0, 0, 0),
          ),
        ),
    )
  private val bikes =
    object : BikeRepository {
      override fun observeBikes(): Flow<List<Bike>> =
        flowOf(
          listOf(
            Bike(
              BikeId(7),
              "Mountain bike",
              BikeType.Mtb,
              null,
              DROPPER,
              Instant.fromEpochMilliseconds(0),
              null,
            )
          )
        )

      override fun observeOverview(id: BikeId?): Flow<BikeOverview?> = emptyFlow()

      override fun observeComponent(id: ComponentId, since: Instant) = emptyFlow<ComponentDetail?>()

      override suspend fun updateBike(id: BikeId, name: String, type: BikeType) = Unit

      override suspend fun deleteBike(id: BikeId) = Unit

      override suspend fun removeSystem(id: BikeId, hubSerial: Long) = Unit
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
    val navigator = FakeNavigator(AddBikeScreen())
    AddBikePresenter(AddBikeScreen(), navigator, discovery, capture, bikes, clock).test {
      assertIs<AddBikeStep.Wake>(awaitItem().step)

      found.value =
        Discovered(listOf(hub), listOf(FoundPowerMeter("Quarq AGN12387", "CC:DD", 7, -58)))
      val ready = awaitStep<AddBikeStep.Found> { !it.reading }
      val system = (ready.step as AddBikeStep.Found).systems.single()
      assertEquals("Rear derailleur", system.hubName)
      assertEquals(listOf("RD", "FD"), system.components.map { it.code })
      assertEquals(listOf("78%", "≈97%"), system.components.map { it.value })

      ready.eventSink(AddBikeEvent.Continue)
      val name = awaitStep<AddBikeStep.Name>()
      name.eventSink(AddBikeEvent.TypeSelected(BikeType.Gravel))
      val gravel = awaitStep<AddBikeStep.Name> { it.type == BikeType.Gravel }
      assertEquals("Gravel bike", (gravel.step as AddBikeStep.Name).name)
      gravel.eventSink(AddBikeEvent.Continue)

      val meters = awaitStep<AddBikeStep.PowerMeter>()
      meters.eventSink(AddBikeEvent.MeterSelected("Quarq AGN12387"))
      awaitStep<AddBikeStep.PowerMeter> { it.selected != null }.eventSink(AddBikeEvent.Continue)

      awaitStep<AddBikeStep.KeepFresh>().eventSink(AddBikeEvent.Continue)
      assertEquals(
        listOf(BikeScreen(42)),
        navigator.awaitResetRoot().newRoot.let { listOf(it) },
      )
      cancelAndIgnoreRemainingEvents()
    }
    assertEquals("Gravel bike", added?.first)
    assertEquals("Quarq AGN12387", added?.third?.name)
    assertEquals(listOf(1405188718L), addedHubs)
  }

  @Test
  fun everyHubThatAnswersIsASystemAndAFaintOneStartsOff() = runTest {
    val navigator = FakeNavigator(AddBikeScreen())
    AddBikePresenter(AddBikeScreen(), navigator, discovery, capture, bikes, clock).test {
      found.value = Discovered(listOf(dropper), emptyList())
      awaitStep<AddBikeStep.Found> { it.listening }
      // The shock, and a road bike's hub far off in the same garage.
      found.value = Discovered(listOf(dropper, shock, hub.copy(rssi = -90)), emptyList())
      val all = awaitStep<AddBikeStep.Found> { it.systems.size == 3 && !it.reading }
      val systems = (all.step as AddBikeStep.Found).systems
      assertEquals(
        listOf("Dropper post", "Rear shock", "Rear derailleur"),
        systems.map { it.hubName },
      )
      assertEquals(listOf(true, true, false), systems.map { it.on })
      assertEquals(listOf(false, false, true), systems.map { it.faint })
      assertEquals(listOf("SH", "RD", "R"), systems[1].components.map { it.code })

      // Not this bike's after all: only the dropper's system is saved.
      all.eventSink(AddBikeEvent.ToggleSystem(SHOCK))
      awaitStep<AddBikeStep.Found> { f -> f.systems.none { it.hubSerial == SHOCK && it.on } }
        .eventSink(AddBikeEvent.Continue)
      awaitStep<AddBikeStep.Name>().eventSink(AddBikeEvent.Continue)
      awaitStep<AddBikeStep.PowerMeter>().eventSink(AddBikeEvent.Skip)
      awaitStep<AddBikeStep.KeepFresh>().eventSink(AddBikeEvent.Continue)
      navigator.awaitResetRoot()
      cancelAndIgnoreRemainingEvents()
    }
    assertEquals(listOf(DROPPER), addedHubs)
  }

  @Test
  fun aSystemIsAddedToABike() = runTest {
    val navigator = FakeNavigator(AddBikeScreen(7))
    AddBikePresenter(AddBikeScreen(7), navigator, discovery, capture, bikes, clock).test {
      // The dropper is the bike's already: heard, but not offered.
      found.value =
        Discovered(emptyList(), emptyList(), known = listOf(KnownHub(dropper, BikeId(7))))
      val wake = awaitStep<AddBikeStep.Wake> { it.known.isNotEmpty() }
      assertEquals(ADD_SYSTEM_STEPS, wake.steps)
      assertEquals("Dropper post", (wake.step as AddBikeStep.Wake).known.single().hubName)

      found.value =
        Discovered(listOf(shock), emptyList(), known = listOf(KnownHub(dropper, BikeId(7))))
      val ready = awaitStep<AddBikeStep.Found> { !it.reading }
      assertEquals("Mountain bike", ready.toBike)
      ready.eventSink(AddBikeEvent.Continue)
      navigator.awaitPop()
      cancelAndIgnoreRemainingEvents()
    }
    assertEquals(BikeId(7), addedTo)
    assertEquals(listOf(SHOCK), addedHubs)
  }

  private fun hubOf(serial: Long, kind: ComponentKind, rssi: Int) =
    FoundHub(serial, "AA:$serial", kind, null, rssi, null, null)

  private suspend inline fun <reified T : AddBikeStep> com.slack.circuit.test.CircuitReceiveTurbine<
    AddBikeUiState
  >
    .awaitStep(crossinline matches: (T) -> Boolean = { true }): AddBikeUiState {
    while (true) {
      val item = awaitItem()
      val step = item.step
      if (step is T && matches(step)) return item
    }
  }

  private companion object {
    const val DROPPER = 1121114092L
    const val SHOCK = 1013571428L
  }
}
