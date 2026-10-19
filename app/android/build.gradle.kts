import com.r0adkll.cassette.convention.FormFactor
import com.r0adkll.cassette.convention.configureVersions

plugins {
  id("cassette.android.application")
  id("cassette.di")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.drop)
}

android {
  namespace = "com.r0adkll.cassette.app"

  defaultConfig {
    applicationId = "com.r0adkll.cassette"
  }

  // The internal key (decision 0008): CI signs every Build it uploads to Drop with it, so they can
  // update each other. Without it (local builds) the debug build keeps the debug key.
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
    // The POC installs as com.r0adkll.cassette; the suffix lets both live on a phone.
    debug {
      applicationIdSuffix = ".debug"
      if (keystore != null) signingConfig = signingConfigs.getByName("internal")
    }

    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }

    // What CI uploads to Drop: the release build's R8 optimisation, under the debug build's id, key
    // and icon so Drop Builds keep updating the debug installs. Local builds fall back to the debug
    // key.
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
}

// The version name is `cassette.version`; each build type's code is derived from it.
configureVersions(FormFactor.Phone)

// Builds from CI go to Drop; the debug and internal builds get its SDK (in-app updates), release
// the no-op.
drop {
  serverUrl = "https://drop.thescavengers.software"
  enableFor { it.buildType == "debug" || it.buildType == "internal" }
}

dependencies {
  implementation(project(":app:shared"))
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
  implementation(project(":surfaces:wear:phone"))
  implementation(project(":surfaces:widget"))
  implementation(project(":features:bikes:api"))
  implementation(project(":features:bikes:impl"))
  implementation(project(":features:bikes:ui"))
  implementation(libs.circuit.foundation)
  implementation(libs.circuitx.gesture.navigation)
  implementation(libs.drop.ui)
  implementation(libs.circuit.codegen.annotations)
  implementation(libs.metro.runtime)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
}
