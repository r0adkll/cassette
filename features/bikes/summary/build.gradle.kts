// The bike's status and component rows as words and tones: shared by the phone's screens, widgets
// and tile, and by the watch (decision 0013), so they all say the same thing.
plugins {
  id("cassette.android.library")
  id("cassette.multiplatform")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":features:bikes:api"))
      // StatusTone.
      api(project(":common:compose"))
    }
    commonTest.dependencies { implementation(libs.kotlin.test) }
  }
}
