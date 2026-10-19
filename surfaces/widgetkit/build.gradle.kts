// What the iPhone's WidgetKit widgets read (decision 0017): the bikes, worded by the shared summary
// code in the app, written to the App Group for the Swift widget extension.
plugins {
  id("cassette.multiplatform")
  id("cassette.di")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":features:bikes:api"))
      implementation(project(":features:bikes:summary"))
      implementation(project(":features:capture:api"))
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.metro.runtime)
    }
    commonTest.dependencies { implementation(libs.kotlin.test) }
  }
}
