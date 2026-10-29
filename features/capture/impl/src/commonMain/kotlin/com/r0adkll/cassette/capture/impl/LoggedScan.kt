package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.data.ble.AdvertScanner
import com.r0adkll.cassette.protocol.AxsReading
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * [AdvertScanner.live] with its start and end in the capture log: how long it ran and what it
 * heard, or why it failed. Callers that swallow a scan's failure (Add bike) still leave a trace.
 */
internal fun AdvertScanner.loggedLive(
  purpose: String,
  log: CaptureLog,
  clock: Clock,
): Flow<AxsReading> = flow {
  val started = clock.now()
  var adverts = 0
  val devices = mutableSetOf<String>()
  log.append(CaptureEvent.Scan(started.toEpochMilliseconds(), purpose, "started", ok = true))
  emitAll(
    live()
      .onEach {
        adverts++
        devices += it.key
      }
      .onCompletion { cause ->
        val now = clock.now()
        val heard =
          "${(now - started).inWholeSeconds} s, $adverts adverts from ${devices.size} devices"
        val event =
          if (cause == null || cause is CancellationException)
            CaptureEvent.Scan(
              now.toEpochMilliseconds(),
              purpose,
              "stopped after $heard",
              adverts > 0,
            )
          else
            CaptureEvent.Scan(
              now.toEpochMilliseconds(),
              purpose,
              "failed after $heard: ${cause.message ?: cause::class.simpleName}",
              ok = false,
            )
        // A scan usually ends by being cancelled; the log still gets its line.
        withContext(NonCancellable) { log.append(event) }
      }
  )
}
