package com.r0adkll.cassette.common.compose

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The runtime permissions Cassette asks for, explained together on one screen (decision 0011). */
enum class AppPermission {
  /** Bluetooth scan + connect ("Nearby devices"); never location. */
  Nearby,
  Notifications,
}

enum class PermissionState {
  Granted,
  /** Not granted, and asking will show the system prompt. */
  Askable,
  /** Denied for good: only the app's system settings can change it. */
  Blocked,
}

/** Requests permissions from the UI layer; the platform provides it (Android: MainActivity). */
interface PermissionController {
  /**
   * Every permission's state. Updates after a request, and when the app comes back to the
   * foreground (the user may have changed it in system settings).
   */
  val states: StateFlow<Map<AppPermission, PermissionState>>

  /** Shows the system prompt; the result arrives through [states]. */
  fun request(permission: AppPermission)

  /** Opens the app's system settings, for a blocked permission. */
  fun openSettings()
}

/** A controller whose states only change when told to. For previews and tests. */
class FixedPermissionController(
  states: Map<AppPermission, PermissionState> =
    AppPermission.entries.associateWith { PermissionState.Granted }
) : PermissionController {
  override val states = MutableStateFlow(states)

  override fun request(permission: AppPermission) = Unit

  override fun openSettings() = Unit
}

val LocalPermissionController =
  staticCompositionLocalOf<PermissionController> { FixedPermissionController() }
