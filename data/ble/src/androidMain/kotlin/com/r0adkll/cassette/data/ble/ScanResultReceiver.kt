package com.r0adkll.cassette.data.ble

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.IntentCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Receives the background scan's results, even when the app isn't running. */
class ScanResultReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val host = context.applicationContext as BleHost
    val error = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
    if (error != 0) Log.w(TAG, "Background scan reported error $error")
    val readings =
      IntentCompat.getParcelableArrayListExtra(
          intent,
          BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
          ScanResult::class.java,
        )
        .orEmpty()
        .mapNotNull { it.toReading() }
    Log.d(TAG, "Background delivery: ${readings.size} AXS readings")

    val pending = goAsync()
    host.bleScope.launch {
      try {
        // goAsync() allows about 10 s; leave a margin.
        withTimeoutOrNull(BUDGET_MILLIS) {
          host.backgroundDeliveryHandler.onDelivery(readings, error)
        }
          ?: run {
            Log.d(TAG, "Background delivery ran out of time")
            host.backgroundDeliveryHandler.onBudgetExceeded()
          }
      } finally {
        pending.finish()
      }
    }
  }

  companion object {
    const val ACTION = "com.r0adkll.cassette.SCAN_RESULT"
    /** PendingIntent request code; each PendingIntent gets its own (CLAUDE.md). */
    const val REQUEST_CODE = 100
    private const val BUDGET_MILLIS = 8_500L
  }
}

/** System-held scans don't survive a reboot or an app update, so re-arm after either. */
class RearmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (
      intent.action != Intent.ACTION_BOOT_COMPLETED &&
        intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
    )
      return
    val host = context.applicationContext as BleHost
    val pending = goAsync()
    host.bleScope.launch {
      try {
        host.rearmBackgroundCapture(reason = intent.action.orEmpty().substringAfterLast('.'))
      } finally {
        pending.finish()
      }
    }
  }
}
