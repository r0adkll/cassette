package com.r0adkll.cassette.settings.ui

import app.cash.turbine.ReceiveTurbine
import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.api.ComponentDetail
import com.r0adkll.cassette.bikes.api.ComponentWithReading
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.common.screens.AddBikeScreen
import com.r0adkll.cassette.common.screens.BikeSettingsScreen
import com.r0adkll.cassette.common.screens.SettingsCategory
import com.r0adkll.cassette.common.screens.SettingsScreen
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.Component
import com.r0adkll.cassette.core.ComponentId
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.settings.api.ThemeMode
import com.r0adkll.cassette.settings.test.InMemorySettings
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest

/** The garage's "last bike seen" switch and a bike's own page (iteration 13). */
class BikeSettingsTest {
  private val now = Instant.fromEpochMilliseconds(1_792_200_000_000)
  private val clock =
    object : Clock {
      override fun now() = this@BikeSettingsTest.now
    }
  private val settings = InMemorySettings()

  private val road = Bike(BikeId(1), "Road bike", BikeType.Road, null, 1, now, now)
  private val gravel = Bike(BikeId(2), "Gravel bike", BikeType.Gravel, null, 2, now, now)

  /** The tester's MTB (decision 0028): added with the dropper's system, then the rear shock's. */
  private val mtb = Bike(BikeId(3), "Mountain bike", BikeType.Mtb, null, DROPPER, now, now)
  private val mtbComponents =
    listOf(
      component(1, ComponentKind.REAR_DERAILLEUR, 1224168767, SHOCK, now + 1.minutes),
      component(2, ComponentKind.FORK, 1151498210, DROPPER, now),
      component(3, ComponentKind.REAR_SHOCK, SHOCK, SHOCK, now + 1.minutes),
      component(4, ComponentKind.DROPPER_POST, DROPPER, DROPPER, now),
      component(5, ComponentKind.POWER_METER, null, null, now),
    )

  /** Bikes in memory; updates and deletes change them. */
  private val repository =
    object : BikeRepository {
      val bikes = MutableStateFlow(listOf(road, gravel, mtb))
      val deleted = mutableListOf<BikeId>()
      val removedSystems = mutableListOf<Long>()

      override fun observeBikes(): Flow<List<Bike>> = bikes

      override fun observeOverview(id: BikeId?): Flow<BikeOverview?> = bikes.map { list ->
        list
          .firstOrNull { it.id == id }
          ?.let { bike ->
            val components = if (bike == mtb) mtbComponents else emptyList()
            BikeOverview(bike, list.size, components.map { ComponentWithReading(it, null) })
          }
      }

      override fun observeComponent(id: ComponentId, since: Instant) = emptyFlow<ComponentDetail?>()

      override suspend fun updateBike(id: BikeId, name: String, type: BikeType) {
        bikes.value = bikes.value.map { if (it.id == id) it.copy(name = name, type = type) else it }
      }

      override suspend fun deleteBike(id: BikeId) {
        deleted += id
        bikes.value = bikes.value.filter { it.id != id }
      }

      override suspend fun removeSystem(id: BikeId, hubSerial: Long) {
        removedSystems += hubSerial
      }
    }

  private val capture =
    object : CaptureController {
      override val isReady = true

      override fun refresh(): Flow<RefreshProgress> = flowOf()

      override suspend fun ensureBackgroundCapture(reason: String) = Unit
    }

