plugins { `kotlin-dsl` }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

dependencies {
  compileOnly(libs.android.gradlePlugin)
  compileOnly(libs.kotlin.gradlePlugin)
  compileOnly(libs.compose.gradlePlugin)
  compileOnly(libs.composeCompiler.gradlePlugin)
  implementation(libs.metro.gradlePlugin)

  testImplementation(libs.android.gradlePlugin)
  testImplementation(kotlin("test"))
}

gradlePlugin {
  plugins {
    register("multiplatform") {
      id = "cassette.multiplatform"
      implementationClass = "com.r0adkll.cassette.convention.KotlinMultiplatformConventionPlugin"
    }
    register("androidLibrary") {
      id = "cassette.android.library"
      implementationClass = "com.r0adkll.cassette.convention.AndroidLibraryConventionPlugin"
    }
    register("androidApplication") {
      id = "cassette.android.application"
      implementationClass = "com.r0adkll.cassette.convention.AndroidApplicationConventionPlugin"
    }
    register("androidTest") {
      id = "cassette.android.test"
      implementationClass = "com.r0adkll.cassette.convention.AndroidTestConventionPlugin"
    }
    register("compose") {
      id = "cassette.compose"
      implementationClass = "com.r0adkll.cassette.convention.ComposeConventionPlugin"
    }
    register("di") {
      id = "cassette.di"
      implementationClass = "com.r0adkll.cassette.convention.DiConventionPlugin"
    }
    register("parcelize") {
      id = "cassette.parcelize"
      implementationClass = "com.r0adkll.cassette.convention.ParcelizeConventionPlugin"
    }
    register("ui") {
      id = "cassette.ui"
      implementationClass = "com.r0adkll.cassette.convention.UiConventionPlugin"
    }
  }
}
