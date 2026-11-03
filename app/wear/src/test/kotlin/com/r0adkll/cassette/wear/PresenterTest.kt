package com.r0adkll.cassette.wear

import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.surfaces.wear.sync.WearSnapshot
import com.r0adkll.cassette.wear.complication.ComplicationPick
import com.r0adkll.cassette.wear.ui.BikeScreen
import com.r0adkll.cassette.wear.ui.ComplicationPicked
import com.r0adkll.cassette.wear.ui.PickBikeScreen
import com.r0adkll.cassette.wear.ui.PickComponentScreen
import com.r0adkll.cassette.wear.ui.bike.BikeEvent
import com.r0adkll.cassette.wear.ui.bike.BikePresenter
import com.r0adkll.cassette.wear.ui.bike.BikeUiState
import com.r0adkll.cassette.wear.ui.bikes.BikeItem
import com.r0adkll.cassette.wear.ui.bikes.BikeList
import com.r0adkll.cassette.wear.ui.bikes.BikesEvent
import com.r0adkll.cassette.wear.ui.bikes.BikesPresenter
import com.r0adkll.cassette.wear.ui.pick.PickBikePresenter
import com.r0adkll.cassette.wear.ui.pick.PickComponentPresenter
import com.r0adkll.cassette.wear.ui.pick.PickEvent
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
  fun aComplicationPicksItsBikeThenItsComponent() = runTest {
    val phone = FakePhoneData(WearSnapshot(defaultBikeId = 1, bikes = listOf(road, gravel)))
    val picks = FakeComplicationPicks()
    val navigator = FakeNavigator(PickBikeScreen(7))
    PickBikePresenter(PickBikeScreen(7), navigator, phone, picks, FixedClock()).test {
      val bikes = expectMostRecentItem()
      assertEquals(
        listOf(
          "Default bike" to "NOW ROAD BIKE",
          "Road bike" to "FD LOW",
          "Gravel bike" to "ALL GOOD",
        ),
        bikes.choices.map { it.name to it.label },
      )
      // Nothing picked yet: a face's own default.
      assertEquals(listOf(false, false, false), bikes.choices.map { it.selected })
      bikes.eventSink(bikes.choices[1].event)
      assertEquals(PickComponentScreen(7, 1), navigator.awaitNextScreen())
    }

    val back = FakeNavigator(PickComponentScreen(7, 1))
    PickComponentPresenter(PickComponentScreen(7, 1), back, phone, picks, FixedClock()).test {
      val components = expectMostRecentItem()
      assertEquals("ROAD BIKE · WHICH ONE?", components.title)
      assertEquals(
        listOf("Weakest" to "NOW FD ≈12%", "Rear derailleur" to "78% · GOOD"),
        components.choices.take(2).map { it.name to it.label },
      )
      components.eventSink(PickEvent.Component("REAR_DERAILLEUR"))
      assertEquals(ComplicationPicked, back.awaitPop().result)
    }
    assertEquals(ComplicationPick(1, "REAR_DERAILLEUR"), picks.get(7))
  }

  @Test
  fun aComplicationsPickIsTickedWhenItsSettingsReopen() = runTest {
    val phone = FakePhoneData(WearSnapshot(defaultBikeId = 1, bikes = listOf(road, gravel)))
    val picks = FakeComplicationPicks().apply { set(7, ComplicationPick(null, null)) }
    PickBikePresenter(
        PickBikeScreen(7),
        FakeNavigator(PickBikeScreen(7)),
        phone,
        picks,
        FixedClock(),
      )
      .test {
        assertEquals(listOf(true, false, false), expectMostRecentItem().choices.map { it.selected })
      }
    PickComponentPresenter(
        PickComponentScreen(7, null),
        FakeNavigator(PickComponentScreen(7, null)),
        phone,
        picks,
        FixedClock(),
      )
      .test {
        val components = expectMostRecentItem()
        assertEquals("DEFAULT BIKE · WHICH ONE?", components.title)
        assertEquals(true, components.choices.first().selected)
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
