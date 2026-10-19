package com.r0adkll.cassette.common.compose

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * This install's version, for Settings → About and bug reports. The platform provides it (Android:
 * the package's version name and code; iOS: the bundle's short version and build).
 */
data class AppInfo(
  /** "0.1.0" */
  val version: String,
  /** The build number: Drop's version code on Android, CFBundleVersion on iOS. */
  val build: String,
) {
  /** "0.1.0 (build 40)" */
  val label: String
    get() = "$version (build $build)"
}

val LocalAppInfo = staticCompositionLocalOf { AppInfo(version = "dev", build = "0") }
