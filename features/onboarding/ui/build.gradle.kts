plugins { id("cassette.ui") }

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(project(":features:settings:api"))
      implementation(project(":features:capture:api"))
    }
    commonTest.dependencies { implementation(project(":features:settings:test")) }
    jvmTest.dependencies { implementation(compose.desktop.currentOs) }
  }
}
