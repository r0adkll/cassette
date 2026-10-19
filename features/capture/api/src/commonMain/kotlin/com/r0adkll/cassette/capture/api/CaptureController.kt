package com.r0adkll.cassette.capture.api

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

  /**
   * [refresh], a minute longer each time [more] emits (the tabletop's Add 60 s), with at most
   * [RefreshProgress.MaxSecondsLeft] to go. Cancelling the collection stops the scan.
   */
  fun refresh(more: Flow<Unit>): Flow<RefreshProgress> = refresh()

  /**
   * The latest refresh, whichever surface started it: running, or how it ended (it stays until the
   * next one starts). Null before the first. The iOS Live Activity follows it (decision 0017).
   */
  val latestRefresh: StateFlow<RefreshRun?>
    get() = NoRefresh

  /** Ends the running refresh early: it finishes as [RefreshProgress.Done] with `stopped` set. */
  fun stopRefresh() {}

  /**
   * A minute more for the running refresh, as its `more` flow gives (the Live Activity's Add 60 s).
   */
  fun addRefreshTime() {}

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
  /** [totalSeconds] is the scan's length so far: 60, plus a minute for each Add 60 s. */
  data class Scanning(val secondsLeft: Int, val componentsHeard: Int, val totalSeconds: Int = 60) :
    RefreshProgress

  /** [stopped]: ended early, by Stop. Surfaces go back to ready rather than showing the result. */
  data class Done(val componentsHeard: Int, val hubRead: Boolean, val stopped: Boolean = false) :
    RefreshProgress

  data object NotReady : RefreshProgress

  companion object {
    /** Add 60 s never leaves more than this to go, so a forgotten scan still ends. */
    const val MaxSecondsLeft = 180
  }
}

/** One refresh: when it started, its latest progress, and when it ended (null while it runs). */
data class RefreshRun(
  val startedAt: Instant,
  val progress: RefreshProgress,
  val endedAt: Instant? = null,
)

private val NoRefresh: StateFlow<RefreshRun?> = MutableStateFlow(null)
