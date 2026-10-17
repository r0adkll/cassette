package com.r0adkll.cassette.bikes.ui

import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.api.ComponentDetail
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.common.screens.ComponentScreen
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.ComponentId
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

  @Test
  fun loadingThenEmptyThenLoaded() = runTest {
    val navigator = FakeNavigator(BikeScreen())
    val presenter = BikePresenter(BikeScreen(), navigator, repository, capture, clock)

    presenter.test {
      assertIs<BikeUiState.Loading>(awaitItem())

      overviews.emit(null)
      assertIs<BikeUiState.Empty>(awaitItem())

      overviews.emit(
        BikeOverview(
          Bike(BikeId(1), "Road bike", BikeType.Road, null, 1, now, now),
          2,
          emptyList(),
        )
      )
      val loaded = assertIs<BikeUiState.Loaded>(awaitItem())
      assertEquals("Road bike", loaded.name)
      assertEquals("1 OF 2", loaded.position)

      loaded.eventSink(BikeEvent.OpenComponent(7))
      assertEquals(ComponentScreen(7), navigator.awaitNextScreen())
      cancelAndIgnoreRemainingEvents()
    }
  }
}
