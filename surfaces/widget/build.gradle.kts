// Android-only: Glance widgets are RemoteViews. A plain Android library with built-in Kotlin.
plugins {
  id("com.android.library")
  alias(libs.plugins.compose.compiler)
  id("cassette.di")
}

android {
  namespace = "com.r0adkll.cassette.surfaces.widget"
  compileSdk = 37
  defaultConfig { minSdk = 31 }
  buildFeatures { compose = true }
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(project(":features:bikes:api"))
  implementation(project(":features:bikes:ui"))
  implementation(project(":common:compose"))
  implementation(project(":common:screens"))
  implementation(libs.androidx.glance.appwidget)
  // The shared UI state types are Circuit states.
  implementation(libs.circuit.runtime)
  implementation(libs.metro.runtime)
}
