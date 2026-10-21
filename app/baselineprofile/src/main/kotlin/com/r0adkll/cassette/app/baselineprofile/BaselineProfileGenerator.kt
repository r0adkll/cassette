package com.r0adkll.cassette.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.uiAutomator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's baseline profile, and from [startup] its startup profile (decision 0023):
 * `./gradlew :app:android:generateBaselineProfile` runs these on the managed emulator and writes
 * both into `app/android/src/main/generated/baselineProfiles/`.
 *
 * There's no bike on an emulator, so the journeys use the sample bike. Each collection starts by
 * clearing the app's profiles, so a walk to the sample bike before it isn't part of the profile.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
  @get:Rule val rule = BaselineProfileRule()

  /** A cold start to the bike screen, the one most launches make. */
  @Test
  fun startup() {
    uiAutomator {
      startApp(targetPackage)
      toSampleBike()
    }
    rule.collect(targetPackage, includeInStartupProfile = true) {
      startActivityAndWait()
      awaitText(SAMPLE_BIKE)
    }
  }

  /**
   * A fresh install's first run: welcome, permissions and add bike, then Diagnostics to load the
   * sample bike. Only the first iteration starts fresh, but profiles add up across iterations.
   */
  @Test
  fun firstRun() {
    uiAutomator { device.executeShellCommand("pm clear $targetPackage") }
    rule.collect(targetPackage) {
      startActivityAndWait()
      toSampleBike()
    }
  }

  /** The bike screen scrolled, a component's detail, and Settings. */
  @Test
  fun bike() {
    uiAutomator {
      startApp(targetPackage)
      toSampleBike()
    }
    rule.collect(targetPackage) {
      startActivityAndWait()
      awaitText(SAMPLE_BIKE)
      val dashboard = onElement { isScrollable }
      dashboard.scroll(Direction.DOWN, 1f)
      dashboard.scroll(Direction.UP, 1f)
      tapText("Front derailleur")
      awaitText("Firmware")
      tapDescribed("Back")
      tapDescribed("Settings")
      awaitText("Settings.")
      tapDescribed("Back")
      awaitText(SAMPLE_BIKE)
    }
  }
}
