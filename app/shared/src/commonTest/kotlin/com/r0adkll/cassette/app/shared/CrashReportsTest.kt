package com.r0adkll.cassette.app.shared

import com.r0adkll.cassette.settings.test.InMemorySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CrashReportsTest {
  @Test
  fun followsTheSwitch() = runTest {
    val settings = InMemorySettings()
    val calls = mutableListOf<Boolean>()
    settings.followCrashReports(backgroundScope) { calls += it }

    runCurrent()
    settings.setCrashReports(false)
    runCurrent()
    // The same value again doesn't reach Crashlytics.
    settings.setCrashReports(false)
    runCurrent()
    settings.setCrashReports(true)
    runCurrent()

    assertEquals(listOf(true, false, true), calls)
  }
}
