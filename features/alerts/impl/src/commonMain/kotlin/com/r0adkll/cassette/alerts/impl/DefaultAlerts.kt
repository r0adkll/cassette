package com.r0adkll.cassette.alerts.impl

import com.r0adkll.cassette.alerts.api.AlertLevel
import com.r0adkll.cassette.alerts.api.Alerts
import com.r0adkll.cassette.alerts.api.BatteryAlert
import com.r0adkll.cassette.alerts.api.LowComponent
import com.r0adkll.cassette.alerts.api.NotSeenReminder
import com.r0adkll.cassette.alerts.api.Notifier
import com.r0adkll.cassette.alerts.api.PreRideNudge
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.core.ComponentId
import com.r0adkll.cassette.core.status
import com.r0adkll.cassette.core.valueText
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.data.db.toDomain
import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.settings.api.CassetteSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultAlerts(
  private val db: CassetteDatabase,
  private val settings: CassetteSettings,
  private val notifier: Notifier,
  private val dispatchers: CassetteDispatchers,
  private val clock: Clock,
) : Alerts {
  private val lock = Mutex()

  override suspend fun onCaptured(bikes: Set<BikeId>, woke: Set<BikeId>) {
    val enabled = settings.notifications.first()
    val toNotify = mutableListOf<BatteryAlert>()
    val nudges = mutableListOf<PreRideNudge>()
    lock.withLock {
      withContext(dispatchers.io) {
        val now = clock.now()
        for (bikeId in bikes) {
          val bike =
            db.bikeQueries.selectById(bikeId.value).executeAsOneOrNull()?.toDomain() ?: continue
          val components =
            db.componentQueries.selectForBike(bikeId.value).executeAsList().map { it.toDomain() }
          val latest =
            db.batterySampleQueries.latestForBike(bikeId.value).executeAsList().associate {
              it.component_id to it.toDomain()
            }
          val justAlerted = mutableSetOf<Long>()
          val low = mutableListOf<LowComponent>()
          db.transaction {
            for (component in components) {
              val reading = latest[component.id.value] ?: continue
              val status = reading.status
              if (status == BatteryStatus.UNKNOWN) continue
              val previous =
                db.alertStateQueries
                  .select(component.id.value)
                  .executeAsOneOrNull()
                  ?.last_status
                  ?.let { name ->
                    BatteryStatus.entries.firstOrNull { it.name == name }
                  }
              val level = status.level
              if (level != null)
                low += LowComponent(component.kind.label, reading.valueText(), level)
              // Notify once per change for the worse; a charge (back to OK) resets silently.
              if (level != null && status.worseThan(previous)) {
                justAlerted += component.id.value
                if (enabled.batteryAlerts) {
                  toNotify +=
                    BatteryAlert(
                      bike.id,
                      bike.name,
                      ComponentId(component.id.value),
                      component.kind.label,
                      level,
                      reading.valueText(),
                    )
                }
              }
              if (status != previous)
                db.alertStateQueries.upsert(
                  component.id.value,
                  status.name,
                  now.toEpochMilliseconds(),
                )
            }
            // Pre-ride: the bike just woke and something was already low (new drops got an alert
            // above).
            // Skipped when a component just got its own battery alert, so one wake doesn't notify
            // twice.
            if (bikeId in woke && enabled.preRide && low.isNotEmpty() && justAlerted.isEmpty()) {
              db.bikeAlertQueries.ensure(bikeId.value)
              val notBefore =
                db.bikeAlertQueries.select(bikeId.value).executeAsOne().pre_ride_not_before
              if (notBefore == null || now.toEpochMilliseconds() >= notBefore) {
                nudges += PreRideNudge(bike.id, bike.name, low)
                db.bikeAlertQueries.setPreRideNotBefore(
                  (now + PRE_RIDE_QUIET).toEpochMilliseconds(),
                  bikeId.value,
                )
              }
            }
          }
        }
      }
    }
    toNotify.forEach(notifier::batteryAlert)
    nudges.forEach(notifier::preRide)
  }

  override suspend fun checkReminders() {
    if (!settings.notifications.first().notSeenReminders) return
    val reminders = mutableListOf<NotSeenReminder>()
    lock.withLock {
      withContext(dispatchers.io) {
        val now = clock.now()
        for (row in db.bikeQueries.selectAll().executeAsList()) {
          val bike = row.toDomain()
          val seen = bike.lastSeenAt ?: continue
          val days = (now - seen).inWholeDays.toInt()
          db.bikeAlertQueries.ensure(row.id)
          val state = db.bikeAlertQueries.select(row.id).executeAsOne()
          // Seeing the bike again moves last_seen_at, which resets the stage.
          val stage =
            if (state.reminded_for_seen_at == row.last_seen_at) state.reminded_days.toInt() else 0
          val due = REMINDER_DAYS.lastOrNull { days >= it && stage < it } ?: continue
          reminders += NotSeenReminder(bike.id, bike.name, days)
          db.bikeAlertQueries.setReminded(due.toLong(), row.last_seen_at, row.id)
        }
      }
    }
    reminders.forEach(notifier::notSeen)
  }

  override suspend fun snoozePreRide(bike: BikeId, until: Instant) {
    lock.withLock {
      withContext(dispatchers.io) {
        db.bikeAlertQueries.ensure(bike.value)
        db.bikeAlertQueries.setPreRideNotBefore(until.toEpochMilliseconds(), bike.value)
      }
    }
  }

  private val BatteryStatus.level: AlertLevel?
    get() =
      when (this) {
        BatteryStatus.LOW -> AlertLevel.Low
        BatteryStatus.CRITICAL -> AlertLevel.ChargeNow
        else -> null
      }

  private fun BatteryStatus.worseThan(previous: BatteryStatus?): Boolean =
    when (this) {
      BatteryStatus.LOW ->
        previous == null || previous == BatteryStatus.OK || previous == BatteryStatus.UNKNOWN
      BatteryStatus.CRITICAL -> previous != BatteryStatus.CRITICAL
      else -> false
    }

  private companion object {
    val PRE_RIDE_QUIET = 12.hours
    /** Not-seen reminders, then stop until the bike is seen (decision 0010). */
    val REMINDER_DAYS = listOf(14, 30)
  }
}
