package com.r0adkll.cassette.diagnostics.ui

import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.capture.api.RideVerdict
import com.r0adkll.cassette.capture.api.Sighting
import com.r0adkll.cassette.capture.api.activeSession
import com.r0adkll.cassette.capture.api.summarizeSessions
import com.r0adkll.cassette.data.ble.ScanProfile
import com.r0adkll.cassette.settings.test.InMemorySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking

class RideTestRunnerTest {
  private val settings = InMemorySettings()
  private val log = MemoryLog()
  private val capture = ArmingController()
  private var now = 1_000L
  private val clock =
    object : Clock {
      override fun now() = Instant.fromEpochMilliseconds(now)
    }
  private val runner = RideTestRunner(log, settings, capture, { 80 }, clock)

  @Test
  fun anotherProfileIsUsedForTheRideAndPutBackOnStop() = runBlocking {
    runner.start("Commute", ScanProfile.Batched30s)

    assertEquals(ScanProfile.Batched30s, settings.scanProfile.value)
    val start = log.events.value.activeSession()!!
    assertEquals(ScanProfile.Batched30s.name, start.profile)
    assertEquals(ScanProfile.AllLowPower.name, start.restoreProfile)
    assertEquals(listOf("ride test started"), capture.arms)

    now = 600_000
    assertEquals(1_000L, runner.stop())

    assertEquals(ScanProfile.AllLowPower, settings.scanProfile.value)
    assertEquals(listOf("ride test started", "ride test ended"), capture.arms)
    assertNull(log.events.value.activeSession())
  }

  @Test
  fun theUsualProfileNeedsNothingPutBack() = runBlocking {
    runner.start("", ScanProfile.AllLowPower)
    val start = log.events.value.activeSession()!!
    assertEquals("Ride", start.label)
    assertNull(start.restoreProfile)

    runner.stop()
    assertEquals(ScanProfile.AllLowPower, settings.scanProfile.value)
    // Only the start arms: nothing changed at the end.
    assertEquals(listOf("ride test started"), capture.arms)
  }

  @Test
  fun stopWithNothingRecordingDoesNothing() = runBlocking {
    assertNull(runner.stop())
    assertTrue(log.events.value.isEmpty())
  }

  @Test
  fun profilesCompareFinishedTestsOnly() {
    val events =
      listOf(
        CaptureEvent.SessionStarted(0, "One", "AllLowPower", 80),
        CaptureEvent.Delivery(60_000, listOf(Sighting("rd", "STATUS", 60_000, -60)), 0),
        CaptureEvent.SessionEnded(3_600_000, 78),
        // An old session with a profile no longer offered is left out.
        CaptureEvent.SessionStarted(4_000_000, "Old", "AllBalanced", null),
        CaptureEvent.SessionEnded(5_000_000, null),
        CaptureEvent.SessionStarted(6_000_000, "Running", "Batched30s", null),
      )
    val comparison =
      compareProfiles(summarizeSessions(events, 6_500_000).filter { it.endedAt != null })

    val (lowPower, batched) = comparison
    assertEquals(1, lowPower.rides)
    assertEquals(1.0, lowPower.wakesPerHour!!, 0.001)
    assertEquals(0.5, lowPower.phonePer15Min!!, 0.001)
    assertEquals(0, batched.rides)
    assertNull(batched.wakesPerHour)
  }

  @Test
  fun verdictSentencesSayWhatHappened() {
    val events =
      listOf(CaptureEvent.SessionStarted(0, "Ride", "AllLowPower", null)) +
        (1..40).map {
          CaptureEvent.Delivery(
            it * 15_000L,
            listOf(Sighting("rd", "STATUS", it * 15_000L, -60)),
            0,
          )
        } +
        CaptureEvent.SessionEnded(600_000, null)
    val test = summarizeSessions(events, 0).single()
    assertEquals(RideVerdict.KeptUp, test.verdict)
    assertEquals(
      "Cassette heard the bike about every 15 s and was never quiet for more than 15 s. " +
        "No battery reads were due.",
      verdictSentence(test),
    )
  }

  @Test
  fun onlyQuietStretchesOf45SecondsAreTinted() {
    // 10 s slices: a 30 s gap (Batched 30 s spacing) stays plain, a 50 s one is tinted.
    val timeline = listOf(1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1)
    assertEquals(setOf(5, 6, 7, 8, 9), quietSlices(timeline, 110_000))
    // A test seconds old: every slice is empty but far shorter than 45 s.
    assertTrue(quietSlices(List(60) { 0 }, 5_000).isEmpty())
  }

  @Test
  fun nextWaitsForAName() {
    val checks = ReadyChecks(true, true, "Road bike", null, Instant.fromEpochMilliseconds(0))
    fun setup(label: String) =
      RideTestSetupUiState(
        1,
        ScanProfile.AllLowPower,
        ScanProfile.AllLowPower,
        label,
        checks,
        starting = false,
      ) {}
    assertTrue(!setup("").canContinue)
    assertTrue(!setup("   ").canContinue)
    assertTrue(setup("Commute").canContinue)
  }

  private class MemoryLog : CaptureLog {
    override val events = MutableStateFlow(emptyList<CaptureEvent>())

    override suspend fun append(event: CaptureEvent) {
      events.value += event
    }

    override suspend fun clear() {
      events.value = emptyList()
    }

    override suspend fun export() = ""
  }

  private class ArmingController : CaptureController {
    val arms = mutableListOf<String>()
    override val isReady = true

    override fun refresh(): Flow<RefreshProgress> = emptyFlow()

    override suspend fun ensureBackgroundCapture(reason: String) {
      arms += reason
    }
  }
}
