plugins {
  id("cassette.android.library")
  id("cassette.multiplatform")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":core"))
      api(libs.kotlinx.serialization.json)
    }
    commonTest.dependencies { implementation(libs.kotlin.test) }
  }
}
