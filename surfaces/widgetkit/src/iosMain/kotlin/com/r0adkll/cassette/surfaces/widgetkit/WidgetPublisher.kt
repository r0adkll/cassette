package com.r0adkll.cassette.surfaces.widgetkit

import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToURL

/**
 * Writes the widgets' snapshot to the App Group whenever bike data changes in this process
 * (captures, including the background relaunches Bluetooth makes; added or removed bikes; a new
 * default bike), then asks WidgetKit to reload. Debounced like Android's widget updates, since a
 * hub read writes several rows. Once at start too, so a new install or update fills the widgets.
 */
@SingleIn(AppScope::class)
@Inject
class WidgetPublisher(
  private val repository: BikeRepository,
  private val log: CaptureLog,
  private val clock: Clock,
) {
  /** The last failure logged, so a missing App Group doesn't fill the capture log. */
  private var lastFailure: String? = null

  /** [reload] is WidgetKit's `reloadAllTimelines`, which only Swift can call. */
  @OptIn(FlowPreview::class)
  fun start(scope: CoroutineScope, reload: () -> Unit) {
    scope.launch {
      val defaultBike = repository.observeOverview(null).map { it?.bike?.id }.distinctUntilChanged()
      combine(repository.observeOverviews(), defaultBike, ::Pair)
        .debounce(1.seconds)
        .distinctUntilChanged()
        .collect { (overviews, default) ->
          val snapshot = widgetSnapshot(overviews, default, clock.now())
          if (write(WidgetCodec.encode(snapshot))) {
            reload()
            log("${snapshot.bikes.size} bikes", ok = true)
            lastFailure = null
          }
        }
    }
  }

  @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
  private suspend fun write(json: String): Boolean =
    withContext(Dispatchers.IO) {
      val failure = run {
        val group =
          NSBundle.mainBundle.objectForInfoDictionaryKey(APP_GROUP_KEY) as? String
            ?: return@run "No $APP_GROUP_KEY in Info.plist"
        val container =
          NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(group)
            ?: return@run "No container for $group (entitlement missing?)"
        val file = container.URLByAppendingPathComponent(FILE_NAME)!!
        val written =
          NSString.create(string = json)
            .writeToURL(file, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (written) null else "Couldn't write $FILE_NAME"
      }
      if (failure != null && failure != lastFailure) {
        lastFailure = failure
        log("Failed: $failure", ok = false)
      }
      failure == null
    }

  private suspend fun log(detail: String, ok: Boolean) =
    log.append(CaptureEvent.Widgets(clock.now().toEpochMilliseconds(), detail, ok))

  companion object {
    /** Set to the App Group's id in the app's and the extension's Info.plist. */
    const val APP_GROUP_KEY = "CassetteAppGroup"
    /** Read by the extension (Widgets/Snapshot.swift). */
    const val FILE_NAME = "widgets.json"
  }
}
