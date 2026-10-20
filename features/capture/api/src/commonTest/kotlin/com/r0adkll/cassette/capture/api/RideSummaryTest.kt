package com.r0adkll.cassette.capture.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RideSummaryTest {
  private fun sighting(key: String, at: Long) = Sighting(key, "STATUS", at, -60)

  @Test
  fun summarisesARide() {
    val events =
      listOf(
        CaptureEvent.SessionStarted(0, "Commute", "AllLowPower", 80),
        CaptureEvent.Delivery(60_000, listOf(sighting("rd", 60_000)), 0),
        CaptureEvent.Gatt(
          61_000,
          "rd",
          GattKind.HubTable,
          background = true,
          durationMillis = 2_000,
          outcome = GattOutcome.Ok,
        ),
        CaptureEvent.Delivery(90_000, listOf(sighting("rd", 90_000), sighting("pm", 90_000)), 0),
        CaptureEvent.Delivery(240_000, listOf(sighting("rd", 240_000)), 0),
        CaptureEvent.Gatt(
          241_000,
          "pm",
          GattKind.BatteryLevel,
          background = true,
          durationMillis = 4_000,
          outcome = GattOutcome.Failed,
        ),
        CaptureEvent.BudgetExceeded(245_000),
        CaptureEvent.SessionEnded(3_600_000, 77),
      )

    val summary = summarizeSessions(events, nowMillis = 9_999_999).single()

    assertEquals("Commute", summary.label)
    assertEquals(3, summary.wakes)
    assertEquals(3.0, summary.wakesPerHour, 0.001)
    assertEquals(150_000, summary.longestGapMillis)
    assertEquals(3_600_000 - 240_000L, summary.silentTailMillis)
    assertEquals(2, summary.gattAttempts)
    assertEquals(1, summary.gattOk)
    assertEquals(3_000, summary.gattAverageMillis)
    assertEquals(1, summary.budgetExceeded)
    assertEquals(3, summary.phoneBatteryUsed)
    assertEquals(listOf("rd" to 3, "pm" to 1), summary.components.map { it.key to it.wakesHeardIn })
    assertNull(events.activeSession())
    // A minute a slice: the wakes at 1:00, 1:30 and 4:00.
    assertEquals(TIMELINE_BUCKETS, summary.timeline.size)
    assertEquals(listOf(0, 2, 0, 0, 1), summary.timeline.take(5))
    assertEquals(3, summary.timeline.sum())
    // The 2.5 minute gap and the failed read.
    assertEquals(RideVerdict.Patchy, summary.verdict)
  }

  @Test
  fun keptUpWhenNeverQuietForLong() {
    val wakes =
      (1..20).map { CaptureEvent.Delivery(it * 30_000L, listOf(sighting("rd", it * 30_000L)), 0) }
    val events =
      listOf(CaptureEvent.SessionStarted(0, "Ride", "AllLowPower", null)) +
        wakes +
        CaptureEvent.SessionEnded(620_000, null)
    assertEquals(RideVerdict.KeptUp, summarizeSessions(events, 0).single().verdict)
  }

  @Test
  fun lostWithoutWakesOrWhenArmingFailed() {
    val silent =
      listOf(
        CaptureEvent.SessionStarted(0, "Ride", "AllLowPower", null),
        CaptureEvent.SessionEnded(600_000, null),
      )
    assertEquals(RideVerdict.Lost, summarizeSessions(silent, 0).single().verdict)

    val unarmed =
      listOf(
        CaptureEvent.SessionStarted(0, "Ride", "AllLowPower", null),
        CaptureEvent.Armed(
          1,
          "AllLowPower",
          "Not armed: Bluetooth is off",
          "ride test",
          ok = false,
        ),
        CaptureEvent.Delivery(30_000, listOf(sighting("rd", 30_000)), 0),
        CaptureEvent.SessionEnded(60_000, null),
      )
    assertEquals(RideVerdict.Lost, summarizeSessions(unarmed, 0).single().verdict)
  }

  @Test
  fun aRunningSessionUsesNow() {
    val events = listOf(CaptureEvent.SessionStarted(0, "Ride", "AllLowPower", null))
    val summary = summarizeSessions(events, nowMillis = 1_000).single()
    assertEquals(1_000, summary.durationMillis)
    assertNull(summary.endedAt)
    assertEquals("Ride", events.activeSession()?.label)
  }
}
