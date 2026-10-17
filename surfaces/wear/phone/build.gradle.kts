// Android-only: the phone's side of Wear sync (decision 0013).
plugins {
  id("com.android.library")
  id("cassette.di")
}

android {
  namespace = "com.r0adkll.cassette.surfaces.wear.phone"
  compileSdk = 37
  defaultConfig { minSdk = 31 }
}

kotlin { jvmToolchain(21) }

dependencies {
  api(project(":surfaces:wear:sync"))
  implementation(project(":features:bikes:api"))
  implementation(project(":features:capture:api"))
  implementation(libs.metro.runtime)
  implementation(libs.play.services.wearable)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.androidx.work.runtime)
}
