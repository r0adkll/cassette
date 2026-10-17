plugins {
  id("cassette.android.library")
  id("cassette.multiplatform")
  id("cassette.di")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":features:alerts:api"))
      implementation(project(":data:db"))
      implementation(project(":features:settings:api"))
      implementation(libs.metro.runtime)
    }
    androidMain.dependencies {
      implementation(libs.androidx.core.ktx)
      implementation(libs.androidx.work.runtime)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
      implementation(project(":features:settings:test"))
    }
  }
}
