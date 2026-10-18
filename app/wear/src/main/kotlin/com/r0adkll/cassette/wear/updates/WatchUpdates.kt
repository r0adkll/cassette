package com.r0adkll.cassette.wear.updates

import kotlinx.coroutines.flow.StateFlow

/**
 * Updating the watch app from Drop (decision 0013, iteration 9 direction A): the watch checks Drop
 * itself, offers the update under the bikes, and installs it when asked.
 */
interface WatchUpdates {
  val state: StateFlow<WatchUpdate>

  /** The build running now (its version code). */
  val installedBuild: Long

  /** True for the first run after an update, for "Updated to build N." once. */
  val justUpdated: Boolean

  /** Asks Drop for a newer Wear Build, at most once an hour. */
  fun check()

  /** Android lets the app install its own updates ("Install unknown apps"). */
  fun canInstall(): Boolean

  /** Opens the app's App info, where the Pixel Watch keeps the switch (under Advanced). */
  fun openAllowSettings()

  /** Downloads the offered update and installs it once it's verified. */
  fun install()
}

sealed interface WatchUpdate {
  /** Up to date, not checked yet, or a build without Drop. */
  data object None : WatchUpdate

  data class Available(val build: Long, val bytes: Long, val channel: String) : WatchUpdate

  data class Downloading(val build: Long, val downloaded: Long, val total: Long) : WatchUpdate {
    val progress: Float
      get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
  }

  data class Installing(val build: Long) : WatchUpdate

  data class Failed(val build: Long?, val message: String) : WatchUpdate
}

/** "4.1 MB". */
fun megabytes(bytes: Long): String {
  val tenths = (bytes + 50_000) / 100_000
  return "${tenths / 10}.${tenths % 10} MB"
}
