package com.r0adkll.cassette.scan

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import com.r0adkll.cassette.axs.AxsAdvertParser
import com.r0adkll.cassette.axs.AxsReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Filtered BLE scans for AXS adverts. Filters run on the Bluetooth controller where supported,
 * which is what lets the [PendingIntent] scan keep running with the screen off and the app process
 * dead.
 */
@SuppressLint("MissingPermission") // Every entry point checks hasScanPermission() first.
class AxsScanner(private val context: Context) {
  private val adapter
    get() = context.getSystemService(BluetoothManager::class.java).adapter

  val isBluetoothEnabled: Boolean
    get() = adapter?.isEnabled == true

  fun hasScanPermission(): Boolean =
    context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
      PackageManager.PERMISSION_GRANTED

  /**
   * Unfiltered low-latency scan for while the app is open, so components that don't match the AXS
   * filters (e.g. a power meter advertising as a standard sensor) still show up. Android pauses
   * unfiltered scans with the screen off, which is fine for a foreground discovery tool.
   */
  fun liveResults(): Flow<ScanResult> = callbackFlow {
    val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
    if (scanner == null || !hasScanPermission()) {
      close(IllegalStateException("Bluetooth is off or the scan permission is missing"))
      return@callbackFlow
    }
    val callback =
      object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
          trySend(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
          results.forEach { trySend(it) }
        }

        override fun onScanFailed(errorCode: Int) {
          close(IllegalStateException("Scan failed with error $errorCode"))
        }
      }
    scanner.startScan(emptyList(), liveSettings, callback)
    awaitClose { runCatching { scanner.stopScan(callback) } }
  }

  /** Arms the system-held scan. Returns 0 on success, otherwise a [ScanCallback] error code. */
  fun startBackground(profile: ScanProfile): Int {
    val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return ERROR_BLUETOOTH_OFF
    if (!hasScanPermission()) return ERROR_NO_PERMISSION
    return scanner.startScan(FILTERS, profile.settings(), backgroundIntent())
  }

  fun stopBackground() {
    if (!hasScanPermission()) return
    runCatching { adapter?.bluetoothLeScanner?.stopScan(backgroundIntent()) }
  }

  private fun backgroundIntent(): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      0,
      Intent(context, ScanResultReceiver::class.java)
        .setAction(ScanResultReceiver.ACTION_SCAN_RESULT),
      // Mutable so the Bluetooth stack can attach the results.
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

  companion object {
    const val ERROR_BLUETOOTH_OFF = -1
    const val ERROR_NO_PERMISSION = -2

    private val SERVICE_UUID = ParcelUuid.fromString("0000fe51-0000-1000-8000-00805f9b34fb")

    // Two filters are OR'd: some sessions send manufacturer data without the service data.
    private val FILTERS =
      listOf(
        ScanFilter.Builder().setManufacturerData(AxsAdvertParser.COMPANY_ID, ByteArray(0)).build(),
        ScanFilter.Builder().setServiceData(SERVICE_UUID, ByteArray(0)).build(),
      )

    private val liveSettings =
      ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
  }
}

internal fun ScanResult.toReading(): AxsReading? {
  val raw = scanRecord?.bytes ?: return null
  // timestampNanos is elapsed-realtime; background results can be delivered well after they were
  // heard.
  val ageMillis = (SystemClock.elapsedRealtimeNanos() - timestampNanos) / 1_000_000
  val reading =
    AxsAdvertParser.parse(raw, rssi, device.address, System.currentTimeMillis() - ageMillis)
      ?: return null
  Log.d(
    "CassetteScan",
    "${reading.key} ${reading.frame} ${reading.kind} ${reading.battery?.millivolts ?: "-"}mV ${reading.battery?.percent ?: "-"}% " +
      "fw=${reading.firmware} rssi=$rssi addr=${reading.address} mfg=[${reading.manufacturerHex ?: ""}] svc=[${reading.serviceDataHex ?: ""}]",
  )
  return reading
}
