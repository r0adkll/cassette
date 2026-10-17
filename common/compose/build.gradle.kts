plugins {
  id("cassette.android.library")
  id("cassette.multiplatform")
  id("cassette.compose")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(libs.compose.runtime)
      api(libs.compose.foundation)
      api(libs.compose.ui)
      api(libs.compose.material3)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.ui.tooling.preview)
    }
    commonTest.dependencies { implementation(libs.kotlin.test) }
    jvmTest.dependencies { implementation(compose.desktop.currentOs) }
  }
}

compose.resources {
  packageOfResClass = "com.r0adkll.cassette.common.compose.resources"
  publicResClass = false
}
