plugins {
  id("cassette.android.application")
  id("cassette.di")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "com.r0adkll.cassette.app"

  defaultConfig {
    applicationId = "com.r0adkll.cassette"
    versionCode = 1
    versionName = "0.1.0"
  }

  buildTypes {
    // The POC installs as com.r0adkll.cassette; the suffix lets both live on a phone.
    debug { applicationIdSuffix = ".debug" }
  }

  buildFeatures { compose = true }
}

dependencies {
  implementation(project(":common:compose"))
  implementation(project(":common:screens"))
  implementation(project(":data:ble"))
  implementation(project(":data:db"))
  implementation(project(":features:alerts:impl"))
  implementation(project(":features:capture:api"))
  implementation(project(":features:capture:impl"))
  implementation(project(":features:settings:api"))
  implementation(project(":features:diagnostics:ui"))
  implementation(project(":features:onboarding:ui"))
  implementation(project(":features:settings:impl"))
  implementation(project(":features:settings:ui"))
  implementation(project(":surfaces:tile"))
  implementation(project(":surfaces:widget"))
  implementation(project(":features:bikes:api"))
  implementation(project(":features:bikes:impl"))
  implementation(project(":features:bikes:ui"))
  implementation(libs.circuit.foundation)
  implementation(libs.circuitx.gesture.navigation)
  implementation(libs.circuit.codegen.annotations)
  implementation(libs.metro.runtime)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
}
