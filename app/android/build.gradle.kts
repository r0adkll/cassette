import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import com.r0adkll.cassette.convention.FormFactor
import com.r0adkll.cassette.convention.configureVersions
import java.util.regex.Pattern

plugins {
  id("cassette.android.application")
  id("cassette.di")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.drop)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
  alias(libs.plugins.aboutlibraries.android)
  alias(libs.plugins.androidx.baselineprofile)
}

// Crash reports (decision 0020). The Firebase config isn't committed: CI writes it from a secret
// and scripts/firebase-wizard makes it. Without it the app builds and Crashlytics stays dormant.
val hasFirebase = file("google-services.json").exists()

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

android {
  namespace = "com.r0adkll.cassette.app"

  defaultConfig {
    applicationId = "com.r0adkll.cassette"
  }

  // The internal key (decision 0008): CI signs every Build it uploads to Drop with it, so they can
  // update each other. Without it (local builds) the debug build keeps the debug key.
  val keystore = providers.environmentVariable("CASSETTE_KEYSTORE").orNull
  // The upload key (M4): Play re-signs with the app signing key it holds, the same for the phone
  // and
  // Wear apps. scripts/release-signing-wizard makes it. Without it, release builds are unsigned.
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
    // The POC installs as com.r0adkll.cassette; the suffix lets both live on a phone.
    debug {
      applicationIdSuffix = ".debug"
      if (keystore != null) signingConfig = signingConfigs.getByName("internal")
    }

    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (uploadKeystore != null) signingConfig = signingConfigs.getByName("upload")
      // R8's mapping, so Crashlytics can deobfuscate; it needs the config's app id.
      configure<CrashlyticsExtension> { mappingFileUploadEnabled = hasFirebase }
    }

    // What CI uploads to Drop: the release build's R8 optimisation, under the debug build's id, key
    // and icon so Drop Builds keep updating the debug installs. Local builds fall back to the debug
    // key.
    create("internal") {
      initWith(getByName("release"))
      applicationIdSuffix = ".debug"
      matchingFallbacks += "release"
      isProfileable = true
      configure<CrashlyticsExtension> { mappingFileUploadEnabled = hasFirebase }
      signingConfig =
        if (keystore != null) signingConfigs.getByName("internal")
        else signingConfigs.getByName("debug")
    }
  }

  // The baseline profile plugin's minified benchmark build types (decision 0023) never ship, so
  // their mappings don't go to Crashlytics.
  buildTypes.configureEach {
    if (name.startsWith("benchmark"))
      configure<CrashlyticsExtension> { mappingFileUploadEnabled = false }
  }

  sourceSets.getByName("internal").res.directories += "src/debug/res"

  buildFeatures { compose = true }
}

// The version name is `cassette.version`; release's code derives from it, the Drop builds' from
// the commit count.
configureVersions(FormFactor.Phone)

// Baseline and startup profiles (decision 0023): :app:baselineprofile generates them from the
// release build on an emulator into src/main/generated/baselineProfiles, so release and internal
// both ship them, and R8 puts the startup classes first in the dex.
baselineProfile {
  mergeIntoMain = true
  dexLayoutOptimization = true
}

// Builds from CI go to Drop; the debug and internal builds get its SDK (in-app updates), release
// the no-op.
val dropBuildTypes = setOf("debug", "internal")

drop {
  serverUrl = "https://drop.thescavengers.software"
  enableFor { it.buildType in dropBuildTypes }
}

// And the link Open in App on a Drop Build page opens (decision 0008).
androidComponents.onVariants { variant ->
  if (variant.buildType in dropBuildTypes)
    variant.sources.manifests.addStaticManifestFile("src/drop/AndroidManifest.xml")
}

// Settings → About → Open-source licences: every dependency's licence, collected per variant into
// res/raw/aboutlibraries.json.
aboutLibraries {
  collect.fetchRemoteLicense = false
  library.duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
  library.exclusionPatterns.add(Pattern.compile(".*-bom"))
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
  // AndroidPermissionController's activity-result launcher: Play Services alone would resolve
  // fragment 1.1.0, which release lint rejects (InvalidFragmentVersionForActivityResult).
  implementation(libs.androidx.fragment)
  implementation(libs.androidx.core.ktx)
  // Installs the baseline profile where Play doesn't: Drop builds and sideloads.
  implementation(libs.androidx.profileinstaller)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.crashlytics)
  // Internal falls back to release's profile run, so one run makes both.
  baselineProfile(project(":app:baselineprofile"))
}
