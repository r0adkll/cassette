// Android-only: the Quick Settings tile is a TileService.
plugins {
  id("com.android.library")
  alias(libs.plugins.compose.compiler)
  id("cassette.di")
}

android {
  namespace = "com.r0adkll.cassette.surfaces.tile"
  compileSdk = 37
  defaultConfig { minSdk = 31 }
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(project(":features:bikes:api"))
  // Shares the bike screen's wording (toLoaded); its state types are Circuit states.
  implementation(project(":features:bikes:ui"))
  implementation(project(":features:capture:api"))
  implementation(project(":features:settings:api"))
  implementation(project(":common:compose"))
  implementation(libs.circuit.runtime)
  implementation(libs.metro.runtime)

  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.junit)
}
