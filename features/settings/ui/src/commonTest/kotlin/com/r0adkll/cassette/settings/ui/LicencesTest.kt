package com.r0adkll.cassette.settings.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LicencesTest {
  private val catalog = licenceCatalog(EXPORT)

  @Test
  fun groupsLibrariesIntoProjectsBiggestFirst() {
    assertEquals(
      listOf("Compose Multiplatform", "AndroidX", "Example Runtime", "Mystery"),
      catalog.projects.map { it.name },
    )
    assertEquals(5, catalog.libraryCount)
    // Skiko and the JetBrains AndroidX forks are part of Compose Multiplatform.
    assertEquals(
      listOf("org.jetbrains.compose.ui:ui", "org.jetbrains.skiko:skiko"),
      catalog.project("Compose Multiplatform")!!.libraries.map { it.coordinates },
    )
  }

  @Test
  fun aLoneLibraryOutsideTheRulesIsItsOwnProject() {
    val example = catalog.project("Example Runtime")!!

    assertEquals("ER", example.code)
    assertEquals("A Developer", example.organisation)
    // No website in the POM: the source repository stands in.
    assertEquals("https://github.com/example/runtime", example.website)
    assertEquals(listOf("Apache-2.0", "MIT"), example.licences)
  }

  @Test
  fun listsLicencesMostUsedFirstWithTheProjectsThatUseThem() {
    val apache = catalog.licences.first()

    assertEquals(listOf("Apache License 2.0", "MIT License"), catalog.licences.map { it.name })
    assertEquals("Apache 2.0", apache.shortName)
    assertEquals("Apache text", apache.text)
    assertEquals(4, apache.libraryCount)
    assertEquals(listOf("Compose Multiplatform", "AndroidX", "Example Runtime"), apache.projects)
    assertNull(catalog.licence("MIT")!!.text)
  }

  @Test
  fun codesAreInitialsOrTheFirstTwoLetters() {
    assertEquals("CM", code("Compose Multiplatform"))
    assertEquals("KA", code("kaml"))
  }

  @Test
  fun summariesNameUpToFiveProjects() {
    assertEquals("AndroidX.", summary(listOf("AndroidX")))
    assertEquals("AndroidX, Kotlin and Ktor.", summary(listOf("AndroidX", "Kotlin", "Ktor")))
    assertEquals("A, B, C, D, E and 2 more.", summary(listOf("A", "B", "C", "D", "E", "F", "G")))
    assertNull(summary(emptyList()))
  }
}

/** The shape the AboutLibraries plugin exports, trimmed to what the screens read. */
private val EXPORT =
  """
  {
    "libraries": [
      {
        "uniqueId": "org.jetbrains.compose.ui:ui", "artifactVersion": "1.12.2", "name": "Compose UI",
        "developers": [], "licenses": ["Apache-2.0"],
        "organization": { "name": "Compose Multiplatform Team" }
      },
      {
        "uniqueId": "org.jetbrains.skiko:skiko", "artifactVersion": "0.9", "name": "Skiko",
        "developers": [], "licenses": ["Apache-2.0"]
      },
      {
        "uniqueId": "androidx.compose.ui:ui", "artifactVersion": "1.12.1", "name": "Compose UI",
        "developers": [], "licenses": ["Apache-2.0"], "website": "https://developer.android.com"
      },
      {
        "uniqueId": "io.github.example:runtime", "artifactVersion": "1.4.5", "name": "Example Runtime",
        "developers": [{ "name": "A Developer" }], "licenses": ["Apache-2.0", "MIT"],
        "scm": { "url": "https://github.com/example/runtime" }
      },
      { "uniqueId": "com.example:mystery", "artifactVersion": "1.0", "name": "Mystery", "developers": [] }
    ],
    "licenses": {
      "Apache-2.0": {
        "name": "Apache License 2.0", "url": "https://spdx.org/licenses/Apache-2.0.html",
        "spdxId": "Apache-2.0", "content": "Apache text\n", "hash": "Apache-2.0"
      },
      "MIT": { "name": "MIT License", "url": "https://spdx.org/licenses/MIT.html", "hash": "MIT" }
    }
  }
  """
