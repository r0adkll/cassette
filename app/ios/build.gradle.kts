plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("cassette.compose")
  id("cassette.di")
}

// The iPhone app's Kotlin half (decision 0016): the graph and the Compose host, built as the static
// CassetteKit framework that the Xcode project in Cassette/ links.
kotlin {
  compilerOptions { freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime") }

  listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
      baseName = "CassetteKit"
      isStatic = true
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
      implementation(project(":surfaces:wear:sync"))
      implementation(libs.circuit.foundation)
      implementation(libs.circuitx.gesture.navigation)
      implementation(libs.circuit.codegen.annotations)
      implementation(libs.metro.runtime)
    }
  }
}
