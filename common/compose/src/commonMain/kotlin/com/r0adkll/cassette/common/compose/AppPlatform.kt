package com.r0adkll.cassette.common.compose

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The phone's OS, for the few words and rows that differ between the apps (decision 0016). Read it
 * through [LocalAppPlatform] so previews and screen renders can pick one.
 */
enum class AppPlatform {
  Android,
  Ios;

  /** What the system calls the Bluetooth permission. */
  val bluetoothPermission: String
    get() =
      when (this) {
        Android -> "Nearby devices"
        Ios -> "Bluetooth"
      }
}

internal expect val currentAppPlatform: AppPlatform

val LocalAppPlatform = staticCompositionLocalOf { currentAppPlatform }
