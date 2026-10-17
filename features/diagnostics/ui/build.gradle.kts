plugins { id("cassette.ui") }

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(project(":features:settings:api"))
      implementation(project(":features:bikes:api"))
      implementation(project(":features:capture:api"))
      implementation(project(":features:alerts:api"))
    }
    jvmTest.dependencies { implementation(compose.desktop.currentOs) }
  }
}
