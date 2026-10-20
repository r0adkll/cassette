package com.r0adkll.cassette.app.ios

import app.cash.sqldelight.db.SqlDriver
import com.r0adkll.cassette.alerts.api.Alerts
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.ui.StatusTitles
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.impl.CaptureLogFile
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.data.ble.IosCentral
import com.r0adkll.cassette.data.db.createNativeDriver
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.r0adkll.cassette.settings.impl.SettingsFile
import com.r0adkll.cassette.surfaces.watchkit.WatchPublisher
import com.r0adkll.cassette.surfaces.widgetkit.RefreshActivityPublisher
import com.r0adkll.cassette.surfaces.widgetkit.WidgetPublisher
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui
import com.slack.circuitx.gesturenavigation.GestureNavigationDecorationFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

@DependencyGraph(AppScope::class)
interface IosAppGraph {
  val circuit: Circuit
  val central: IosCentral
  val captureController: CaptureController
  val alerts: Alerts
  val settings: CassetteSettings
  val statusTitles: StatusTitles
  val bikeRepository: BikeRepository
  val permissions: IosPermissionController
  val widgets: WidgetPublisher
  val refreshActivity: RefreshActivityPublisher
  val watch: WatchPublisher

  @Provides
  @SingleIn(AppScope::class)
  fun provideCircuit(
    uiFactories: Set<Ui.Factory>,
    presenterFactories: Set<Presenter.Factory>,
  ): Circuit =
    Circuit.Builder()
      .addUiFactories(uiFactories)
      .addPresenterFactories(presenterFactories)
      // Swipe back from the edge, with the screen underneath showing.
      .setAnimatedNavDecoratorFactory(GestureNavigationDecorationFactory())
      .build()

  @Provides @SingleIn(AppScope::class) fun provideSqlDriver(): SqlDriver = createNativeDriver()

  @Provides
  fun provideCaptureLogFile(): CaptureLogFile = CaptureLogFile(unbackedFile("capture-log.jsonl"))

  @Provides
  fun provideSettingsFile(): SettingsFile = SettingsFile(appFile("settings.preferences_pb"))

  @Provides
  @SingleIn(AppScope::class)
  fun provideDispatchers(): CassetteDispatchers =
    CassetteDispatchers(io = Dispatchers.IO, default = Dispatchers.Default, main = Dispatchers.Main)

  @Provides fun provideClock(): Clock = Clock.System
}

/** Application Support: private to the app and backed up, like Android's files dir. */
@OptIn(ExperimentalForeignApi::class)
internal fun appFile(name: String): String {
  val dir =
    NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
      .first() as String
  NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
  return "$dir/$name"
}

/**
 * Application Support/Diagnostics: like [appFile], but kept out of iCloud and device backups
 * (decision 0018), as what it holds is about this phone. The folder carries the flag, since its
 * files get rewritten. A file from before it existed moves in.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun unbackedFile(name: String): String {
  val files = NSFileManager.defaultManager
  val dir = appFile("Diagnostics")
  files.createDirectoryAtPath(dir, true, null, null)
  NSURL.fileURLWithPath(dir, isDirectory = true)
    .setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
  val path = "$dir/$name"
  val old = appFile(name)
  if (files.fileExistsAtPath(old) && !files.fileExistsAtPath(path)) {
    files.moveItemAtPath(old, path, null)
  }
  return path
}
