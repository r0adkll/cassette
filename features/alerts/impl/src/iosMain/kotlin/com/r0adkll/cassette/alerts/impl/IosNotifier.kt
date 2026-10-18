package com.r0adkll.cassette.alerts.impl

import com.r0adkll.cassette.alerts.api.BatteryAlert
import com.r0adkll.cassette.alerts.api.NotSeenReminder
import com.r0adkll.cassette.alerts.api.Notifier
import com.r0adkll.cassette.alerts.api.PreRideNudge
import com.r0adkll.cassette.common.screens.DeepLinks
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Posts through `UNUserNotificationCenter` (decision 0016). Identifiers follow the Android ids in
 * the CLAUDE.md ledger, so a newer alert replaces the older one; iOS has no channels, so each kind
 * is its own thread instead.
 */
@ContributesBinding(AppScope::class)
@Inject
class IosNotifier : Notifier {
  override fun batteryAlert(alert: BatteryAlert) =
    post(
      id = "$BATTERY_ALERTS-${alert.component.value}",
      thread = BATTERY_ALERTS,
      link = DeepLinks.component(alert.bike.value, alert.component.value),
      title = AlertText.title(alert),
      text = AlertText.text(alert),
    )

  override fun preRide(nudge: PreRideNudge) =
    post(
      id = "$PRE_RIDE-${nudge.bike.value}",
      thread = PRE_RIDE,
      link = DeepLinks.bike(nudge.bike.value),
      title = AlertText.title(nudge),
      text = AlertText.text(nudge),
      category = PRE_RIDE,
      bike = nudge.bike.value,
      // Android's pre-ride channel is high importance; this is iOS's equivalent.
      level = UNNotificationInterruptionLevel.UNNotificationInterruptionLevelTimeSensitive,
    )

  override fun notSeen(reminder: NotSeenReminder) =
    post(
      id = "$REMINDERS-${reminder.bike.value}",
      thread = REMINDERS,
      link = DeepLinks.bike(reminder.bike.value),
      title = AlertText.title(reminder),
      text = AlertText.NOT_SEEN_TEXT,
      level = UNNotificationInterruptionLevel.UNNotificationInterruptionLevelPassive,
    )

  private fun post(
    id: String,
    thread: String,
    link: String,
    title: String,
    text: String,
    category: String? = null,
    bike: Long? = null,
    level: UNNotificationInterruptionLevel =
      UNNotificationInterruptionLevel.UNNotificationInterruptionLevelActive,
  ) {
    val content =
      UNMutableNotificationContent().apply {
        setTitle(title)
        setBody(text)
        setThreadIdentifier(thread)
        setInterruptionLevel(level)
        // Strings only: NSNumber doesn't come back as a Kotlin Long.
        setUserInfo(listOfNotNull(LINK to link, bike?.let { BIKE to it.toString() }).toMap())
        category?.let { setCategoryIdentifier(it) }
      }
    // Without permission the request is dropped, as Android's are.
    UNUserNotificationCenter.currentNotificationCenter()
      .addNotificationRequest(
        UNNotificationRequest.requestWithIdentifier(id, content, null),
        withCompletionHandler = null,
      )
  }

  companion object {
    const val BATTERY_ALERTS = "battery"
    const val PRE_RIDE = "pre-ride"
    const val REMINDERS = "not-seen"

    /** The userInfo keys a tap or the snooze action reads back. */
    const val LINK = "link"
    const val BIKE = "bike"

    const val SNOOZE_ACTION = "snooze"

    /** The pre-ride nudge's "Snooze until tomorrow"; registered at launch. */
    fun registerCategories() {
      val snooze = UNNotificationAction.actionWithIdentifier(SNOOZE_ACTION, AlertText.SNOOZE, 0uL)
      UNUserNotificationCenter.currentNotificationCenter()
        .setNotificationCategories(
          setOf(
            UNNotificationCategory.categoryWithIdentifier(
              PRE_RIDE,
              listOf(snooze),
              emptyList<String>(),
              0uL,
            )
          )
        )
    }
  }
}
