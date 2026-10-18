plugins { id("cassette.ui") }

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(project(":features:settings:api"))
      implementation(project(":features:bikes:api"))
      // Words bikes' status as the bike screen does, for the garage.
      implementation(project(":features:bikes:summary"))
      implementation(project(":features:capture:api"))
    }
    commonTest.dependencies { implementation(project(":features:settings:test")) }
    // Renders screens to PNG for review without a device (build/screens/).
    jvmTest.dependencies { implementation(compose.desktop.currentOs) }
  }
}
