package com.r0adkll.cassette.settings.ui

import app.cash.turbine.ReceiveTurbine
import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.api.ComponentDetail
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.common.screens.BikeSettingsScreen
import com.r0adkll.cassette.common.screens.SettingsCategory
import com.r0adkll.cassette.common.screens.SettingsScreen
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.ComponentId
import com.r0adkll.cassette.settings.test.InMemorySettings
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
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

  /** Bikes in memory; updates and deletes change them. */
  private val repository =
    object : BikeRepository {
      val bikes = MutableStateFlow(listOf(road, gravel))
      val deleted = mutableListOf<BikeId>()

      override fun observeBikes(): Flow<List<Bike>> = bikes

      override fun observeOverview(id: BikeId?): Flow<BikeOverview?> = bikes.map { list ->
        list.firstOrNull { it.id == id }?.let { BikeOverview(it, list.size, emptyList()) }
      }

      override fun observeComponent(id: ComponentId, since: Instant) = emptyFlow<ComponentDetail?>()

      override suspend fun updateBike(id: BikeId, name: String, type: BikeType) {
        bikes.value = bikes.value.map { if (it.id == id) it.copy(name = name, type = type) else it }
      }

      override suspend fun deleteBike(id: BikeId) {
        deleted += id
        bikes.value = bikes.value.filter { it.id != id }
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
      val loaded = awaitUntil { it.garage.size == 2 }
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

  private suspend fun <T> ReceiveTurbine<T>.awaitUntil(until: (T) -> Boolean): T {
    while (true) {
      val state = awaitItem()
      if (until(state)) return state
    }
  }
}
