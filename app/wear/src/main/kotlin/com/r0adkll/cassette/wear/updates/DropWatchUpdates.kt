package com.r0adkll.cassette.wear.updates

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.r0adkll.drop.Drop
import com.r0adkll.drop.DropState
import com.r0adkll.drop.UpdateCheckResult
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * [WatchUpdates] on Drop's SDK. Only the debug and internal builds have it (release gets Drop's
 * no-op and the Drop plugin writes no server URL), so a release build is always up to date.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DropWatchUpdates(private val application: Application, private val clock: Clock) :
  WatchUpdates {
  private val enabled = hasDrop(application)
  /** One per process, Drop's rule. */
  private val drop by lazy { Drop(application) }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val prefs = application.getSharedPreferences("updates", Context.MODE_PRIVATE)
  /**
   * Install as soon as the download is verified: set by [install], so a restored download waits.
   */
  private var installWhenReady = false

  override val installedBuild: Long =
    application.packageManager.getPackageInfo(application.packageName, 0).longVersionCode

  override val justUpdated: Boolean =
    prefs
      .getLong(LAST_BUILD, 0)
      .let { it in 1 until installedBuild }
      .also {
        prefs.edit().putLong(LAST_BUILD, installedBuild).apply()
      }

  override val state: StateFlow<WatchUpdate> =
    if (!enabled) MutableStateFlow(WatchUpdate.None)
    else
      drop.state.map { it.toWatchUpdate() }.stateIn(scope, SharingStarted.Eagerly, WatchUpdate.None)

  init {
    if (enabled) {
      scope.launch {
        drop.state.collect {
          if (it is DropState.ReadyToInstall && installWhenReady) {
            installWhenReady = false
            drop.install()
          }
        }
      }
    }
  }

  override fun check() {
    if (!enabled) return
    val last = Instant.fromEpochMilliseconds(prefs.getLong(LAST_CHECK, 0))
    if (clock.now() - last < 1.hours) return
    scope.launch {
      val result = drop.check()
      if (result !is UpdateCheckResult.Failed) {
        prefs.edit().putLong(LAST_CHECK, clock.now().toEpochMilliseconds()).apply()
      }
    }
  }

  override fun canInstall(): Boolean = application.packageManager.canRequestPackageInstalls()

  // App info on a watch, where the switch is under Advanced (spike, 2026-10-17).
  override fun openAllowSettings() {
    if (enabled) application.startActivity(drop.installPermissionIntent())
  }

  override fun install() {
    if (!enabled) return
    when (val current = drop.state.value) {
      is DropState.UpdateAvailable -> {
        installWhenReady = true
        drop.download(current.update)
      }
      is DropState.ReadyToInstall -> drop.install()
      is DropState.Failed ->
        current.update?.let {
          installWhenReady = true
          drop.download(it)
        }
      else -> Unit
    }
  }

  private companion object {
    const val LAST_CHECK = "last_check"
    const val LAST_BUILD = "last_build"

    /** The Drop plugin writes the server's URL into the manifest only for Drop's variants. */
    fun hasDrop(context: Context): Boolean =
      context.packageManager
        .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData
        ?.containsKey("com.r0adkll.drop.SERVER_URL") == true
  }
}

/** Drop's state as the watch shows it. A reinstall (different key, older code) isn't offered. */
internal fun DropState.toWatchUpdate(): WatchUpdate =
  when (this) {
    DropState.Idle -> WatchUpdate.None
    is DropState.UpdateAvailable ->
      if (update.requiresReinstall != null) WatchUpdate.None
      else WatchUpdate.Available(update.versionCode, update.fileSize, update.channel)
    // Downloaded and verified, waiting for Install.
    is DropState.ReadyToInstall ->
      WatchUpdate.Available(update.versionCode, update.fileSize, update.channel)
    is DropState.Downloading ->
      WatchUpdate.Downloading(update.versionCode, downloadedBytes, totalBytes)
    is DropState.Installing -> WatchUpdate.Installing(update.versionCode)
    is DropState.Failed -> WatchUpdate.Failed(update?.versionCode, message)
  }
