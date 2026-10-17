plugins { id("cassette.ui") }

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(project(":features:bikes:api"))
      api(project(":features:bikes:summary"))
      implementation(project(":features:capture:api"))
      api(project(":features:settings:api"))
    }
    commonTest.dependencies { implementation(project(":features:settings:test")) }
    // Renders screens to PNG for review without a device (build/screens/).
    jvmTest.dependencies { implementation(compose.desktop.currentOs) }
  }
}
