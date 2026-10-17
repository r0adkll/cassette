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
