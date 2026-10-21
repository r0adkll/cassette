plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("cassette.compose")
  id("cassette.di")
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
    }
  }
}
