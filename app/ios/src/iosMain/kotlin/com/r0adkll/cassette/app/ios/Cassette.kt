package com.r0adkll.cassette.app.ios

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.r0adkll.cassette.alerts.impl.IosNotifier
import com.r0adkll.cassette.app.shared.CassetteRoot
import com.r0adkll.cassette.common.compose.LocalAppUpdates
import com.r0adkll.cassette.common.screens.DeepLinks
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.settings.api.ThemeMode
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.createGraph
import kotlin.time.Instant
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * What the Swift app calls (decision 0017): `Cassette.shared` in Swift. iOS entry points reach the
 * graph through it, as Android's reach it through the Application (decision 0007).
 */
object Cassette {
  val graph: IosAppGraph by lazy { createGraph<IosAppGraph>() }

  private val scope = MainScope()

  /** Links from notifications and `cassette://` URLs. */
  private val links = Channel<List<Screen>>(Channel.CONFLATED)

  private val notifications = NotificationDelegate()

  /**
   * At every launch, including the ones iOS makes in the background for the Bluetooth scan: the
   * central manager has to exist before launch finishes for iOS to restore it. [reloadWidgets] is
   * WidgetKit's reload and [showRefresh] starts, updates or ends the refresh's Live Activity
   * (returning null, or why ActivityKit refused): only Swift can call either.
   */
  fun didFinishLaunching(reloadWidgets: () -> Unit, showRefresh: (String) -> String?) {
    graph.central.start()
    graph.widgets.start(scope, reloadWidgets)
    graph.refreshActivity.start(scope, showRefresh)
    IosReminders({ graph.alerts }, scope).register()
    IosNotifier.registerCategories()
    UNUserNotificationCenter.currentNotificationCenter().delegate = notifications
  }

  /** The Live Activity's Stop. */
  fun stopRefresh() = graph.captureController.stopRefresh()

  /** The Live Activity's Add 60 s. */
  fun addRefreshTime() = graph.captureController.addRefreshTime()

  /** Leaving the app: the bike screen picks a new status title (decision 0015). */
  fun didEnterBackground() = graph.statusTitles.appBackgrounded()

  fun open(url: String) {
    DeepLinks.parse(url)?.let(links::trySend)
  }

  fun mainViewController(): UIViewController {
    scope.launch { graph.captureController.ensureBackgroundCapture("app opened") }
    return ComposeUIViewController {
      CassetteRoot(
        circuit = graph.circuit,
        settings = graph.settings,
        bikes = graph.bikeRepository,
        permissions = graph.permissions,
        // TestFlight updates the app: App updates stays hidden.
        updates = LocalAppUpdates.current,
        launchLink = null,
        newLinks = links.receiveAsFlow(),
        systemBars = { _, mode ->
          // The status bar follows the window's style; System leaves it to the phone.
          LaunchedEffect(mode) {
            UIApplication.sharedApplication.keyWindow?.overrideUserInterfaceStyle =
              when (mode) {
                ThemeMode.System -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
                ThemeMode.Light -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
                ThemeMode.Dark -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
              }
          }
        },
      )
    }
  }

  /** Taps open the notification's link; the pre-ride nudge's action snoozes it. */
  private class NotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
      center: UNUserNotificationCenter,
      willPresentNotification: UNNotification,
      withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
      // Shown while the app is open too, as on Android.
      withCompletionHandler(
        UNNotificationPresentationOptionBanner or
          UNNotificationPresentationOptionList or
          UNNotificationPresentationOptionSound
      )
    }

    override fun userNotificationCenter(
      center: UNUserNotificationCenter,
      didReceiveNotificationResponse: UNNotificationResponse,
      withCompletionHandler: () -> Unit,
    ) {
      val info = didReceiveNotificationResponse.notification.request.content.userInfo
      val bike = (info[IosNotifier.BIKE] as? String)?.toLongOrNull()
      if (
        didReceiveNotificationResponse.actionIdentifier == IosNotifier.SNOOZE_ACTION && bike != null
      ) {
        scope.launch {
          try {
            graph.alerts.snoozePreRide(BikeId(bike), tomorrowAtFive())
          } finally {
            withCompletionHandler()
          }
        }
        return
      }
      (info[IosNotifier.LINK] as? String)?.let(::open)
      withCompletionHandler()
    }
  }

  /** "Snooze until tomorrow": quiet until 5:00 tomorrow, local time, as on Android. */
  private fun tomorrowAtFive(): Instant {
    val calendar = NSCalendar.currentCalendar
    val tomorrow = calendar.dateByAddingUnit(NSCalendarUnitDay, 1, NSDate(), 0u)!!
    val five =
      calendar.dateBySettingHour(5, minute = 0, second = 0, ofDate = tomorrow, options = 0u)!!
    return Instant.fromEpochMilliseconds((five.timeIntervalSince1970 * 1000).toLong())
  }
}
