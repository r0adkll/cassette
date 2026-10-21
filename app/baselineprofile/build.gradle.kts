import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
  id("cassette.android.test")
  alias(libs.plugins.androidx.baselineprofile)
}

// Generates :app:android's baseline and startup profiles (decision 0023) on a Gradle managed
// emulator, so a run never touches a shared emulator or a phone with real bike data:
// `./gradlew :app:android:generateBaselineProfile`. The same device runs StartupBenchmark.
android {
  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // Emulator timings are noisy, but comparing two compilation modes on one is still useful.
    testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
  }

  targetProjectPath = ":app:android"

  testOptions.managedDevices {
    allDevices {
      create<ManagedVirtualDevice>("pixel6Api34") {
        device = "Pixel 6"
        apiLevel = 34
        systemImageSource = "google"
      }
    }
  }
}

baselineProfile {
  managedDevices += "pixel6Api34"
  useConnectedDevices = false
  skipBenchmarksOnEmulator = false
}

dependencies {
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.test.runner)
  implementation(libs.androidx.uiautomator)
  implementation(libs.androidx.benchmark.macro.junit4)
}
