package com.r0adkll.cassette.common.compose

import androidx.compose.runtime.staticCompositionLocalOf

/** The runtime permissions Cassette asks for, each with its own explainer (decision 0011). */
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
  fun state(permission: AppPermission): PermissionState

  fun request(permission: AppPermission, onResult: (PermissionState) -> Unit)

  /** Opens the app's system settings, for a blocked permission. */
  fun openSettings()
}

val LocalPermissionController =
  staticCompositionLocalOf<PermissionController> {
    object : PermissionController {
      override fun state(permission: AppPermission) = PermissionState.Granted

      override fun request(permission: AppPermission, onResult: (PermissionState) -> Unit) =
        onResult(PermissionState.Granted)

      override fun openSettings() = Unit
    }
  }
