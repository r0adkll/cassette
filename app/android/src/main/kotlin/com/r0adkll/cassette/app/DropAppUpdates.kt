package com.r0adkll.cassette.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.r0adkll.cassette.common.compose.AppUpdates
import com.r0adkll.cassette.common.compose.UpdateStatus
import com.r0adkll.drop.Drop
import com.r0adkll.drop.UpdateCheckResult
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
) : AppUpdates {
  override val status = MutableStateFlow(if (enabled) UpdateStatus.UpToDate else UpdateStatus.Off)

  var sheetOpen by mutableStateOf(false)
    private set

  private var checking: Job? = null

  override fun check() {
    if (!enabled || checking?.isActive == true) return
    status.value = UpdateStatus.Checking
    checking = scope.launch {
      status.value =
        when (val result = drop.check()) {
          is UpdateCheckResult.Available -> UpdateStatus.Available(result.update.headline)
          is UpdateCheckResult.ReinstallRequired -> UpdateStatus.Available(result.update.headline)
          is UpdateCheckResult.ChannelEnded ->
            result.update?.let { UpdateStatus.Available(it.headline) } ?: UpdateStatus.UpToDate
          UpdateCheckResult.UpToDate -> UpdateStatus.UpToDate
          // A build from a local machine isn't known to Drop: nothing to update to.
          UpdateCheckResult.UnknownProduct -> UpdateStatus.UpToDate
          UpdateCheckResult.SignInRequired -> UpdateStatus.Failed("Sign in to Drop to check")
          is UpdateCheckResult.Failed -> UpdateStatus.Failed(result.message)
        }
    }
  }

  override fun open() {
    if (enabled) sheetOpen = true
  }

  fun closeSheet() {
    sheetOpen = false
    check()
  }

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
