// The Data Layer contract shared by the phone and the watch (decision 0013).
plugins {
  id("cassette.android.library")
  id("cassette.multiplatform")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":features:bikes:api"))
      implementation(libs.kotlinx.serialization.json)
    }
    commonTest.dependencies { implementation(libs.kotlin.test) }
  }
}
