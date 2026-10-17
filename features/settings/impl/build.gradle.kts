plugins {
  id("cassette.android.library")
  id("cassette.multiplatform")
  id("cassette.di")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":features:settings:api"))
      implementation(libs.androidx.datastore.preferences.core)
      implementation(libs.okio)
      implementation(libs.metro.runtime)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
