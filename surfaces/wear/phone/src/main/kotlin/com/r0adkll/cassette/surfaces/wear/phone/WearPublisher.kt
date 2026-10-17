package com.r0adkll.cassette.surfaces.wear.phone

import android.app.Application
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.capture.api.SharedRefresh
import com.r0adkll.cassette.capture.api.WearAction
import com.r0adkll.cassette.surfaces.wear.sync.WearCodec
import com.r0adkll.cassette.surfaces.wear.sync.WearPaths
import com.r0adkll.cassette.surfaces.wear.sync.WearRefresh
import com.r0adkll.cassette.surfaces.wear.sync.WearSnapshot
import com.r0adkll.cassette.surfaces.wear.sync.wearSnapshot
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Sends the phone's bikes to the watch (decision 0013): a snapshot whenever the data changes in
 * this process (captures, including background wakes, which start the process; added or removed
 * bikes; a new default bike), and the shared refresh's state while it runs. Debounced like the
 * widgets, since a hub read writes several rows.
 */
@SingleIn(AppScope::class)
@Inject
class WearPublisher(
  private val application: Application,
  private val repository: BikeRepository,
  private val refresh: SharedRefresh,
  private val log: CaptureLog,
  private val clock: Clock,
) {
  private val dataClient by lazy { Wearable.getDataClient(application) }
  private val lock = Mutex()
  /** Logged once per change, so a phone without Wear doesn't fill the capture log. */
  private var lastFailure: String? = null

  @OptIn(FlowPreview::class)
  fun start(scope: CoroutineScope) {
    scope.launch {
      snapshots().debounce(1.seconds).distinctUntilChanged().collect { publish(it) }
    }
    scope.launch {
      refresh.progress
        .map { if (it is RefreshProgress.Scanning) it.secondsLeft else null }
        .distinctUntilChanged { old, new -> (old == null) == (new == null) }
        // Nothing to say until a refresh starts; the watch expires an old scan by its end time.
        .dropWhile { it == null }
        .collect { secondsLeft ->
          val now = clock.now()
          publishRefresh(
            if (secondsLeft == null) WearRefresh(WearRefresh.State.Idle, at = now.ms)
            else
              WearRefresh(
                WearRefresh.State.Scanning,
                until = (now + secondsLeft.seconds).ms,
                at = now.ms,
              )
          )
        }
    }
  }

  /** The phone can't scan: Bluetooth is off or Cassette isn't allowed to. */
  suspend fun publishNotReady() =
    publishRefresh(WearRefresh(WearRefresh.State.NotReady, at = clock.now().ms))

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun snapshots(): Flow<WearSnapshot> {
    val overviews =
      repository.observeBikes().flatMapLatest { bikes ->
        if (bikes.isEmpty()) flowOf(emptyList())
        else
          combine(bikes.map { repository.observeOverview(it.id) }) {
            it.filterNotNull<BikeOverview>()
          }
      }
    val defaultBike = repository.observeOverview(null).map { it?.bike?.id }.distinctUntilChanged()
    return combine(overviews, defaultBike, ::wearSnapshot)
  }

  private suspend fun publish(snapshot: WearSnapshot) {
    val sent = put(WearPaths.SNAPSHOT, WearCodec.encode(snapshot))
    if (sent) {
      log(WearAction.Published, "${snapshot.bikes.size} bikes")
      lastFailure = null
    }
  }

  private suspend fun publishRefresh(state: WearRefresh) {
    put(WearPaths.REFRESH, WearCodec.encode(state))
  }

  private suspend fun put(path: String, bytes: ByteArray): Boolean = lock.withLock {
    try {
      dataClient.putDataItem(PutDataRequest.create(path).setData(bytes).setUrgent()).await()
      true
    } catch (e: ApiException) {
      // API_UNAVAILABLE (17) when the phone has no Wear OS app.
      val failure = "Failed: ${e.statusCode} ${e.message.orEmpty()}".trim()
      if (failure != lastFailure) {
        lastFailure = failure
        log(WearAction.Published, failure)
      }
      false
    }
  }

  private suspend fun log(action: WearAction, detail: String) =
    log.append(CaptureEvent.Wear(clock.now().ms, action, detail))
}

private val kotlin.time.Instant.ms: Long
  get() = toEpochMilliseconds()
