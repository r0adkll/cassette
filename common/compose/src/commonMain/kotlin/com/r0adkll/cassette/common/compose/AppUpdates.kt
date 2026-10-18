package com.r0adkll.cassette.common.compose

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether a newer build of the app is waiting (decision 0008: builds come from Drop). */
sealed interface UpdateStatus {
  /** This build doesn't update itself (store or local builds): nothing to show. */
  data object Off : UpdateStatus

  data object Checking : UpdateStatus

  data object UpToDate : UpdateStatus

  data class Available(val build: AvailableBuild) : UpdateStatus

  data class Failed(val message: String) : UpdateStatus
}

/** A build waiting on Drop, as the App updates page shows it (iteration 13). */
data class AvailableBuild(
  /** The build number (its version code). */
  val number: Long,
  /** What changed, in a line: the pull request's title. */
  val headline: String,
  val sizeBytes: Long,
  val uploadedAt: Instant,
  /** The release notes, a change per line; null when the build has none. */
  val whatsNew: String?,
  val pullRequest: Int?,
  val channel: String,
  /** Null when it installs over this build; otherwise why only a fresh install takes it. */
  val reinstall: ReinstallReason? = null,
)

/** Why Android can only install a build fresh, which clears the app's data. */
enum class ReinstallReason {
  /** Signed with a different key. */
  DifferentKey,

  /** Its build number is lower than the installed one's. */
  LowerBuild,
  Unknown,
}

/** In-app updates for the UI layer; the platform provides it (Android: Drop, in MainActivity). */
interface AppUpdates {
  val status: StateFlow<UpdateStatus>

  /** This install's build number, when the build updates itself. */
  val installedBuild: Long?

  /** When the last check finished; null before the first. */
  val lastChecked: StateFlow<Instant?>

  fun check()

  /** Shows the update sheet, which downloads and installs the waiting build. */
  fun open()
}

val LocalAppUpdates =
  staticCompositionLocalOf<AppUpdates> {
    object : AppUpdates {
      override val status = MutableStateFlow(UpdateStatus.Off)

      override val installedBuild: Long? = null

      override val lastChecked = MutableStateFlow<Instant?>(null)

      override fun check() = Unit

      override fun open() = Unit
    }
  }
