package com.r0adkll.cassette.scan

import android.bluetooth.le.ScanSettings

/** Background scan configurations to compare on rides: coverage vs. how often the app is woken. */
enum class ScanProfile(val label: String, val description: String) {
  ALL_LOW_POWER(
    "All matches · low power",
    "Every advert heard in the low-power scan window wakes the app.",
  ),
  ALL_BALANCED(
    "All matches · balanced",
    "Scans more often: better coverage, more phone battery.",
  ),
  BATCHED_30S(
    "Batched · 30 s",
    "The controller holds results and wakes the app at most every 30 s.",
  ),
  FIRST_AND_LOST(
    "First seen + lost",
    "One wake when a component appears and one when it goes quiet. Needs offloaded filtering.",
  );

  fun settings(): ScanSettings =
    ScanSettings.Builder()
      .apply {
        when (this@ScanProfile) {
          ALL_LOW_POWER ->
            setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
              .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
          ALL_BALANCED ->
            setScanMode(ScanSettings.SCAN_MODE_BALANCED)
              .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
          BATCHED_30S ->
            setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
              .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
              .setReportDelay(30_000L)
          FIRST_AND_LOST ->
            setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
              .setCallbackType(
                ScanSettings.CALLBACK_TYPE_FIRST_MATCH or ScanSettings.CALLBACK_TYPE_MATCH_LOST
              )
              .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
              .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
        }
      }
      .build()
}
