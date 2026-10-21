package com.r0adkll.cassette.app.baselineprofile

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiAutomatorTestScope
import androidx.test.uiautomator.scrollToElement
import androidx.test.uiautomator.textAsString

/** The app under test: the baseline profile plugin passes the target build's id. */
val targetPackage: String
  get() =
    InstrumentationRegistry.getArguments().getString("androidx.benchmark.targetPackageName")
      ?: error("No androidx.benchmark.targetPackageName: run through the baseline profile plugin")

/** The sample bike's name, which only its bike screen shows. */
const val SAMPLE_BIKE = "Sample bike"

/**
 * From wherever the app opens to the sample bike's screen: the first run on a fresh install, then
 * Diagnostics → Load sample bike, which needs no Bluetooth.
 */
fun UiAutomatorTestScope.toSampleBike() {
  val opened =
    onElement(timeoutMs = 20_000) {
      shows("Get started") || shows("No bike yet.") || shows(SAMPLE_BIKE)
    }
  when {
    opened.text.equals("Get started", ignoreCase = true) -> {
      firstRun()
      loadSampleBike()
    }
    opened.text.equals("No bike yet.", ignoreCase = true) -> loadSampleBike()
  }
  awaitText(SAMPLE_BIKE)
}

/** Welcome, permissions (left unasked) and add bike (closed: there's no bike to wake). */
fun UiAutomatorTestScope.firstRun() {
  tapText("Get started")
  tapText("Continue")
  awaitText("Wake your bike.")
  tapDescribed("Close")
  awaitText("No bike yet.")
}

/** Settings → Diagnostics → Load sample bike, then back to the bike screen. */
fun UiAutomatorTestScope.loadSampleBike() {
  tapDescribed("Settings")
  tapText("Diagnostics")
  tapText("Load sample bike")
  tapDescribed("Back")
  awaitText("Settings.")
  tapDescribed("Back")
}

fun UiAutomatorTestScope.awaitText(text: String) {
  onElement(timeoutMs = 15_000) { shows(text) }
}

/** Taps a text, scrolling the screen down to it if it's below the fold. */
fun UiAutomatorTestScope.tapText(text: String) {
  val target =
    onElementOrNull(timeoutMs = 5_000) { shows(text) }
      ?: onElement { isScrollable }.scrollToElement(Direction.DOWN) { shows(text) }
  target.click()
  waitForStableInActiveWindow()
}

/** Taps an icon button by its content description, matched exactly ("Back" isn't "background"). */
fun UiAutomatorTestScope.tapDescribed(description: String) {
  onElement(timeoutMs = 15_000) { isVisibleToUser && contentDescription?.toString() == description }
    .click()
  waitForStableInActiveWindow()
}

/**
 * Compose keeps a scrolling column's off-screen rows in the tree, so match only what shows; buttons
 * and kickers show their text upper-cased.
 */
private fun AccessibilityNodeInfo.shows(text: String) =
  isVisibleToUser && textAsString().equals(text, ignoreCase = true)
