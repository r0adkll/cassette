package com.r0adkll.cassette.ridetest

import com.r0adkll.cassette.axs.FrameType
import com.r0adkll.cassette.data.CaptureSource
import com.r0adkll.cassette.scan.ScanProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideSummaryTest {
  @Test
  fun summarisesWakesGapsComponentsAndReads() {
    val events =
      listOf(
        start(at = 0, label = "Pocket · no head unit", battery = 90),
        delivery(at = 60_000, RD, SHIFTER),
        delivery(at = 120_000, RD),
        delivery(at = 720_000, RD), // 10 min gap
        RideEvent.Gatt(at = 125_000, key = RD, kind = GattKind.HUB_TABLE, source = CaptureSource.BACKGROUND, durationMillis = 2_000, outcome = GattOutcome.OK),
        RideEvent.Gatt(at = 725_000, key = RD, kind = GattKind.HUB_TABLE, source = CaptureSource.BACKGROUND, durationMillis = 4_000, outcome = GattOutcome.FAILED),
        RideEvent.BudgetExceeded(at = 730_000),
        RideEvent.SessionEnded(at = 900_000, phoneBatteryPercent = 87),
      )

    val summary = summarizeSessions(events, nowMillis = 1_000_000).single()

    assertEquals(900_000, summary.durationMillis)
    assertEquals(3, summary.wakes)
    assertEquals(12.0, summary.wakesPerHour, 0.001)
    assertEquals(600_000, summary.longestGapMillis)
    assertEquals(120_000L, summary.longestGapStartedAt)
    assertEquals(180_000, summary.silentTailMillis)
    assertEquals(2, summary.gattAttempts)
    assertEquals(1, summary.gattOk)
    assertEquals(1, summary.gattFailed)
    assertEquals(3_000L, summary.gattAverageMillis)
    assertEquals(1, summary.budgetExceeded)
    assertEquals(3, summary.phoneBatteryUsed)

    val rd = summary.components.first { it.key == RD }
    assertEquals(3, rd.wakesHeardIn)
    assertEquals(600_000, rd.longestSilenceMillis)
    assertEquals(1, rd.gattReadsOk)
    assertEquals(1, summary.components.first { it.key == SHIFTER }.wakesHeardIn)
  }

  @Test
  fun splitsEventsBetweenSessionsAndTracksRunningOne() {
    val events =
      listOf(
        start(at = 0, label = "first"),
        delivery(at = 10_000, RD),
        RideEvent.SessionEnded(at = 20_000, phoneBatteryPercent = null),
        delivery(at = 30_000, RD), // between sessions: ignored
        start(at = 40_000, label = "second", profile = ScanProfile.BATCHED_30S),
        delivery(at = 50_000, RD),
        delivery(at = 55_000, RD),
      )

    val summaries = summarizeSessions(events, nowMillis = 100_000)

    assertEquals(listOf("second", "first"), summaries.map { it.label })
    assertEquals(2, summaries[0].wakes)
    assertNull(summaries[0].endedAt)
    assertEquals(60_000, summaries[0].durationMillis)
    assertEquals(45_000, summaries[0].silentTailMillis)
    assertEquals(1, summaries[1].wakes)
    assertEquals("second", events.activeSession()?.label)
    assertNull(events.dropLast(3).activeSession())
  }

  @Test
  fun sessionWithNoWakesIsAllQuietTail() {
    val summary = summarizeSessions(listOf(start(at = 0, label = "dead scan")), nowMillis = 3_600_000).single()

    assertEquals(0, summary.wakes)
    assertEquals(3_600_000, summary.silentTailMillis)
    assertEquals(0, summary.longestGapMillis)
    assertNull(summary.phoneBatteryUsed)
  }

  private fun start(at: Long, label: String, profile: ScanProfile = ScanProfile.ALL_LOW_POWER, battery: Int? = null) =
    RideEvent.SessionStarted(at, label, profile, battery)

  private fun delivery(at: Long, vararg keys: String) =
    RideEvent.Delivery(at, results = keys.size, sightings = keys.map { Sighting(it, FrameType.STATUS, at) }, callbackType = 1, error = 0)

  private companion object {
    const val RD = "1405188718"
    const val SHIFTER = "1132134130"
  }
}
