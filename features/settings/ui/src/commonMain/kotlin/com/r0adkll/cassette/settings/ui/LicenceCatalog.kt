package com.r0adkll.cassette.settings.ui

import androidx.compose.runtime.Immutable
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.r0adkll.cassette.settings.api.LibraryDefinitions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Everything the Licences screens show about the build's libraries. */
@Immutable
data class LicenceCatalog(val projects: List<LibraryProject>, val licences: List<LicenceInfo>) {
  val libraryCount: Int
    get() = projects.sumOf { it.libraries.size }

  fun project(name: String): LibraryProject? = projects.find { it.name == name }

  fun licence(key: String): LicenceInfo? = licences.find { it.key == key }
}

/** The libraries one project contributes (AndroidX's 86 artifacts are one row, not 86). */
@Immutable
data class LibraryProject(
  val name: String,
  /** Two letters for its [CodeBadge][com.r0adkll.cassette.common.compose.components.CodeBadge]. */
  val code: String,
  val organisation: String?,
  val website: String?,
  val libraries: List<OpenSourceLibrary>,
  /** Keys of the [LicenceInfo]s its libraries name, the most used first. */
  val licences: List<String>,
)

@Immutable
data class OpenSourceLibrary(
  val name: String,
  /** Maven `group:artifact`, which tells apart forks that share a name. */
  val coordinates: String,
  val version: String?,
  val licences: List<String>,
)

@Immutable
data class LicenceInfo(
  val key: String,
  val name: String,
  /** For lists: "Apache 2.0" rather than "Apache License 2.0". */
  val shortName: String,
  /** The full text, when the export carries it; otherwise [url] has it. */
  val text: String?,
  val url: String?,
  val libraryCount: Int,
  /** Names of the projects that use it, the biggest first. */
  val projects: List<String>,
)

/**
 * A font or icon set Cassette bundles by hand, so the export doesn't know it; its notice is [file]
 * in this module's resources (from `thirdparty/licenses/`).
 */
@Immutable
data class BundledWork(
  val key: String,
  val name: String,
  val code: String,
  val licence: String,
  val authors: String,
  val file: String,
)

internal val BUNDLED =
  listOf(
    BundledWork(
      "bundled:barlow",
      "Barlow, Barlow Condensed",
      "Aa",
      "SIL Open Font License 1.1",
      "The Barlow Project Authors",
      "files/licenses/Barlow-OFL.txt",
    ),
    BundledWork(
      "bundled:jetbrains-mono",
      "JetBrains Mono",
      "{ }",
      "SIL Open Font License 1.1",
      "The JetBrains Mono Project Authors",
      "files/licenses/JetBrainsMono-OFL.txt",
    ),
    BundledWork(
      "bundled:lucide",
      "Lucide",
      "LU",
      "ISC License",
      "Lucide Contributors; portions Cole Bemis (Feather, MIT)",
      "files/licenses/Lucide-LICENSE.txt",
    ),
  )

/** Parses the export once for all three Licences screens, and keeps it for the app's life. */
@SingleIn(AppScope::class)
@Inject
class LicenceCatalogStore(private val definitions: LibraryDefinitions) {
  private val mutex = Mutex()
  private var catalog: LicenceCatalog? = null

  suspend fun load(): LicenceCatalog = mutex.withLock {
    catalog
      ?: withContext(Dispatchers.Default) { licenceCatalog(definitions.json()) }
        .also { catalog = it }
  }
}

/**
 * Groups an AboutLibraries export into projects by Maven group ([PROJECTS]; any other group is its
 * own project), the biggest first, and lists the licences they name, the most used first.
 */
internal fun licenceCatalog(json: String): LicenceCatalog {
  val libs = Libs.Builder().withJson(json).build()
  val projects =
    libs.libraries
      .groupBy { library -> PROJECTS.find { it.matches(library.group) } }
      .flatMap { (rule, libraries) ->
        if (rule != null) listOf(project(rule.name, rule.code, rule.website, libraries))
        else
          libraries.groupBy { it.group }.map { (group, inGroup) -> fallbackProject(group, inGroup) }
      }
      .sortedWith(compareBy({ -it.libraries.size }, { it.name.lowercase() }))
  val licences =
    libs.libraries
      .flatMap { it.licenses }
      .distinctBy { it.hash }
      .map { licence ->
        val users = projects.filter { licence.hash in it.licences }
        LicenceInfo(
          key = licence.hash,
          name = licence.name,
          shortName = SHORT_NAMES[licence.hash] ?: SHORT_NAMES[licence.spdxId] ?: licence.name,
          text = licence.licenseContent?.takeIf { it.isNotBlank() }?.trim(),
          url = licence.url,
          libraryCount = users.sumOf { p -> p.libraries.count { licence.hash in it.licences } },
          projects = users.map { it.name },
        )
      }
      .sortedWith(compareBy({ -it.libraryCount }, { it.name }))
  return LicenceCatalog(projects, licences)
}

