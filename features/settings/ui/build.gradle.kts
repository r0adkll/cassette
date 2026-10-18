plugins { id("cassette.ui") }

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(project(":features:settings:api"))
      implementation(project(":features:bikes:api"))
      implementation(project(":features:capture:api"))
    }
    // Renders screens to PNG for review without a device (build/screens/).
    jvmTest.dependencies { implementation(compose.desktop.currentOs) }
  }
}
