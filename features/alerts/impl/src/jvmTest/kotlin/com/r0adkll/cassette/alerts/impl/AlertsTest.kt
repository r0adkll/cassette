package com.r0adkll.cassette.alerts.impl

import com.r0adkll.cassette.alerts.api.AlertLevel
import com.r0adkll.cassette.alerts.api.BatteryAlert
import com.r0adkll.cassette.alerts.api.NotSeenReminder
import com.r0adkll.cassette.alerts.api.Notifier
import com.r0adkll.cassette.alerts.api.PreRideNudge
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.data.db.createInMemoryDriver
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.settings.api.NotificationSettings
import com.r0adkll.cassette.settings.test.InMemorySettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

class AlertsTest {
  private val driver = createInMemoryDriver()
  private val db = CassetteDatabase(driver)
  private var now = Instant.fromEpochMilliseconds(1_792_200_000_000)
  private val clock =
    object : Clock {
      override fun now() = this@AlertsTest.now
    }
  private val settings = InMemorySettings()
  private val notifier = RecordingNotifier()
  private val alerts =
    DefaultAlerts(
      db,
      settings,
      notifier,
      CassetteDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined),
      clock,
    )
  private val bike = BikeId(1)

  @AfterTest fun close() = driver.close()

  @Test
  fun alertsOncePerChangeForTheWorse() = runTest {
    val fd = setUp()
    sample(fd, mV = 8_100, status = "OK")
    alerts.onCaptured(setOf(bike), emptySet())
    assertEquals(emptyList(), notifier.alerts)

    sample(fd, mV = 7_400, status = "LOW")
    alerts.onCaptured(setOf(bike), emptySet())
    alerts.onCaptured(setOf(bike), emptySet())
    assertEquals(listOf(AlertLevel.Low), notifier.alerts.map { it.level })
    assertEquals("Front derailleur", notifier.alerts.single().componentName)

    sample(fd, mV = 7_000, status = "CRITICAL")
    alerts.onCaptured(setOf(bike), emptySet())
    assertEquals(listOf(AlertLevel.Low, AlertLevel.ChargeNow), notifier.alerts.map { it.level })

    // Charged: resets silently, so the next drop alerts again.
    sample(fd, mV = 8_300, status = "OK")
    alerts.onCaptured(setOf(bike), emptySet())
    sample(fd, mV = 7_400, status = "LOW")
    alerts.onCaptured(setOf(bike), emptySet())
    assertEquals(3, notifier.alerts.size)
  }

  @Test
  fun preRideWhenTheBikeWakesWithSomethingAlreadyLowAtMostEvery12Hours() = runTest {
    val fd = setUp()
    sample(fd, mV = 7_400, status = "LOW")
    alerts.onCaptured(setOf(bike), emptySet()) // the battery alert for the drop
    assertEquals(1, notifier.alerts.size)

    now += 1.hours
    alerts.onCaptured(setOf(bike), woke = setOf(bike))
    assertEquals(listOf("Front derailleur"), notifier.nudges.single().low.map { it.name })

    now += 2.hours
    alerts.onCaptured(setOf(bike), woke = setOf(bike))
    assertEquals(1, notifier.nudges.size)

    now += 11.hours
    alerts.onCaptured(setOf(bike), woke = setOf(bike))
    assertEquals(2, notifier.nudges.size)

    alerts.snoozePreRide(bike, until = now + 1.days)
    now += 13.hours
    alerts.onCaptured(setOf(bike), woke = setOf(bike))
    assertEquals(2, notifier.nudges.size)
  }

  @Test
  fun settingsSwitchesSilenceEachKind() = runTest {
    val fd = setUp()
    settings.setNotifications(
      NotificationSettings(batteryAlerts = false, preRide = false, notSeenReminders = false)
    )
    sample(fd, mV = 7_000, status = "CRITICAL")
    alerts.onCaptured(setOf(bike), woke = setOf(bike))
    now += 20.days
    alerts.checkReminders()
    assertEquals(0, notifier.alerts.size + notifier.nudges.size + notifier.reminders.size)
  }

  @Test
  fun notSeenRemindersAt14And30DaysThenStopUntilSeen() = runTest {
    setUp()
    db.bikeQueries.markSeen(now.toEpochMilliseconds(), bike.value)

    now += 13.days
    alerts.checkReminders()
    now += 2.days
    alerts.checkReminders()
    alerts.checkReminders()
    now += 16.days
    alerts.checkReminders()
    now += 30.days
    alerts.checkReminders()
    assertEquals(listOf(15, 31), notifier.reminders.map { it.days })

    // Seen again, then gone again: the reminders start over.
    db.bikeQueries.markSeen(now.toEpochMilliseconds(), bike.value)
    now += 14.days
    alerts.checkReminders()
    assertEquals(listOf(15, 31, 14), notifier.reminders.map { it.days })
  }

  private fun setUp(): Long {
    db.bikeQueries.insert("Road bike", BikeType.Road.name, null, 1, 0, now.toEpochMilliseconds())
    db.componentQueries.insert(
      1,
      "1315137227",
      1315137227,
      ComponentKind.FRONT_DERAILLEUR.name,
      1002,
      0x80,
      null,
      null,
      0,
      now.toEpochMilliseconds(),
      null,
    )
    return db.componentQueries.lastInsertedId().executeAsOne()
  }

  private fun sample(component: Long, mV: Long, status: String) {
    now += 1.minutes
    db.batterySampleQueries.insert(component, now.toEpochMilliseconds(), mV, null, status, "Hub")
  }

  private class RecordingNotifier : Notifier {
    val alerts = mutableListOf<BatteryAlert>()
    val nudges = mutableListOf<PreRideNudge>()
    val reminders = mutableListOf<NotSeenReminder>()

    override fun batteryAlert(alert: BatteryAlert) {
      alerts += alert
    }

    override fun preRide(nudge: PreRideNudge) {
      nudges += nudge
    }

    override fun notSeen(reminder: NotSeenReminder) {
      reminders += reminder
    }
  }
}