private val Library.group: String
  get() = uniqueId.substringBefore(':')

private fun project(
  name: String,
  code: String,
  website: String?,
  libraries: List<Library>,
): LibraryProject {
  val entries =
    libraries
      .map {
        OpenSourceLibrary(
          name = it.name,
          coordinates = it.uniqueId,
          version = it.artifactVersion,
          licences = it.licenses.map { licence -> licence.hash },
        )
      }
      .distinct()
      .sortedWith(compareBy({ it.name.lowercase() }, { it.coordinates }))
  return LibraryProject(
    name = name,
    code = code,
    organisation =
      libraries
        .mapNotNull { it.organization?.name ?: it.developers.firstOrNull()?.name }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key,
    website =
      website ?: libraries.mapNotNull { it.website ?: it.scm?.url }.distinct().singleOrNull(),
    libraries = entries,
    licences =
      entries
        .flatMap { it.licences }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key },
  )
}

/** A group no rule names: a lone library goes by its own name, more by the group. */
private fun fallbackProject(group: String, libraries: List<Library>): LibraryProject {
  val name = libraries.singleOrNull()?.name ?: group
  return project(name, code(name), website = null, libraries)
}

/** "Compose Multiplatform" is CM, "kaml" KA. */
internal fun code(name: String): String {
  val words = name.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
  return if (words.size >= 2) "${words[0][0]}${words[1][0]}".uppercase()
  else name.take(2).uppercase()
}

/**
 * A project that spans several Maven groups, or whose artifacts would otherwise list one by one.
 */
private class ProjectRule(
  val name: String,
  val code: String,
  val website: String?,
  val groups: List<String>,
  /** Groups that count only exactly, not their subgroups. */
  val exact: List<String> = emptyList(),
) {
  fun matches(group: String) =
    group in exact || groups.any { group == it || group.startsWith("$it.") }
}

// Compose Multiplatform comes before Kotlin: both live under org.jetbrains.
private val PROJECTS =
  listOf(
    ProjectRule(
      "AndroidX",
      "AX",
      "https://developer.android.com/jetpack/androidx",
      listOf("androidx"),
    ),
    ProjectRule(
      "Compose Multiplatform",
      "CM",
      "https://www.jetbrains.com/compose-multiplatform/",
      listOf("org.jetbrains.compose", "org.jetbrains.androidx", "org.jetbrains.skiko"),
    ),
    ProjectRule(
      "Kotlin",
      "KT",
      "https://kotlinlang.org",
      listOf("org.jetbrains.kotlin", "org.jetbrains.kotlinx"),
      exact = listOf("org.jetbrains"),
    ),
    ProjectRule("Ktor", "KR", "https://ktor.io", listOf("io.ktor")),
    ProjectRule("Circuit", "CI", "https://slackhq.github.io/circuit/", listOf("com.slack.circuit")),
    ProjectRule(
      "SQLDelight",
      "SQ",
      "https://sqldelight.github.io/sqldelight/",
      listOf("app.cash.sqldelight"),
    ),
    ProjectRule("Google Play services", "GP", null, listOf("com.google.android.gms")),
    ProjectRule("Drop", "DR", null, listOf("com.r0adkll.drop")),
    ProjectRule("Touchlab", "TL", null, listOf("co.touchlab")),
    ProjectRule(
      "kotlin-inject-anvil",
      "KI",
      null,
      listOf("software.amazon.lastmile.kotlin.inject.anvil"),
    ),
    // Single libraries whose POM names read badly in a list ("Guava ListenableFuture only").
    ProjectRule("AboutLibraries", "AL", null, listOf("com.mikepenz")),
    ProjectRule("Guava", "GU", null, listOf("com.google.guava")),
    ProjectRule("JSpecify", "JS", null, listOf("org.jspecify")),
    ProjectRule("kaml", "KA", null, listOf("com.charleskorn.kaml")),
    ProjectRule("Metro", "ME", null, listOf("dev.zacsweers.metro")),
    ProjectRule("OkHttp", "OH", null, listOf("com.squareup.okhttp3")),
    ProjectRule("Okio", "OK", null, listOf("com.squareup.okio")),
    ProjectRule("SLF4J", "SL", null, listOf("org.slf4j")),
    ProjectRule("SnakeYAML Engine", "SY", null, listOf("it.krzeminski")),
    ProjectRule("UrlEncoder", "UE", null, listOf("net.thauvin.erik.urlencoder")),
  )

private val SHORT_NAMES =
  mapOf(
    "Apache-2.0" to "Apache 2.0",
    "BSD-3-Clause" to "BSD 3-Clause",
    "MIT" to "MIT",
    "ASDKL" to "Android SDK License",
  )
