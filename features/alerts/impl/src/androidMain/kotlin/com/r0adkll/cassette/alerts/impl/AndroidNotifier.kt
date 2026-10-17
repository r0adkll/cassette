package com.r0adkll.cassette.alerts.impl

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.r0adkll.cassette.alerts.api.AlertLevel
import com.r0adkll.cassette.alerts.api.BatteryAlert
import com.r0adkll.cassette.alerts.api.NotSeenReminder
import com.r0adkll.cassette.alerts.api.Notifier
import com.r0adkll.cassette.alerts.api.PreRideNudge
import com.r0adkll.cassette.common.screens.DeepLinks
import com.r0adkll.cassette.common.screens.deepLinkIntent
import com.r0adkll.cassette.features.alerts.impl.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * Notification channels and posts (decision 0011). Ids and PendingIntent request codes are in the
 * ledger in CLAUDE.md.
 */
@ContributesBinding(AppScope::class)
@Inject
class AndroidNotifier(private val application: Application) : Notifier {

  override fun batteryAlert(alert: BatteryAlert) {
    val title =
      when (alert.level) {
        AlertLevel.Low -> "${alert.componentName} is low"
        AlertLevel.ChargeNow -> "${alert.componentName}: charge now"
      }
    val text =
      when (alert.level) {
        AlertLevel.Low -> "${alert.value} left on ${alert.bikeName}. Charge it soon."
        AlertLevel.ChargeNow ->
          "${alert.value} left on ${alert.bikeName}. It may stop working on your next ride."
      }
    post(
      BATTERY_ALERTS,
      BATTERY_ALERT_ID + alert.component.value.toInt(),
      DeepLinks.component(alert.bike.value, alert.component.value),
      title,
      text,
    ) {}
  }

  override fun preRide(nudge: PreRideNudge) {
    val first = nudge.low.first()
    val more = if (nudge.low.size > 1) " and ${nudge.low.size - 1} more" else ""
    val id = PRE_RIDE_ID + nudge.bike.value.toInt()
    val snooze =
      PendingIntent.getBroadcast(
        application,
        SNOOZE_REQUEST + nudge.bike.value.toInt(),
        Intent(application, SnoozeReceiver::class.java)
          .putExtra(SnoozeReceiver.EXTRA_BIKE, nudge.bike.value)
          .putExtra(SnoozeReceiver.EXTRA_NOTIFICATION, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    post(
      PRE_RIDE,
      id,
      DeepLinks.bike(nudge.bike.value),
      "${nudge.bikeName} is awake",
      "${first.name} is ${if (first.level == AlertLevel.ChargeNow) "nearly empty" else "low"} (${first.value})$more. Charge it before you ride.",
    ) {
      addAction(0, "Snooze until tomorrow", snooze)
    }
  }

  override fun notSeen(reminder: NotSeenReminder) {
    post(
      REMINDERS,
      REMINDER_ID + reminder.bike.value.toInt(),
      DeepLinks.bike(reminder.bike.value),
      "Haven't seen ${reminder.bikeName} in ${reminder.days} days",
      "Wake it so Cassette can check the batteries.",
    ) {}
  }

  private fun post(
    channel: String,
    id: Int,
    link: String,
    title: String,
    text: String,
    extra: NotificationCompat.Builder.() -> Unit,
  ) {
    ensureChannels(application)
    val manager = NotificationManagerCompat.from(application)
    if (!manager.areNotificationsEnabled()) return
    val open =
      PendingIntent.getActivity(
        application,
        id,
        application.deepLinkIntent(link),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val notification =
      NotificationCompat.Builder(application, channel)
        .setSmallIcon(R.drawable.lucide_battery_medium)
        .setColor(CASSETTE_RED)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(open)
        .setAutoCancel(true)
        .apply(extra)
        .build()
    try {
      manager.notify(id, notification)
    } catch (_: SecurityException) {
      // Notifications not allowed; nothing to do.
    }
  }

  companion object {
    const val BATTERY_ALERTS = "battery_alerts"
    const val PRE_RIDE = "pre_ride"
    const val REMINDERS = "reminders"

    const val BATTERY_ALERT_ID = 1_000_000
    const val PRE_RIDE_ID = 2_000_000
    const val REMINDER_ID = 3_000_000
    const val SNOOZE_REQUEST = 2_500_000

    private const val CASSETTE_RED = 0xFFE4002B.toInt()

    /** The four channels from decision 0011, minus Capture until a foreground read needs it. */
    fun ensureChannels(context: Context) {
      val manager = context.getSystemService(NotificationManager::class.java) ?: return
      manager.createNotificationChannels(
        listOf(
          NotificationChannel(
              BATTERY_ALERTS,
              "Battery alerts",
              NotificationManager.IMPORTANCE_DEFAULT,
            )
            .apply {
              description = "A component got low or needs charging"
            },
          NotificationChannel(PRE_RIDE, "Pre-ride", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Your bike woke and something is low"
          },
          NotificationChannel(REMINDERS, "Reminders", NotificationManager.IMPORTANCE_LOW).apply {
            description = "A bike hasn't been seen for a while"
          },
        )
      )
    }
  }
}
