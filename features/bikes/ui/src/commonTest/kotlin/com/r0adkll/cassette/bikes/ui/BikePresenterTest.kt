package com.r0adkll.cassette.bikes.ui

import app.cash.turbine.ReceiveTurbine
import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.api.ComponentDetail
import com.r0adkll.cassette.bikes.api.ComponentWithReading
import com.r0adkll.cassette.bikes.api.HomeSurfaces
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.common.screens.ComponentScreen
import com.r0adkll.cassette.core.BatteryReading
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.Component
import com.r0adkll.cassette.core.ComponentId
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.protocol.ComponentKind
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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

      override suspend fun updateBike(id: BikeId, name: String, type: BikeType) = Unit

      override suspend fun deleteBike(id: BikeId) = Unit
    }

  private val capture =
    object : CaptureController {
      override val isReady = true
      var progress: Flow<RefreshProgress> =
        flowOf(RefreshProgress.Scanning(59, 0), RefreshProgress.Done(5, true))
      var more: Flow<Unit> = emptyFlow()

      override fun refresh(): Flow<RefreshProgress> = progress

      override fun refresh(more: Flow<Unit>): Flow<RefreshProgress> {
        this.more = more
        return progress
      }

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

  @Test
  fun theSheetOpensSwitchesAndCloses() = runTest {
    val withComponents =
      roadBike.copy(
        components =
          listOf(
            component(7, ComponentKind.REAR_DERAILLEUR),
            component(8, ComponentKind.FRONT_DERAILLEUR),
          )
      )
    overviews.emit(withComponents)
    presenter(FakeNavigator(BikeScreen())).test {
      val closed = awaitLoaded()
      assertEquals(null, closed.sheet)

      closed.eventSink(BikeEvent.ShowComponent(7))
      val open = awaitLoaded { it.sheet == 7L }

      open.eventSink(BikeEvent.ShowComponent(8))
      val switched = awaitLoaded { it.sheet == 8L }

      switched.eventSink(BikeEvent.CloseSheet)
      awaitLoaded { it.sheet == null }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun theSheetClosesWhenItsComponentGoes() = runTest {
    overviews.emit(roadBike.copy(components = listOf(component(7, ComponentKind.REAR_DERAILLEUR))))
    presenter(FakeNavigator(BikeScreen())).test {
      awaitLoaded().eventSink(BikeEvent.ShowComponent(7))
      awaitLoaded { it.sheet == 7L }

      overviews.emit(roadBike)
      awaitLoaded { it.components.isEmpty() && it.sheet == null }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun switchingBikesClosesTheSheet() = runTest {
    overviews.emit(roadBike.copy(components = listOf(component(7, ComponentKind.REAR_DERAILLEUR))))
    presenter(FakeNavigator(BikeScreen())).test {
      awaitLoaded().eventSink(BikeEvent.ShowComponent(7))
      val open = awaitLoaded { it.sheet == 7L }

      open.eventSink(BikeEvent.SelectBike(2))
      awaitLoaded { it.sheet == null }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun aRefreshMarksWhatAnsweredAndStaysDoneUntilFinished() = runTest {
    // The rear derailleur was read during the refresh; the front derailleur wasn't.
    val read = BatteryReading(ComponentId(7), now, null, 78, null, ReadMethod.Advert)
    overviews.emit(
      roadBike.copy(
        components =
          listOf(
            component(7, ComponentKind.REAR_DERAILLEUR).copy(latest = read),
            component(8, ComponentKind.FRONT_DERAILLEUR),
          )
      )
    )
    presenter(FakeNavigator(BikeScreen())).test {
      val idle = awaitLoaded()
      assertEquals(null, idle.heard)

      idle.eventSink(BikeEvent.Refresh)
      val done = awaitLoaded { it.refresh is RefreshButton.Done }
      assertEquals(setOf(7L), done.heard)

      done.eventSink(BikeEvent.FinishRefresh)
      awaitLoaded { it.refresh == RefreshButton.Idle && it.heard == null }
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun stopEndsTheScanAndAddSixtySecondsAsksForMore() = runTest {
    val progress = MutableSharedFlow<RefreshProgress>()
    capture.progress = progress
    overviews.emit(roadBike)
    presenter(FakeNavigator(BikeScreen())).test {
      awaitLoaded().eventSink(BikeEvent.Refresh)
      progress.subscriptionCount.first { it == 1 }
      val asked = mutableListOf<Unit>()
      backgroundScope.launch { capture.more.collect { asked += it } }
      testScheduler.runCurrent()
      progress.emit(RefreshProgress.Scanning(50, 0, 60))
      val scanning = awaitLoaded { it.refresh is RefreshButton.Scanning }

      scanning.eventSink(BikeEvent.MoreTime)
      testScheduler.runCurrent()
      assertEquals(1, asked.size)

      scanning.eventSink(BikeEvent.StopRefresh)
      awaitLoaded { it.refresh == RefreshButton.Idle && it.heard == null }
      // Stopping cancels the collection, which stops the scan.
      progress.subscriptionCount.first { it == 0 }
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun component(id: Long, kind: ComponentKind) =
    ComponentWithReading(
      Component(
        id = ComponentId(id),
        bikeId = BikeId(1),
        key = "$id",
        serial = id,
        kind = kind,
        modelId = null,
        deviceType = null,
        firmware = null,
        actuations = null,
        addedAt = now,
        lastSeenAt = now,
        removedAt = null,
      ),
      latest = null,
    )

  private suspend fun ReceiveTurbine<BikeUiState>.awaitLoaded(
    until: (BikeUiState.Loaded) -> Boolean = { true }
  ): BikeUiState.Loaded {
    while (true) {
      val state = awaitItem()
      if (state is BikeUiState.Loaded && until(state)) return state
    }
  }
}
