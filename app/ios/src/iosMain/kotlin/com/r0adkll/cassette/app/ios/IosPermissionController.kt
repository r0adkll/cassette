package com.r0adkll.cassette.app.ios

import com.r0adkll.cassette.common.compose.AppPermission
import com.r0adkll.cassette.common.compose.PermissionController
import com.r0adkll.cassette.common.compose.PermissionState
import com.r0adkll.cassette.data.ble.BluetoothState
import com.r0adkll.cassette.data.ble.IosCentral
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Bluetooth and notification permissions for the UI layer (decision 0017). iOS asks for Bluetooth
 * when the central manager is first created, and each permission only once: after that, only the
 * Settings app can change it. States are re-read whenever the app becomes active.
 */
@SingleIn(AppScope::class)
@Inject
class IosPermissionController(private val central: IosCentral) : PermissionController {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  override val states =
    MutableStateFlow(
      mapOf(
        AppPermission.Nearby to central.state.value.permission(),
        AppPermission.Notifications to PermissionState.Askable,
      )
    )

  init {
    scope.launch {
      central.state.collect { state -> set(AppPermission.Nearby, state.permission()) }
    }
    readNotifications()
    NSNotificationCenter.defaultCenter.addObserverForName(
      UIApplicationDidBecomeActiveNotification,
      null,
      NSOperationQueue.mainQueue,
    ) { _ ->
      readNotifications()
    }
  }

  override fun request(permission: AppPermission) {
    when (permission) {
      AppPermission.Nearby -> central.requestPermission()
      AppPermission.Notifications ->
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
          UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { _, _ ->
          readNotifications()
        }
    }
  }

  override fun openSettings() {
    val url = NSURL(string = UIApplicationOpenSettingsURLString)
    UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any?>(), null)
  }

  private fun readNotifications() {
    UNUserNotificationCenter.currentNotificationCenter()
      .getNotificationSettingsWithCompletionHandler { settings ->
        val state =
          when (settings?.authorizationStatus) {
            UNAuthorizationStatusNotDetermined -> PermissionState.Askable
            UNAuthorizationStatusDenied -> PermissionState.Blocked
            else -> PermissionState.Granted
          }
        scope.launch { set(AppPermission.Notifications, state) }
      }
  }

  private fun set(permission: AppPermission, state: PermissionState) = states.update {
    it + (permission to state)
  }

  private fun BluetoothState.permission(): PermissionState =
    when (this) {
      BluetoothState.NotAsked -> PermissionState.Askable
      BluetoothState.Denied -> PermissionState.Blocked
      BluetoothState.Off,
      BluetoothState.On,
      BluetoothState.Starting -> PermissionState.Granted
    }
}
