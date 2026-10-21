import com.r0adkll.cassette.convention.resolveRootModulesWhenUnrequested
import java.util.regex.Pattern

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("cassette.compose")
  id("cassette.di")
  alias(libs.plugins.aboutlibraries)
}

// The iPhone app's Kotlin half (decision 0017): the graph and the Compose host, built as the static
// CassetteKit framework that the Xcode project in Cassette/ links.
kotlin {
  compilerOptions { freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime") }

  listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
      baseName = "CassetteKit"
      isStatic = true
      // Line numbers for Kotlin frames in the app's dSYM, which Crashlytics symbolicates with
      // (decision 0020); release frameworks carry no debug info otherwise.
      freeCompilerArgs += "-Xadd-light-debug=enable"
    }
  }

  sourceSets {
    iosMain.dependencies {
      implementation(project(":app:shared"))
      implementation(project(":data:ble"))
      implementation(project(":data:db"))
      implementation(project(":features:alerts:impl"))
      implementation(project(":features:bikes:impl"))
      implementation(project(":features:bikes:ui"))
      implementation(project(":features:capture:api"))
      implementation(project(":features:capture:impl"))
      implementation(project(":features:diagnostics:ui"))
      implementation(project(":features:onboarding:ui"))
      implementation(project(":features:settings:impl"))
      implementation(project(":features:settings:ui"))
      implementation(project(":surfaces:watchkit"))
      implementation(project(":surfaces:wear:sync"))
      implementation(project(":surfaces:widgetkit"))
      implementation(libs.circuit.foundation)
      implementation(libs.circuitx.gesture.navigation)
      implementation(libs.circuit.codegen.annotations)
      implementation(libs.metro.runtime)
      // Kotlin crashes reach Crashlytics with Kotlin stacks (decision 0020). The framework is
      // static, so the app's link resolves Firebase and CrashKiOS's link plugin isn't needed.
      implementation(libs.crashkios.crashlytics)
      implementation(libs.compose.components.resources)
    }
  }
}

// Settings → About → Open-source licences: every library the framework links, exported at build
// time into its Compose resources as files/aboutlibraries.json.
val libraryDefinitions = layout.buildDirectory.dir("generated/aboutLibraries")

aboutLibraries {
  collect.fetchRemoteLicense = false
  // Native targets resolve klibs, not classpaths: take every configuration of the device target.
  collect.all = true
  collect.filterVariants.add("iosArm64")
  library.duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
  library.exclusionPatterns.add(Pattern.compile(".*-bom"))
  export.outputFile = libraryDefinitions.map { it.file("files/aboutlibraries.json") }
}

resolveRootModulesWhenUnrequested()

compose.resources {
  packageOfResClass = "com.r0adkll.cassette.app.ios.resources"
  // The only resources are iosMain's, which `auto` doesn't generate an accessor for.
  generateResClass = always
  customDirectory(
    sourceSetName = "iosMain",
    directoryProvider = tasks.named("exportLibraryDefinitions").map { libraryDefinitions.get() },
  )
}
