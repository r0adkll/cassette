package com.r0adkll.cassette.surfaces.widgetkit

import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The Live Activity while a refresh runs (decision 0017, iteration 16): follows the latest refresh,
 * whichever surface started it, and hands each new state to Swift, which starts, updates and ends
 * the ActivityKit activity. The countdown draws itself from the state's end time, so a state only
 * changes when a component answers, Add 60 s moves the end, or the scan ends. Starts and ends go
 * into the capture log.
 */
@SingleIn(AppScope::class)
@Inject
class RefreshActivityPublisher(
  private val capture: CaptureController,
  private val repository: BikeRepository,
  private val log: CaptureLog,
  private val clock: Clock,
) {
  /**
   * [apply] takes [RefreshActivityCodec]'s JSON and returns null, or why ActivityKit refused (Live
   * Activities turned off, say). Only Swift can call ActivityKit.
   */
  fun start(scope: CoroutineScope, apply: (String) -> String?) {
    scope.launch {
      val defaultBike = repository.observeOverview(null).map { it?.bike?.id }.distinctUntilChanged()
      var last: RefreshActivityState? = null
      combine(capture.latestRefresh.filterNotNull(), repository.observeOverviews(), defaultBike) {
          run,
          overviews,
          default ->
          refreshActivityState(run, overviews, default, capture.isReady)
        }
        .filterNotNull()
        .distinctUntilChanged()
        .collect { state ->
          val previous = last
          val sameRun = previous?.startedAt == state.startedAt
          // An ended activity stays ended; the last refresh's end isn't replayed at launch either.
          if (sameRun && previous.ended) return@collect
          if (!sameRun && state.ended) return@collect
          val failure = apply(RefreshActivityCodec.encode(state))
          // Not started: leave this refresh alone rather than retrying every second.
          last = if (failure != null && !sameRun) state.copy(ended = true) else state
          when {
            failure != null && !sameRun -> log("Not started: $failure", ok = false)
            failure != null -> log("Update failed: $failure", ok = false)
            !sameRun -> log("Started for bike ${state.bikeId}", ok = true)
            state.ended ->
              log("Ended: ${state.phase}, ${state.heard} of ${state.components.size}", ok = true)
          }
        }
    }
  }

  private suspend fun log(detail: String, ok: Boolean) =
    log.append(CaptureEvent.LiveActivity(clock.now().toEpochMilliseconds(), detail, ok))
}
