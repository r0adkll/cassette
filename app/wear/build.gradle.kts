// The Wear OS app, tile and complications (decision 0013). The watch never scans: it shows the
// phone's snapshot from the Wear Data Layer.
plugins {
  id("cassette.android.application")
  id("cassette.di")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.parcelize)
}

android {
  namespace = "com.r0adkll.cassette.wear"

  defaultConfig {
    // The phone app's id and key: the Data Layer only connects an app to itself.
    applicationId = "com.r0adkll.cassette"
    // Wear OS 4.
    minSdk = 33
    versionCode = 1
    versionName = "0.1.0"
  }

  // The same internal key as the phone app (decision 0008), so a CI-built watch app and phone app
  // can talk. Without it (local builds) both keep the debug key.
  val keystore = providers.environmentVariable("CASSETTE_KEYSTORE").orNull
  signingConfigs {
    if (keystore != null)
      create("internal") {
        storeFile = file(keystore)
        storePassword = providers.environmentVariable("CASSETTE_KEYSTORE_PASSWORD").get()
        keyAlias = providers.environmentVariable("CASSETTE_KEY_ALIAS").get()
        keyPassword = providers.environmentVariable("CASSETTE_KEY_PASSWORD").get()
      }
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
      if (keystore != null) signingConfig = signingConfigs.getByName("internal")
    }
  }

  buildFeatures { compose = true }

  testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
  implementation(project(":common:compose"))
  implementation(project(":features:bikes:summary"))
  implementation(project(":surfaces:wear:sync"))
  implementation(libs.circuit.foundation)
  implementation(libs.circuit.retained)
  implementation(libs.circuit.codegen.annotations)
  implementation(libs.metro.runtime)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.wear.compose.material3)
  implementation(libs.androidx.wear.compose.foundation)
  implementation(libs.play.services.wearable)
  implementation(libs.kotlinx.coroutines.play.services)

  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.junit)
  testImplementation(libs.circuit.test)
  testImplementation(libs.turbine)
  testImplementation(libs.kotlinx.coroutines.test)
}
