package com.r0adkll.cassette.alerts.impl

import com.r0adkll.cassette.alerts.api.AlertLevel
import com.r0adkll.cassette.alerts.api.BatteryAlert
import com.r0adkll.cassette.alerts.api.NotSeenReminder
import com.r0adkll.cassette.alerts.api.PreRideNudge

/** Each notification's words, shared by every platform's notifier (decision 0011). */
internal object AlertText {
  fun title(alert: BatteryAlert): String =
    when (alert.level) {
      AlertLevel.Low -> "${alert.componentName} is low"
      AlertLevel.ChargeNow -> "${alert.componentName}: charge now"
    }

  fun text(alert: BatteryAlert): String =
    when (alert.level) {
      AlertLevel.Low -> "${alert.value} left on ${alert.bikeName}. Charge it soon."
      AlertLevel.ChargeNow ->
        "${alert.value} left on ${alert.bikeName}. It may stop working on your next ride."
    }

  fun title(nudge: PreRideNudge): String = "${nudge.bikeName} is awake"

  fun text(nudge: PreRideNudge): String {
    val first = nudge.low.first()
    val more = if (nudge.low.size > 1) " and ${nudge.low.size - 1} more" else ""
    val state = if (first.level == AlertLevel.ChargeNow) "nearly empty" else "low"
    return "${first.name} is $state (${first.value})$more. Charge it before you ride."
  }

  fun title(reminder: NotSeenReminder): String =
    "Haven't seen ${reminder.bikeName} in ${reminder.days} days"

  const val NOT_SEEN_TEXT = "Wake it so Cassette can check the batteries."

  const val SNOOZE = "Snooze until tomorrow"
}
