package com.r0adkll.cassette.scan

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.IntentCompat
import com.r0adkll.cassette.CassetteApp
import com.r0adkll.cassette.data.CaptureSource
import com.r0adkll.cassette.data.resolveIdentity
import com.r0adkll.cassette.ridetest.RideEvent
import com.r0adkll.cassette.ridetest.Sighting
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives results from the background [android.app.PendingIntent] scan, even when the app isn't
 * running.
 */
class ScanResultReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val app = context.applicationContext as CassetteApp
    val error = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
    if (error != 0) Log.w(TAG, "Background scan reported error $error")

    val results =
      IntentCompat.getParcelableArrayListExtra(
          intent,
          BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
          ScanResult::class.java,
        )
        .orEmpty()
    val readings = results.mapNotNull { it.toReading() }
    Log.d(TAG, "Background delivery: ${results.size} results, ${readings.size} AXS readings")

    val callbackType = intent.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, 0)
    val pending = goAsync()
    app.scope.launch {
      try {
        val now = System.currentTimeMillis()
        app.store.recordBackgroundDelivery(readings, now)
        // Resolve serial-less frames so the log doesn't list the hub's address as a
        // separate component.
        val state = app.store.state.first()
        val sightings =
          readings
            .map { state.resolveIdentity(it) }
            .map { Sighting(it.key, it.frame, it.timestampMillis) }
        app.rideLog.appendIfActive(
          RideEvent.Delivery(now, results.size, sightings, callbackType, error)
        )
        // goAsync() allows ~10 s; one GATT read fits, the rest wait for the next delivery.
        val finished =
          withTimeoutOrNull(GATT_BUDGET_MILLIS) {
            app.refreshBatteries(readings, CaptureSource.BACKGROUND)
          }
        if (finished == null)
          app.rideLog.appendIfActive(RideEvent.BudgetExceeded(System.currentTimeMillis()))
      } finally {
        pending.finish()
      }
    }
  }

  companion object {
    const val ACTION_SCAN_RESULT = "com.r0adkll.cassette.SCAN_RESULT"
    private const val TAG = "CassetteScan"
    private const val GATT_BUDGET_MILLIS = 8_500L
  }
}
