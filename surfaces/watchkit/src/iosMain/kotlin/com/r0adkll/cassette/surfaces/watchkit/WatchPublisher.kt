package com.r0adkll.cassette.surfaces.watchkit

import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.capture.api.SharedRefresh
import com.r0adkll.cassette.capture.api.WearAction
import com.r0adkll.cassette.surfaces.wear.sync.ConnectedWatch
import com.r0adkll.cassette.surfaces.wear.sync.ConnectedWatches
import com.r0adkll.cassette.surfaces.wear.sync.WearRefresh
import com.r0adkll.cassette.surfaces.widgetkit.WidgetSnapshot
import com.r0adkll.cassette.surfaces.widgetkit.widgetSnapshots
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import platform.Foundation.NSError
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import platform.WatchConnectivity.WCSession
import platform.WatchConnectivity.WCSessionActivationState
import platform.WatchConnectivity.WCSessionActivationStateActivated
import platform.WatchConnectivity.WCSessionDelegateProtocol
import platform.darwin.NSObject

/**
 * Sends the phone's bikes to the Apple Watch (decision 0017): the widgets' snapshot whenever the
 * data changes, and the shared refresh's state while it runs, as one WatchConnectivity application
 * context ([WatchContext]). Runs the refreshes the watch asks for, as Wear's listener does. Each
 * send and request goes into the capture log as Wear's do, so Diagnostics' watch panel reads them.
 */
@SingleIn(AppScope::class)
@Inject
class WatchPublisher(
  private val repository: BikeRepository,
  private val refresh: SharedRefresh,
  private val log: CaptureLog,
  private val clock: Clock,
) {
  private var snapshot: WidgetSnapshot? = null
  private var refreshState: WearRefresh? = null
  private var complicationKey: List<String>? = null
  private var complicationAt: Instant? = null
  /** Logged once per change, so a phone without a watch doesn't fill the capture log. */
  private var lastFailure: String? = null
  private lateinit var scope: CoroutineScope

  /** At launch, so a message from the watch can wake the app in the background. */
  fun start(scope: CoroutineScope) {
    if (!WCSession.isSupported()) return
    this.scope = scope
    WCSession.defaultSession.delegate = delegate
    WCSession.defaultSession.activateSession()
    scope.launch {
      repository.widgetSnapshots(clock).collect {
        snapshot = it
        send()
      }
    }
    scope.launch {
      refresh.progress
        .map { if (it is RefreshProgress.Scanning) it.secondsLeft else null }
        .distinctUntilChanged { old, new -> (old == null) == (new == null) }
        // Nothing to say until a refresh starts; the watch expires an old scan by its end time.
        .dropWhile { it == null }
        .collect { secondsLeft ->
          val now = clock.now()
          refreshState =
            if (secondsLeft == null) WearRefresh(WearRefresh.State.Idle, at = now.ms)
            else
              WearRefresh(
                WearRefresh.State.Scanning,
                until = (now + secondsLeft.seconds).ms,
                at = now.ms,
              )
          send()
        }
    }
  }

  @OptIn(ExperimentalForeignApi::class)
  private suspend fun send() {
    val session = WCSession.defaultSession
    // Sent again once activation completes.
    if (session.activationState != WCSessionActivationStateActivated) return
    val failure =
      when {
        !session.paired -> "No Apple Watch paired"
        !session.watchAppInstalled -> "Cassette isn't on the watch"
        else -> null
      }
    if (failure != null) {
      if (failure != lastFailure) {
        lastFailure = failure
        log(WearAction.Published, failure)
      }
      return
    }
    val context = WatchContext.build(snapshot, refreshState)
    val sent = session.updateApplicationContext(context as Map<Any?, *>, null)
    val snapshot = snapshot
    if (sent && snapshot != null) {
      pushComplications(session, context, snapshot)
      log(WearAction.Published, "${snapshot.bikes.size} bikes")
      lastFailure = null
    } else if (!sent && lastFailure != "Couldn't send") {
      lastFailure = "Couldn't send"
      log(WearAction.Published, "Failed: couldn't update the watch")
    }
  }

  /** Wakes the watch to redraw its complications when what they show changed. */
  private fun pushComplications(
    session: WCSession,
    context: Map<String, String>,
    snapshot: WidgetSnapshot,
  ) {
    if (!session.complicationEnabled || session.remainingComplicationUserInfoTransfers == 0uL)
      return
    val key = WatchContext.complicationKey(snapshot)
    val now = clock.now()
    val quiet = complicationAt?.let { now - it > 1.hours } ?: true
    if (key == complicationKey && !quiet) return
    session.transferCurrentComplicationUserInfo(context as Map<Any?, *>)
    complicationKey = key
    complicationAt = now
  }

  /** The watch's Refresh: the shared refresh, kept alive while the app is in the background. */
  private suspend fun runRefresh() {
    log(WearAction.RefreshRequested, "Apple Watch")
    val app = UIApplication.sharedApplication
    var task = UIBackgroundTaskInvalid
    task = app.beginBackgroundTaskWithName("watch-refresh") { app.endBackgroundTask(task) }
    try {
      if (refresh.run() is RefreshProgress.NotReady) {
        refreshState = WearRefresh(WearRefresh.State.NotReady, at = clock.now().ms)
        send()
      }
    } finally {
      app.endBackgroundTask(task)
    }
  }

  private suspend fun log(action: WearAction, detail: String) =
    log.append(CaptureEvent.Wear(clock.now().ms, action, detail))

  private val delegate =
    SessionDelegate(
      onActivated = { scope.launch { send() } },
      onRefreshRequested = { scope.launch { runRefresh() } },
    )
}

/** WatchConnectivity calls back on its own queue; the publisher hops to its scope. */
private class SessionDelegate(
  private val onActivated: () -> Unit,
  private val onRefreshRequested: () -> Unit,
) : NSObject(), WCSessionDelegateProtocol {
  override fun session(
    session: WCSession,
    activationDidCompleteWithState: WCSessionActivationState,
    error: NSError?,
  ) {
    if (activationDidCompleteWithState == WCSessionActivationStateActivated) onActivated()
  }

  override fun sessionDidBecomeInactive(session: WCSession) = Unit

  /** The user switched watches: activate again for the new one. */
  override fun sessionDidDeactivate(session: WCSession) {
    session.activateSession()
  }

  /** Cassette installed or removed on the watch, or a complication added. */
  override fun sessionWatchStateDidChange(session: WCSession) = onActivated()

  override fun session(
    session: WCSession,
    didReceiveMessage: Map<Any?, *>,
    replyHandler: (Map<Any?, *>?) -> Unit,
  ) {
    if (didReceiveMessage[WatchContext.REQUEST] == WatchContext.REQUEST_REFRESH) {
      replyHandler(mapOf(WatchContext.REPLY to WatchContext.REPLY_STARTED))
      onRefreshRequested()
    } else {
      replyHandler(emptyMap<Any?, Any?>())
    }
  }
}

/** Diagnostics' watch panel on iOS: the paired Apple Watch, and whether Cassette is on it. */
@ContributesBinding(AppScope::class)
@Inject
class PairedWatches : ConnectedWatches {
  override suspend fun list(): List<ConnectedWatch>? {
    if (!WCSession.isSupported()) return null
    val session = WCSession.defaultSession
    return if (session.paired) listOf(ConnectedWatch("Apple Watch", session.watchAppInstalled))
    else emptyList()
  }
}

private val Instant.ms: Long
  get() = toEpochMilliseconds()
