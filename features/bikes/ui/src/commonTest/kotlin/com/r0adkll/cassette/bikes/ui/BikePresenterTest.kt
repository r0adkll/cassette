package com.r0adkll.cassette.bikes.ui

import app.cash.turbine.ReceiveTurbine
import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.api.ComponentDetail
import com.r0adkll.cassette.bikes.api.HomeSurfaces
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.common.screens.ComponentScreen
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.ComponentId
import com.r0adkll.cassette.settings.api.Tip
import com.r0adkll.cassette.settings.test.InMemorySettings
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class BikePresenterTest {
  private val now = Instant.fromEpochMilliseconds(1_792_200_000_000)
  private val clock =
    object : Clock {
      override fun now() = this@BikePresenterTest.now
    }
  private val overviews = MutableSharedFlow<BikeOverview?>(replay = 1)
  private val repository =
    object : BikeRepository {
      override fun observeBikes(): Flow<List<Bike>> = emptyFlow()

      override fun observeOverview(id: BikeId?): Flow<BikeOverview?> = overviews

      override fun observeComponent(id: ComponentId, since: Instant) = emptyFlow<ComponentDetail?>()

      override suspend fun deleteBike(id: BikeId) = Unit
    }

  private val capture =
    object : CaptureController {
      override val isReady = true

      override fun refresh(): Flow<RefreshProgress> =
        flowOf(RefreshProgress.Scanning(59, 0), RefreshProgress.Done(5, true))

      override suspend fun ensureBackgroundCapture(reason: String) = Unit
    }

  private val settings = InMemorySettings()
  private val requested = mutableListOf<String>()
  private val surfaces =
    object : HomeSurfaces {
      var tileSupported = true

      override fun canOfferWidget() = true

      override fun requestWidget() {
        requested += "widget"
      }

      override fun canOfferTile() = tileSupported

      override fun requestTile() {
        requested += "tile"
      }
    }

  private fun presenter(navigator: FakeNavigator) =
    BikePresenter(BikeScreen(), navigator, repository, capture, clock, settings, surfaces)

  private val roadBike =
    BikeOverview(Bike(BikeId(1), "Road bike", BikeType.Road, null, 1, now, now), 2, emptyList())

  @Test
  fun loadingThenEmptyThenLoaded() = runTest {
    val navigator = FakeNavigator(BikeScreen())
    val presenter = presenter(navigator)

    presenter.test {
      assertIs<BikeUiState.Loading>(awaitItem())

      overviews.emit(null)
      assertIs<BikeUiState.Empty>(awaitItem())

      overviews.emit(roadBike)
      val loaded = awaitLoaded()
      assertEquals("Road bike", loaded.name)
      assertEquals("1 OF 2", loaded.position)

      loaded.eventSink(BikeEvent.OpenComponent(7))
      assertEquals(ComponentScreen(7), navigator.awaitNextScreen())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun tipsOfferTheWidgetThenTheTileUntilDone() = runTest {
    overviews.emit(roadBike)
    presenter(FakeNavigator(BikeScreen())).test {
      val widget = awaitLoaded { it.tip != null }
      assertEquals(Tip.Widget, widget.tip)

      widget.eventSink(BikeEvent.AcceptTip(Tip.Widget))
      val tile = awaitLoaded { it.tip == Tip.QuickSettingsTile }
      assertEquals(listOf("widget"), requested)

      tile.eventSink(BikeEvent.DismissTip(Tip.QuickSettingsTile))
      awaitLoaded { it.tip == null }
      assertEquals(listOf("widget"), requested)
      cancelAndIgnoreRemainingEvents()
    }
    assertEquals(setOf(Tip.Widget, Tip.QuickSettingsTile), settings.dismissedTips.value)
  }

  @Test
  fun noTileTipWhereTheSystemCantAddIt() = runTest {
    surfaces.tileSupported = false
    settings.dismissTip(Tip.Widget)
    overviews.emit(roadBike)
    presenter(FakeNavigator(BikeScreen())).test {
      awaitLoaded()
      testScheduler.runCurrent()
      val states =
        cancelAndConsumeRemainingEvents()
          .filterIsInstance<app.cash.turbine.Event.Item<BikeUiState>>()
      assertTrue(
        states.map { it.value }.filterIsInstance<BikeUiState.Loaded>().all { it.tip == null }
      )
    }
  }

  private suspend fun ReceiveTurbine<BikeUiState>.awaitLoaded(
    until: (BikeUiState.Loaded) -> Boolean = { true }
  ): BikeUiState.Loaded {
    while (true) {
      val state = awaitItem()
      if (state is BikeUiState.Loaded && until(state)) return state
    }
  }
}
