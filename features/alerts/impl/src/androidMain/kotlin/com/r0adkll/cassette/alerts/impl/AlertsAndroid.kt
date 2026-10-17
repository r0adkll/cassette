package com.r0adkll.cassette.alerts.impl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.r0adkll.cassette.alerts.api.Alerts
import com.r0adkll.cassette.core.BikeId
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Implemented by the Application so receivers and workers reach the graph (decision 0007). */
interface AlertsHost {
  val alerts: Alerts
  val alertsScope: CoroutineScope
}

/** "Snooze until tomorrow" on the pre-ride nudge: quiet until 5:00 tomorrow, local time. */
class SnoozeReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val bike = intent.getLongExtra(EXTRA_BIKE, -1).takeIf { it >= 0 } ?: return
    NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
    val host = context.applicationContext as AlertsHost
    val until =
      LocalDate.now()
        .plusDays(1)
        .atTime(LocalTime.of(5, 0))
        .atZone(ZoneId.systemDefault())
        .toInstant()
    val pending = goAsync()
    host.alertsScope.launch {
      try {
        host.alerts.snoozePreRide(BikeId(bike), Instant.fromEpochMilliseconds(until.toEpochMilli()))
      } finally {
        pending.finish()
      }
    }
  }

  companion object {
    const val EXTRA_BIKE = "bike"
    const val EXTRA_NOTIFICATION = "notification"
  }
}

/** The daily not-seen check (14 and 30 days). */
class ReminderWorker(context: Context, params: WorkerParameters) :
  CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    (applicationContext as AlertsHost).alerts.checkReminders()
    return Result.success()
  }

  companion object {
    private const val NAME = "not-seen-reminders"

    fun schedule(context: Context) {
      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
          NAME,
          ExistingPeriodicWorkPolicy.KEEP,
          PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build(),
        )
    }
  }
}
