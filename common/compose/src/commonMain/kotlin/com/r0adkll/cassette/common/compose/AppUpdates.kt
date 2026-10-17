package com.r0adkll.cassette.common.compose

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether a newer build of the app is waiting (decision 0008: builds come from Drop). */
sealed interface UpdateStatus {
  /** This build doesn't update itself (store or local builds): nothing to show. */
  data object Off : UpdateStatus

  data object Checking : UpdateStatus

  data object UpToDate : UpdateStatus

  /** [headline] says what changed, e.g. the pull request title. */
  data class Available(val headline: String) : UpdateStatus

  data class Failed(val message: String) : UpdateStatus
}

/** In-app updates for the UI layer; the platform provides it (Android: Drop, in MainActivity). */
interface AppUpdates {
  val status: StateFlow<UpdateStatus>

  fun check()

  /** Shows the update sheet, which downloads and installs the waiting build. */
  fun open()
}

val LocalAppUpdates =
  staticCompositionLocalOf<AppUpdates> {
    object : AppUpdates {
      override val status = MutableStateFlow(UpdateStatus.Off)

      override fun check() = Unit

      override fun open() = Unit
    }
  }
