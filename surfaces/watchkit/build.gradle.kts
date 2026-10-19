// What the Apple Watch reads (decision 0017): the widgets' snapshot and the refresh's state, sent
// over WatchConnectivity, and the refreshes the watch asks the iPhone for.
plugins {
  id("cassette.multiplatform")
  id("cassette.di")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":surfaces:widgetkit"))
      implementation(project(":features:capture:api"))
      implementation(project(":surfaces:wear:sync"))
      implementation(libs.metro.runtime)
    }
    commonTest.dependencies { implementation(libs.kotlin.test) }
  }
}
