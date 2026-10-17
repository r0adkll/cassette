package com.r0adkll.cassette.alerts.api

import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.ComponentId
import kotlin.time.Instant

/**
 * Decides what to notify about (decisions 0004, 0011). Capture calls it after it writes readings.
 */
interface Alerts {
  /**
   * Readings for [bikes] changed. Battery alerts fire for components whose status just worsened;
   * [woke] are bikes that had been asleep and just woke in the background, which may get the
   * pre-ride nudge.
   */
  suspend fun onCaptured(bikes: Set<BikeId>, woke: Set<BikeId>)

  /** The daily not-seen check (14 and 30 days). */
  suspend fun checkReminders()

  /** "Snooze until tomorrow" on a pre-ride nudge. */
  suspend fun snoozePreRide(bike: BikeId, until: Instant)
}

/** Posts notifications; the platform implements it (Android: NotificationManager). */
interface Notifier {
  fun batteryAlert(alert: BatteryAlert)

  fun preRide(nudge: PreRideNudge)

  fun notSeen(reminder: NotSeenReminder)
}

enum class AlertLevel {
  Low,
  ChargeNow,
}

data class BatteryAlert(
  val bike: BikeId,
  val bikeName: String,
  val component: ComponentId,
  val componentName: String,
  val level: AlertLevel,
  /** "18%", "≈12%", "2.18 V" */
  val value: String,
)

data class PreRideNudge(val bike: BikeId, val bikeName: String, val low: List<LowComponent>)

data class LowComponent(val name: String, val value: String, val level: AlertLevel)

data class NotSeenReminder(val bike: BikeId, val bikeName: String, val days: Int)
