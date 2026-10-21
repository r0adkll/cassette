package com.r0adkll.cassette.app.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.uiAutomator
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold starts to the sample bike's screen without the profiles and with them (decision 0023), on
 * the minified `benchmarkRelease` build: `./gradlew
 * :app:baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest`. On the emulator, compare the two
 * modes with each other, not with a phone.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {
  @get:Rule val rule = MacrobenchmarkRule()

  @Before
  fun sampleBike() = uiAutomator {
    startApp(targetPackage)
    toSampleBike()
  }

  @Test fun startupWithoutProfiles() = startup(CompilationMode.None())

  @Test fun startupWithProfiles() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

  private fun startup(compilationMode: CompilationMode) =
    rule.measureRepeated(
      packageName = targetPackage,
      metrics = listOf(StartupTimingMetric()),
      compilationMode = compilationMode,
      startupMode = StartupMode.COLD,
      iterations = 10,
      setupBlock = { pressHome() },
    ) {
      startActivityAndWait()
      awaitText(SAMPLE_BIKE)
    }
}
