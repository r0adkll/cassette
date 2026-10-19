import com.r0adkll.cassette.convention.FormFactor
import com.r0adkll.cassette.convention.configureVersions

// The Wear OS app, tile and complications (decision 0013). The watch never scans: it shows the
// phone's snapshot from the Wear Data Layer.
plugins {
  id("cassette.android.application")
  id("cassette.di")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.drop)
}

android {
  namespace = "com.r0adkll.cassette.wear"

  defaultConfig {
    // The phone app's id and key: the Data Layer only connects an app to itself.
    applicationId = "com.r0adkll.cassette"
    // Wear OS 4.
    minSdk = 33
  }

  // The same internal key as the phone app (decision 0008), so a CI-built watch app and phone app
  // can talk. Without it (local builds) both keep the debug key.
  val keystore = providers.environmentVariable("CASSETTE_KEYSTORE").orNull
  // The phone app's upload key (M4); Play signs both apps with one app signing key.
  val uploadKeystore = providers.environmentVariable("CASSETTE_UPLOAD_KEYSTORE").orNull
  signingConfigs {
    if (keystore != null)
      create("internal") {
        storeFile = file(keystore)
        storePassword = providers.environmentVariable("CASSETTE_KEYSTORE_PASSWORD").get()
        keyAlias = providers.environmentVariable("CASSETTE_KEY_ALIAS").get()
        keyPassword = providers.environmentVariable("CASSETTE_KEY_PASSWORD").get()
      }
    if (uploadKeystore != null)
      create("upload") {
        storeFile = file(uploadKeystore)
        storePassword = providers.environmentVariable("CASSETTE_UPLOAD_KEYSTORE_PASSWORD").get()
        keyAlias = providers.environmentVariable("CASSETTE_UPLOAD_KEY_ALIAS").get()
        keyPassword = providers.environmentVariable("CASSETTE_UPLOAD_KEY_PASSWORD").get()
      }
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
      if (keystore != null) signingConfig = signingConfigs.getByName("internal")
    }

    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (uploadKeystore != null) signingConfig = signingConfigs.getByName("upload")
    }

    // The watch side of the phone's internal build: R8 like release, with the debug build's id, key
    // and icon so it pairs with the phone's internal and debug builds.
    create("internal") {
      initWith(getByName("release"))
      applicationIdSuffix = ".debug"
      matchingFallbacks += "release"
      isProfileable = true
      signingConfig =
        if (keystore != null) signingConfigs.getByName("internal")
        else signingConfigs.getByName("debug")
    }
  }

  sourceSets.getByName("internal").res.directories += "src/debug/res"

  buildFeatures { compose = true }

  testOptions { unitTests.isReturnDefaultValues = true }
}

// The phone app's version, with Wear's own codes: Play needs every bundle of a package distinct,
// and each Drop Build has its own.
configureVersions(FormFactor.Wear)

// Drop (decisions 0008 and 0013): CI uploads the internal build as a Wear Build beside the phone's.
// The plugin stamps it with its run and Channel.
drop {
  serverUrl = "https://drop.thescavengers.software"
  enableFor { it.buildType == "debug" || it.buildType == "internal" }
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
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.wear.compose.material3)
  implementation(libs.androidx.wear.compose.foundation)
  implementation(libs.androidx.wear.tiles)
  implementation(libs.androidx.wear.complications.data.source)
  implementation(libs.androidx.concurrent.futures.ktx)
  implementation(libs.androidx.wear.protolayout)
  implementation(libs.androidx.wear.protolayout.material3)
  implementation(libs.play.services.wearable)
  implementation(libs.kotlinx.coroutines.play.services)

  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.junit)
  testImplementation(libs.circuit.test)
  testImplementation(libs.turbine)
  testImplementation(libs.kotlinx.coroutines.test)
}
