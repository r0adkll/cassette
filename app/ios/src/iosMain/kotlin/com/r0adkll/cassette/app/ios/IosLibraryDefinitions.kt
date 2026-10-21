package com.r0adkll.cassette.app.ios

import com.r0adkll.cassette.app.ios.resources.Res
import com.r0adkll.cassette.settings.api.LibraryDefinitions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Reads the library list the AboutLibraries plugin exports into the framework's resources. */
@ContributesBinding(AppScope::class)
@Inject
class IosLibraryDefinitions : LibraryDefinitions {
  override suspend fun json(): String = Res.readBytes("files/aboutlibraries.json").decodeToString()
}
