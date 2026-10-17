plugins {
  id("cassette.android.library")
  id("cassette.multiplatform")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":protocol"))
      api(libs.kotlinx.coroutines.core)
    }
    commonTest.dependencies { implementation(libs.kotlin.test) }
  }
}
