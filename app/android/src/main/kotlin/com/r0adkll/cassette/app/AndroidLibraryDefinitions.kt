package com.r0adkll.cassette.app

import android.app.Application
import com.r0adkll.cassette.settings.api.LibraryDefinitions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the library list the AboutLibraries plugin generates for each variant into `res/raw`. */
@ContributesBinding(AppScope::class)
@Inject
class AndroidLibraryDefinitions(private val application: Application) : LibraryDefinitions {
  override suspend fun json(): String =
    withContext(Dispatchers.IO) {
      application.resources.openRawResource(R.raw.aboutlibraries).use {
        it.readBytes().decodeToString()
      }
    }
}
