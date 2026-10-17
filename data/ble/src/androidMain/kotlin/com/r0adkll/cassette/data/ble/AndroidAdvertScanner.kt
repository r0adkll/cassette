package com.r0adkll.cassette.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import com.r0adkll.cassette.protocol.AxsAdvertParser
import com.r0adkll.cassette.protocol.AxsReading
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Filtered BLE scans for AXS adverts. Filters run on the Bluetooth controller where supported,
 * which is what lets the [PendingIntent] scan keep running with the screen off and the app process
 * dead.
 */
@SuppressLint("MissingPermission") // Every entry point checks hasScanPermission() first.
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AndroidAdvertScanner(private val application: Application) : AdvertScanner {
  private val adapter
    get() = application.getSystemService(BluetoothManager::class.java)?.adapter

  override val isReady: Boolean
    get() = adapter?.isEnabled == true && hasScanPermission()

  private fun hasScanPermission(): Boolean =
    application.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
      PackageManager.PERMISSION_GRANTED

  /**
   * Unfiltered so components that don't match the AXS filters (a power meter advertising as a
   * standard sensor) still show up. Android pauses unfiltered scans with the screen off, which is
   * fine while the app is open.
   */
  override fun live(): Flow<AxsReading> = callbackFlow {
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
    scanner.startScan(emptyList(), LIVE_SETTINGS, callback)
    awaitClose { runCatching { scanner.stopScan(callback) } }
  }
    .mapNotNull { it.toReading() }

  override fun startBackground(profile: ScanProfile): ArmResult {
    val scanner =
      adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return ArmResult.BluetoothOff
    if (!hasScanPermission()) return ArmResult.NoPermission
    // Re-registering the same PendingIntent with new settings isn't reliable; stop first.
    stopBackground()
    val code = scanner.startScan(FILTERS, profile.settings(), backgroundIntent())
    return if (code == 0) ArmResult.Armed else ArmResult.Failed(code)
  }

  override fun stopBackground() {
    if (!hasScanPermission()) return
    runCatching { adapter?.bluetoothLeScanner?.stopScan(backgroundIntent()) }
  }

  private fun backgroundIntent(): PendingIntent =
    PendingIntent.getBroadcast(
      application,
      ScanResultReceiver.REQUEST_CODE,
      Intent(application, ScanResultReceiver::class.java).setAction(ScanResultReceiver.ACTION),
      // Mutable so the Bluetooth stack can attach the results.
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

  private companion object {
    val SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("0000fe51-0000-1000-8000-00805f9b34fb")

    // OR'd: some sessions send manufacturer data without the service data.
    val FILTERS =
      listOf(
        ScanFilter.Builder().setManufacturerData(AxsAdvertParser.COMPANY_ID, ByteArray(0)).build(),
        ScanFilter.Builder().setServiceData(SERVICE_UUID, ByteArray(0)).build(),
      )

    val LIVE_SETTINGS: ScanSettings =
      ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
  }
}

private fun ScanProfile.settings(): ScanSettings =
  ScanSettings.Builder()
    .apply {
      when (this@settings) {
        ScanProfile.AllLowPower ->
          setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        ScanProfile.AllBalanced ->
          setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        ScanProfile.Batched30s ->
          setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(30_000L)
        ScanProfile.FirstAndLost ->
          setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(
              ScanSettings.CALLBACK_TYPE_FIRST_MATCH or ScanSettings.CALLBACK_TYPE_MATCH_LOST
            )
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
      }
    }
    .build()

/** Raw bytes stay in logcat (tag CassetteScan) for diagnostics (decision 0005). */
internal fun ScanResult.toReading(): AxsReading? {
  val raw = scanRecord?.bytes ?: return null
  // timestampNanos is elapsed-realtime; background results can arrive well after they were heard.
  val ageMillis = (SystemClock.elapsedRealtimeNanos() - timestampNanos) / 1_000_000
  val reading =
    AxsAdvertParser.parse(raw, rssi, device.address, System.currentTimeMillis() - ageMillis)
      ?: return null
  Log.d(
    TAG,
    "${reading.key} ${reading.frame} ${reading.kind} ${reading.battery?.millivolts ?: "-"}mV " +
      "${reading.battery?.percent ?: "-"}% fw=${reading.firmware} rssi=$rssi addr=${reading.address} " +
      "mfg=[${reading.manufacturerHex.orEmpty()}] svc=[${reading.serviceDataHex.orEmpty()}]",
  )
  return reading
}

internal const val TAG = "CassetteScan"
