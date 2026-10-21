package com.r0adkll.cassette.app.shared

import com.r0adkll.cassette.settings.api.CassetteSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Crashlytics' collection switch (decision 0020). Only the app modules hold Firebase, so each
 * passes its own; off also deletes reports not yet sent.
 */
fun interface CrashCollection {
  fun setEnabled(enabled: Boolean)
}

/**
 * Keeps [collection] in step with Settings → About → Send crash reports while [scope] lives.
 * Crashlytics remembers the last value, so a launch before the settings load keeps the person's
 * choice; the very first launch sends nothing until this runs (collection is off in the manifests).
 */
fun CassetteSettings.followCrashReports(scope: CoroutineScope, collection: CrashCollection): Job =
  crashReports.distinctUntilChanged().onEach(collection::setEnabled).launchIn(scope)
