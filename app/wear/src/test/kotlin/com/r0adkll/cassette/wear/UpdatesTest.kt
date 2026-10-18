package com.r0adkll.cassette.wear

import com.r0adkll.cassette.surfaces.wear.sync.WearSnapshot
import com.r0adkll.cassette.wear.ui.AllowUpdatesScreen
import com.r0adkll.cassette.wear.ui.BikesScreen
import com.r0adkll.cassette.wear.ui.UpdateScreen
import com.r0adkll.cassette.wear.ui.bikes.BikesEvent
import com.r0adkll.cassette.wear.ui.bikes.BikesPresenter
import com.r0adkll.cassette.wear.ui.bikes.UpdateRow
import com.r0adkll.cassette.wear.ui.update.AllowStep
import com.r0adkll.cassette.wear.ui.update.AllowUpdatesEvent
import com.r0adkll.cassette.wear.ui.update.AllowUpdatesPresenter
import com.r0adkll.cassette.wear.ui.update.UpdateEvent
import com.r0adkll.cassette.wear.ui.update.UpdatePresenter
import com.r0adkll.cassette.wear.updates.WatchUpdate
import com.r0adkll.cassette.wear.updates.megabytes
import com.r0adkll.cassette.wear.updates.toWatchUpdate
import com.r0adkll.drop.DropState
import com.r0adkll.drop.ReinstallReason
import com.r0adkll.drop.Update
import com.r0adkll.drop.UpdateCheckResult
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class UpdatesTest {
  @Test
  fun theBikeListOffersTheUpdateUnderTheBikes() = runTest {
    val updates = FakeWatchUpdates(WatchUpdate.Available(37, 4_081_551, "main"))
    val navigator = FakeNavigator(BikesScreen)
    val phone = FakePhoneData(WearSnapshot(defaultBikeId = 1, bikes = listOf(road)))
    BikesPresenter(navigator, phone, updates, FixedClock()).test {
      val state = expectMostRecentItem()
      assertEquals(UpdateRow("BUILD 37 · 4.1 MB"), state.update)
      assertNull(state.updatedTo)
      assertEquals(1, updates.checks)

      updates.state.value = WatchUpdate.Downloading(37, 2_500_000, 4_081_551)
      assertEquals(UpdateRow("DOWNLOADING 61%"), awaitItem().update)

      state.eventSink(BikesEvent.OpenUpdate)
      assertEquals(UpdateScreen, navigator.awaitNextScreen())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun afterAnUpdateTheListSaysSoOnce() = runTest {
    val updates = FakeWatchUpdates(installedBuild = 37, justUpdated = true)
    BikesPresenter(FakeNavigator(BikesScreen), FakePhoneData(), updates, FixedClock()).test {
      assertEquals(37L, expectMostRecentItem().updatedTo)
    }
  }

  @Test
  fun installAsksForPermissionFirst() = runTest {
    val updates = FakeWatchUpdates(WatchUpdate.Available(37, 4_081_551, "main"))
    val navigator = FakeNavigator(UpdateScreen)
    UpdatePresenter(navigator, updates).test {
      val state = expectMostRecentItem()
      updates.allowed = false
      state.eventSink(UpdateEvent.Install)
      assertEquals(AllowUpdatesScreen, navigator.awaitNextScreen())
      assertEquals(0, updates.installs)

      updates.allowed = true
      state.eventSink(UpdateEvent.Install)
      assertEquals(1, updates.installs)
    }
  }

  @Test
  fun allowStepExplainsOpensSettingsAndCarriesOnOnceAllowed() = runTest {
    val updates = FakeWatchUpdates().apply { allowed = false }
    val navigator = FakeNavigator(AllowUpdatesScreen)
    AllowUpdatesPresenter(navigator, updates).test {
      val explain = awaitItem()
      assertEquals(AllowStep.Explain, explain.step)
      explain.eventSink(AllowUpdatesEvent.OpenSettings)
      assertEquals(AllowStep.Path, awaitItem().step)
      testScheduler.advanceTimeBy(
        AllowUpdatesPresenter.PATH_FOR + kotlin.time.Duration.parse("1ms")
      )
      testScheduler.runCurrent()
      assertEquals(1, updates.settingsOpened)

      // Back from Settings with the switch on.
      updates.allowed = true
      explain.eventSink(AllowUpdatesEvent.Resumed)
      assertEquals(AllowStep.Allowed, awaitItem().step)
      testScheduler.advanceTimeBy(
        AllowUpdatesPresenter.ALLOWED_FOR + kotlin.time.Duration.parse("1ms")
      )
      testScheduler.runCurrent()
      assertEquals(1, updates.installs)
      navigator.awaitPop()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun backFromSettingsWithoutTheSwitchExplainsAgain() = runTest {
    val updates = FakeWatchUpdates().apply { allowed = false }
    AllowUpdatesPresenter(FakeNavigator(AllowUpdatesScreen), updates).test {
      awaitItem().eventSink(AllowUpdatesEvent.OpenSettings)
      val path = awaitItem()
      path.eventSink(AllowUpdatesEvent.Resumed)
      assertEquals(AllowStep.Explain, awaitItem().step)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun dropStatesAsTheWatchShowsThem() {
    val update = dropUpdate()
    assertEquals(WatchUpdate.None, DropState.Idle.toWatchUpdate())
    assertEquals(
      WatchUpdate.Available(37, 4_081_551, "main"),
      DropState.UpdateAvailable(update, UpdateCheckResult.Available(update)).toWatchUpdate(),
    )
    // Downloaded already: still an offer, Install finishes it.
    assertEquals(
      WatchUpdate.Available(37, 4_081_551, "main"),
      DropState.ReadyToInstall(update).toWatchUpdate(),
    )
    assertEquals(
      WatchUpdate.Downloading(37, 1_000, 4_081_551),
      DropState.Downloading(update, 1_000, 4_081_551).toWatchUpdate(),
    )
    assertEquals(WatchUpdate.Failed(37, "nope"), DropState.Failed("nope", update).toWatchUpdate())
    // A reinstall would mean uninstalling the watch app: not offered.
    val reinstall = update.copy(requiresReinstall = ReinstallReason.Signer)
    assertEquals(
      WatchUpdate.None,
      DropState.UpdateAvailable(reinstall, UpdateCheckResult.ReinstallRequired(reinstall))
        .toWatchUpdate(),
    )
  }

  @Test
  fun sizesInMegabytes() {
    assertEquals("4.1 MB", megabytes(4_081_551))
    assertEquals("47.5 MB", megabytes(47_542_976))
    assertEquals("0.0 MB", megabytes(0))
  }

  private fun dropUpdate() =
    Update(
      buildId = "b1",
      channel = "main",
      label = "Cassette (dev)",
      versionName = "0.1.0",
      versionCode = 37,
      uploadedAt = kotlin.time.Instant.fromEpochMilliseconds(0),
      headline = "M2",
      commitSha = "abc",
      pullRequest = null,
      buildUrl = "https://drop.example/b1",
      fileSize = 4_081_551,
      fileSha256 = "00",
      downloadUrl = "https://drop.example/b1/download",
      downloadUrlExpiresAt = kotlin.time.Instant.fromEpochMilliseconds(0),
      requiresReinstall = null,
    )
}
