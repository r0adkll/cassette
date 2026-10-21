package com.r0adkll.cassette.settings.api

/**
 * The open-source libraries this build ships, for Settings → About → Open-source licences. Each app
 * collects them at build time with the AboutLibraries Gradle plugin and binds this to read that
 * export.
 */
fun interface LibraryDefinitions {
  /** The AboutLibraries export as JSON: the libraries and the licences they name. */
  suspend fun json(): String
}
