plugins {
  id("cassette.android.library")
  id("cassette.multiplatform")
  id("cassette.parcelize")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":core"))
      api(libs.circuit.runtime)
    }
    commonTest.dependencies { implementation(libs.kotlin.test) }
  }
}