  @Test
  fun turningOffTheLastBikeSeenPinsTheMostRecentBike() = runTest {
    val presenter =
      SettingsPresenter(
        SettingsScreen(SettingsCategory.Bikes),
        FakeNavigator(SettingsScreen()),
        settings,
        repository,
        capture,
        clock,
      )
    presenter.test {
      val loaded = awaitUntil { it.garage.size == 3 }
      assertEquals(null, loaded.pinned)

      loaded.eventSink(SettingsEvent.SetFollowLastSeen(false))
      val pinned = awaitUntil { it.pinned != null }
      assertEquals(BikeId(1), pinned.pinned)
      assertTrue(pinned.garage.first().opensFirst)

      pinned.eventSink(SettingsEvent.SetFollowLastSeen(true))
      awaitUntil { it.pinned == null }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun appearanceStoresTheThemeChoice() = runTest {
    val presenter =
      SettingsPresenter(
        SettingsScreen(SettingsCategory.Appearance),
        FakeNavigator(SettingsScreen()),
        settings,
        repository,
        capture,
        clock,
      )
    presenter.test {
      val loaded = awaitUntil { it.themeMode == ThemeMode.System }
      loaded.eventSink(SettingsEvent.SetThemeMode(ThemeMode.Dark))
      awaitUntil { it.themeMode == ThemeMode.Dark }
      assertEquals(ThemeMode.Dark, settings.themeMode.value)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun aBikesPageRenamesSetsOpensFirstAndRemovesOnTheSecondTap() = runTest {
    val navigator = FakeNavigator(BikeSettingsScreen(2))
    BikeSettingsPresenter(BikeSettingsScreen(2), navigator, repository, settings).test {
      val loaded = awaitUntil { it.bike != null }
      assertEquals("Gravel bike", loaded.bike?.name)

      // A blank name is ignored; a real one is saved.
      loaded.eventSink(BikeSettingsEvent.Rename("  "))
      loaded.eventSink(BikeSettingsEvent.Rename("Commuter"))
      awaitUntil { it.bike?.name == "Commuter" }

      loaded.eventSink(BikeSettingsEvent.SetOpensFirst(true))
      val first = awaitUntil { it.bike?.opensFirst == true }
      assertEquals(BikeId(2), settings.pinnedBike.value)

      first.eventSink(BikeSettingsEvent.Remove)
      val asking = awaitUntil { it.confirmRemove }
      assertTrue(repository.deleted.isEmpty())

      asking.eventSink(BikeSettingsEvent.Remove)
      navigator.awaitPop()
      assertEquals(listOf(BikeId(2)), repository.deleted)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun anUnansweredRemoveResetsAfterTheTimeout() = runTest {
    BikeSettingsPresenter(
        BikeSettingsScreen(2),
        FakeNavigator(BikeSettingsScreen(2)),
        repository,
        settings,
      )
      .test {
        val loaded = awaitUntil { it.bike != null }
        loaded.eventSink(BikeSettingsEvent.Remove)
        awaitUntil { it.confirmRemove }
        val armedAt = testScheduler.currentTime
        awaitUntil { !it.confirmRemove }
        assertEquals(RemoveTimeout.inWholeMilliseconds, testScheduler.currentTime - armedAt)
        assertTrue(repository.deleted.isEmpty())
        cancelAndIgnoreRemainingEvents()
      }
  }

  @Test
  fun aBikesComponentsAreGroupedUnderTheirSystems() = runTest {
    val navigator = FakeNavigator(BikeSettingsScreen(3))
    BikeSettingsPresenter(BikeSettingsScreen(3), navigator, repository, settings).test {
      val loaded = awaitUntil { it.bike != null }
      val bike = loaded.bike!!
      // The one it was added with first, each hub first; the power meter on its own.
      assertEquals(
        listOf("Dropper post system", "Rear shock system"),
        bike.systems.map { it.name },
      )
      assertEquals(listOf("DP", "FK"), bike.systems[0].components.map { it.code })
      assertEquals(listOf("SH", "RD"), bike.systems[1].components.map { it.code })
      assertEquals(listOf("PM"), bike.others.map { it.code })

      loaded.eventSink(BikeSettingsEvent.RemoveSystem(SHOCK))
      val asking = awaitUntil { it.confirmRemoveSystem == SHOCK }
      assertTrue(repository.removedSystems.isEmpty())
      asking.eventSink(BikeSettingsEvent.RemoveSystem(SHOCK))
      awaitUntil { it.confirmRemoveSystem == null }
      assertEquals(listOf(SHOCK), repository.removedSystems)

      loaded.eventSink(BikeSettingsEvent.AddSystem)
      assertEquals(AddBikeScreen(3), navigator.awaitNextScreen())
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun component(id: Long, kind: ComponentKind, serial: Long?, hub: Long?, added: Instant) =
    Component(
      ComponentId(id),
      BikeId(3),
      serial?.toString() ?: "Quarq AGD60899",
      serial,
      kind,
      null,
      null,
      null,
      null,
      added,
      now,
      null,
      hubSerial = hub,
    )

  private suspend fun <T> ReceiveTurbine<T>.awaitUntil(until: (T) -> Boolean): T {
    while (true) {
      val state = awaitItem()
      if (until(state)) return state
    }
  }

  private companion object {
    const val DROPPER = 1121114092L
    const val SHOCK = 1013571428L
  }
}
