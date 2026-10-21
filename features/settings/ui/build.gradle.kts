plugins { id("cassette.ui") }

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(project(":features:settings:api"))
      implementation(project(":features:bikes:api"))
      // Words bikes' status as the bike screen does, for the garage.
      implementation(project(":features:bikes:summary"))
      implementation(project(":features:capture:api"))
      // Reads the open-source licence list each app exports at build time.
      implementation(libs.aboutlibraries.core)
      implementation(libs.compose.components.resources)
    }
    commonTest.dependencies { implementation(project(":features:settings:test")) }
    // AboutLibraries parses with org.json on Android; host tests otherwise get android.jar's stubs,
    // which return null, so every export parsed empty.
    getByName("androidHostTest").dependencies { implementation(libs.org.json) }
    // Renders screens to PNG for review without a device (build/screens/).
    jvmTest.dependencies { implementation(compose.desktop.currentOs) }
  }
}

// The bundled fonts' and icons' notices (thirdparty/licenses/) for the Licences screen, synced into
// this module's resources as files/licenses/ so the repo keeps one copy.
val bundledLicences =
  tasks.register<Sync>("syncBundledLicences") {
    from(rootProject.layout.projectDirectory.dir("thirdparty/licenses"))
    into(layout.buildDirectory.dir("generated/bundledLicences/files/licenses"))
  }

compose.resources {
  packageOfResClass = "com.r0adkll.cassette.settings.ui.resources"
  customDirectory(
    sourceSetName = "commonMain",
    directoryProvider =
      bundledLicences.map { layout.buildDirectory.dir("generated/bundledLicences").get() },
  )
}
