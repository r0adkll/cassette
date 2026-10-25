package com.r0adkll.cassette.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.r0adkll.cassette.common.compose.AppUpdates
import com.r0adkll.cassette.common.compose.AvailableBuild
import com.r0adkll.cassette.common.compose.ReinstallReason
import com.r0adkll.cassette.common.compose.UpdateStatus
import com.r0adkll.drop.Drop
import com.r0adkll.drop.DropState
import com.r0adkll.drop.ReinstallReason as DropReinstallReason
import com.r0adkll.drop.Update
import com.r0adkll.drop.UpdateCheckResult
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * In-app updates from Drop. Only the debug and internal builds have Drop's SDK (the Gradle plugin
 * gives release a no-op), so anything else reports [UpdateStatus.Off]. MainActivity shows Drop's
 * update sheet while [sheetOpen].
 */
class DropAppUpdates(
  val drop: Drop,
  private val scope: CoroutineScope,
  private val enabled: Boolean,
  installedVersionCode: Long,
) : AppUpdates {
  override val status = MutableStateFlow(if (enabled) UpdateStatus.UpToDate else UpdateStatus.Off)

  override val installedBuild: Long? = installedVersionCode.takeIf { enabled }

  override val lastChecked = MutableStateFlow<Instant?>(null)

  var sheetOpen by mutableStateOf(false)
    private set

  private var checking: Job? = null

  override fun check() {
    if (!enabled || checking?.isActive == true) return
    status.value = UpdateStatus.Checking
    checking = scope.launch {
      status.value =
        when (val result = drop.check()) {
          is UpdateCheckResult.Available -> UpdateStatus.Available(result.update.toBuild())
          is UpdateCheckResult.ReinstallRequired ->
            UpdateStatus.Available(result.update.toBuild(reinstall = true))
          is UpdateCheckResult.ChannelEnded ->
            result.update?.let { UpdateStatus.Available(it.toBuild()) } ?: UpdateStatus.UpToDate
          UpdateCheckResult.UpToDate -> UpdateStatus.UpToDate
          // A build from a local machine isn't known to Drop: nothing to update to.
          UpdateCheckResult.UnknownProduct -> UpdateStatus.UpToDate
          UpdateCheckResult.SignInRequired -> UpdateStatus.Failed("Sign in to Drop to check")
          is UpdateCheckResult.Failed -> UpdateStatus.Failed(result.message)
          // Answers about one Build, which only checkBuild asks for.
          is UpdateCheckResult.OpenedInApp,
          UpdateCheckResult.AlreadyInstalled,
          is UpdateCheckResult.BuildExpired -> UpdateStatus.UpToDate
        }
      lastChecked.value = Clock.System.now()
    }
  }

  /**
   * Offers the Build that Open in App on a Drop Build page opened (`cassette://drop/{buildId}`,
   * decision 0008) in Drop's update sheet, whichever channel it's on: installing it moves this
   * install to that channel. The sheet also says when it's the installed Build or has expired.
   */
  fun checkBuild(buildId: String) {
    if (!enabled) return
    // Drop's sheet shows the last answer, so a check still running finishes first.
    val running = checking
    checking = scope.launch {
      running?.join()
      when (val result = drop.check(buildId)) {
        is UpdateCheckResult.OpenedInApp -> offer(result.update.toBuild())
        is UpdateCheckResult.Available -> offer(result.update.toBuild())
        is UpdateCheckResult.ReinstallRequired -> offer(result.update.toBuild(reinstall = true))
        // The status stays this channel's.
        UpdateCheckResult.AlreadyInstalled,
        is UpdateCheckResult.BuildExpired -> sheetOpen = true
        // Nothing for the sheet to say (a Build Drop has no copy of for this app, or no answer):
        // App updates says why.
        UpdateCheckResult.SignInRequired ->
          status.value = UpdateStatus.Failed("Sign in to Drop to check")
        is UpdateCheckResult.Failed -> status.value = UpdateStatus.Failed(result.message)
        // Answers to an Update check, not about one Build.
        UpdateCheckResult.UpToDate,
        UpdateCheckResult.UnknownProduct,
        is UpdateCheckResult.ChannelEnded -> Unit
      }
    }
  }

  private fun offer(build: AvailableBuild) {
    status.value = UpdateStatus.Available(build)
    sheetOpen = true
  }

  override fun open() {
    if (enabled) sheetOpen = true
  }

  fun closeSheet() {
    sheetOpen = false
    // A download the sheet started stays on offer: checking would put this channel's answer in
    // its place, which for a Build opened from another channel is a different build.
    when (drop.state.value) {
      DropState.Idle,
      is DropState.UpdateAvailable,
      is DropState.NotInstallable -> check()
      is DropState.Downloading,
      is DropState.ReadyToInstall,
      is DropState.Installing,
      is DropState.Failed -> Unit
    }
  }

  /** What the App updates page shows of a build (iteration 13). */
  private fun Update.toBuild(reinstall: Boolean = requiresReinstall != null) =
    AvailableBuild(
      number = versionCode,
      headline = headline,
      sizeBytes = fileSize,
      uploadedAt = uploadedAt,
      whatsNew = whatsNew?.takeIf { it.isNotBlank() },
      pullRequest = pullRequest,
      channel = channel,
      reinstall =
        if (!reinstall) null
        else
          when (requiresReinstall) {
            DropReinstallReason.Signer -> ReinstallReason.DifferentKey
            DropReinstallReason.VersionCode -> ReinstallReason.LowerBuild
            DropReinstallReason.Unknown,
            null -> ReinstallReason.Unknown
          },
    )

  companion object {
    /**
     * Whether this build has Drop's SDK: the plugin writes the server's URL into the manifest only
     * for those variants. Not "debuggable": the internal build Drop ships is R8'd and isn't.
     */
    fun hasDrop(context: Context): Boolean =
      context.packageManager
        .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData
        ?.containsKey("com.r0adkll.drop.SERVER_URL") == true
  }
}
