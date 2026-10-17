package com.r0adkll.cassette.capture.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Getting readings from bikes: the open-app refresh and the background scan (decision 0009). */
interface CaptureController {
  /** Bluetooth is on and Cassette has the Nearby devices permission. */
  val isReady: Boolean

  /**
   * A 60-second scan while the app is open: records every known component it hears and reads the
   * hub's battery table and the power meter. Completes when the time is up or everything was read.
   */
  fun refresh(): Flow<RefreshProgress>

  /** Arms the background scan when there's a bike to watch; stops it when there isn't. */
  suspend fun ensureBackgroundCapture(reason: String)
}

/**
 * The 60-second refresh for surfaces outside the app's screens (the Quick Settings tile, the
 * watch): one at a time, whichever surface asked, in a scope that outlives the caller.
 */
interface SharedRefresh {
  /** Scanning or not ready while a refresh runs; null otherwise. */
  val progress: StateFlow<RefreshProgress?>

  /** Starts a refresh, or joins the running one, and returns how it ended (done or not ready). */
  suspend fun run(): RefreshProgress
}

sealed interface RefreshProgress {
  data class Scanning(val secondsLeft: Int, val componentsHeard: Int) : RefreshProgress

  data class Done(val componentsHeard: Int, val hubRead: Boolean) : RefreshProgress

  data object NotReady : RefreshProgress
}
