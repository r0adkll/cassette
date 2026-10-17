package com.r0adkll.cassette.wear

import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.surfaces.wear.sync.WearSnapshot
import com.r0adkll.cassette.wear.ui.BikeScreen
import com.r0adkll.cassette.wear.ui.bike.BikeEvent
import com.r0adkll.cassette.wear.ui.bike.BikePresenter
import com.r0adkll.cassette.wear.ui.bike.BikeUiState
import com.r0adkll.cassette.wear.ui.bikes.BikeItem
import com.r0adkll.cassette.wear.ui.bikes.BikeList
import com.r0adkll.cassette.wear.ui.bikes.BikesEvent
import com.r0adkll.cassette.wear.ui.bikes.BikesPresenter
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PresenterTest {
  @Test
  fun bikesListsEachWithItsStatus() = runTest {
    val phone = FakePhoneData(WearSnapshot(defaultBikeId = 1, bikes = listOf(road, gravel)))
    val navigator = FakeNavigator(com.r0adkll.cassette.wear.ui.BikesScreen)
    BikesPresenter(navigator, phone, FakeWatchUpdates(), FixedClock()).test {
      val loaded = expectMostRecentItem()
      assertEquals(
        listOf(
          BikeItem(1, "Road bike", StatusTone.Low, "FD LOW"),
          BikeItem(2, "Gravel bike", StatusTone.Good, "ALL GOOD"),
        ),
        (loaded.list as BikeList.Bikes).bikes,
      )
      loaded.eventSink(BikesEvent.Open(2))
      assertEquals(BikeScreen(2), navigator.awaitNextScreen())
    }
  }

  @Test
  fun nothingFromThePhoneYet() = runTest {
    BikesPresenter(
        FakeNavigator(com.r0adkll.cassette.wear.ui.BikesScreen),
        FakePhoneData(),
        FakeWatchUpdates(),
        FixedClock(),
      )
      .test { assertEquals(BikeList.Empty(synced = false), expectMostRecentItem().list) }
  }

  @Test
  fun refreshAsksThePhoneOrSaysItCant() = runTest {
    val phone = FakePhoneData(WearSnapshot(defaultBikeId = 1, bikes = listOf(road)))
    BikePresenter(BikeScreen(1), phone, FixedClock()).test {
      val loaded = expectMostRecentItem() as BikeUiState.Loaded
      assertEquals("Charge soon.", loaded.status.headline)
      assertEquals(listOf("RD", "FD"), loaded.components.map { it.code })

      phone.reachable = false
      loaded.eventSink(BikeEvent.Refresh)
      var refresh: WatchRefresh
      do refresh = (awaitItem() as BikeUiState.Loaded).refresh while (
        refresh != WatchRefresh.NoPhone
      )
      assertEquals(1, phone.requests)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun aBikeRemovedOnThePhoneIsGone() = runTest {
    val phone = FakePhoneData(WearSnapshot(defaultBikeId = 2, bikes = listOf(gravel)))
    BikePresenter(BikeScreen(1), phone, FixedClock()).test {
      assertEquals(BikeUiState.Gone, expectMostRecentItem())
    }
  }
}
