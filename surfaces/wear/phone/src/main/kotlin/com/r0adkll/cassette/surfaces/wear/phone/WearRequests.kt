package com.r0adkll.cassette.surfaces.wear.phone

import android.app.Application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.capture.api.SharedRefresh
import com.r0adkll.cassette.capture.api.WearAction
import com.r0adkll.cassette.surfaces.wear.sync.ConnectedWatch
import com.r0adkll.cassette.surfaces.wear.sync.ConnectedWatches
import com.r0adkll.cassette.surfaces.wear.sync.WearPaths
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** What Wear sync needs from the app graph. */
@ContributesTo(AppScope::class)
interface WearComponent {
  val wearPublisher: WearPublisher
  val sharedRefresh: SharedRefresh
  val captureLog: CaptureLog
  val clock: Clock
}

/** Implemented by the Application so the listener service and worker reach the graph. */
interface WearHost {
  val wearComponent: WearComponent

  /** Lives as long as the process. */
  val wearScope: CoroutineScope
}

/** A watch asking the phone to refresh (decision 0013): the refresh runs as an expedited job. */
class WearRequestService : WearableListenerService() {
  override fun onMessageReceived(event: MessageEvent) {
    if (event.path != WearPaths.REQUEST_REFRESH) return
    WearRefreshWorker.enqueue(this)
    val host = applicationContext as WearHost
    host.wearScope.launch {
      val name =
        runCatching {
          Wearable.getNodeClient(applicationContext)
            .connectedNodes
            .await()
            .firstOrNull { it.id == event.sourceNodeId }
            ?.displayName
        }
          .getOrNull() ?: event.sourceNodeId
      val wear = host.wearComponent
      wear.captureLog.append(
        CaptureEvent.Wear(wear.clock.now().toEpochMilliseconds(), WearAction.RefreshRequested, name)
      )
    }
  }
}

/**
 * Runs the shared refresh for the watch. A job keeps the process up for the whole 60 seconds, which
 * a listener service, unbound as soon as the message is handled, doesn't.
 */
class WearRefreshWorker(context: Context, params: WorkerParameters) :
  CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val wear = (applicationContext as WearHost).wearComponent
    if (wear.sharedRefresh.run() is RefreshProgress.NotReady) wear.wearPublisher.publishNotReady()
    return Result.success()
  }

  companion object {
    private const val NAME = "wear-refresh"

    fun enqueue(context: Context) {
      WorkManager.getInstance(context)
        .enqueueUniqueWork(
          NAME,
          ExistingWorkPolicy.KEEP,
          OneTimeWorkRequestBuilder<WearRefreshWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build(),
        )
    }
  }
}

@ContributesBinding(AppScope::class)
@Inject
class DataLayerConnectedWatches(private val application: Application) : ConnectedWatches {
  override suspend fun list(): List<ConnectedWatch>? =
    try {
      val nodes = Wearable.getNodeClient(application).connectedNodes.await()
      val withCassette =
        Wearable.getCapabilityClient(application)
          .getCapability(WearPaths.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
          .await()
          .nodes
          .map { it.id }
          .toSet()
      nodes.map { ConnectedWatch(it.displayName, it.id in withCassette) }
    } catch (e: ApiException) {
      null
    }
}
