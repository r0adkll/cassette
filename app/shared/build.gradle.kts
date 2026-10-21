plugins {
  id("cassette.android.library")
  id("cassette.multiplatform")
  id("cassette.compose")
}

// What the Android and iOS apps host the same way: the theme, the first screens and the back stack.
kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":common:compose"))
      api(project(":common:screens"))
      api(project(":features:bikes:api"))
      api(project(":features:settings:api"))
      api(libs.circuit.foundation)
    }
    commonTest.dependencies {
      implementation(project(":features:settings:test"))
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
