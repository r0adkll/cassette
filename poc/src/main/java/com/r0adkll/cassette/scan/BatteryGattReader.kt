package com.r0adkll.cassette.scan

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.r0adkll.cassette.axs.toHex
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

sealed interface GattReadResult {
  /**
   * Values for the requested characteristics the device has; absent ones are simply missing from
   * the map.
   */
  data class Values(val values: Map<UUID, ByteArray>, val services: List<UUID>) : GattReadResult

  data object Failed : GattReadResult
}

/**
 * Short read-only GATT sessions: connect, read the requested characteristics (Android handles long
 * reads), disconnect. No pairing, no writes, no subscriptions.
 */
@SuppressLint("MissingPermission") // read() checks hasConnectPermission() first.
class BatteryGattReader(private val context: Context) {
  fun hasConnectPermission(): Boolean =
    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
      PackageManager.PERMISSION_GRANTED

  suspend fun read(
    address: String,
    characteristics: List<UUID>,
    timeoutMillis: Long = 12_000,
  ): GattReadResult {
    if (!hasConnectPermission()) return GattReadResult.Failed
    val adapter =
      context.getSystemService(BluetoothManager::class.java).adapter?.takeIf { it.isEnabled }
        ?: return GattReadResult.Failed
    val device =
      runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return GattReadResult.Failed
    val result = withTimeoutOrNull(timeoutMillis) { connectAndRead(device, characteristics) }
    if (result == null) Log.d(TAG, "GATT $address timed out")
    return result ?: GattReadResult.Failed
  }

  private suspend fun connectAndRead(
    device: BluetoothDevice,
    wanted: List<UUID>,
  ): GattReadResult = suspendCancellableCoroutine { cont ->
    val address = device.address
    val finished = AtomicBoolean(false)
    var gatt: BluetoothGatt? = null
    // Android allows one outstanding GATT operation at a time, so reads are chained.
    val queue = ArrayDeque<BluetoothGattCharacteristic>()
    val values = mutableMapOf<UUID, ByteArray>()
    var services = emptyList<UUID>()

    fun finish(result: GattReadResult) {
      if (!finished.compareAndSet(false, true)) return
      gatt?.run {
        disconnect()
        close()
      }
      if (cont.isActive) cont.resume(result)
    }

    fun readNext(gatt: BluetoothGatt) {
      while (queue.isNotEmpty()) {
        if (gatt.readCharacteristic(queue.removeFirst())) return
      }
      finish(GattReadResult.Values(values, services))
    }

    val callback =
      object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
          gatt: BluetoothGatt,
          status: Int,
          newState: Int,
        ) {
          Log.d(TAG, "GATT $address state=$newState status=$status")
          when {
            status != BluetoothGatt.GATT_SUCCESS -> finish(GattReadResult.Failed)
            newState == BluetoothProfile.STATE_CONNECTED ->
              if (!gatt.discoverServices()) finish(GattReadResult.Failed)
            newState == BluetoothProfile.STATE_DISCONNECTED -> finish(GattReadResult.Failed)
          }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
          if (status != BluetoothGatt.GATT_SUCCESS) return finish(GattReadResult.Failed)
          services = gatt.services.map { it.uuid }
          val available = gatt.services.flatMap { it.characteristics }.associateBy { it.uuid }
          wanted.mapNotNullTo(queue) { available[it] }
          readNext(gatt)
        }

        override fun onCharacteristicRead(
          gatt: BluetoothGatt,
          characteristic: BluetoothGattCharacteristic,
          value: ByteArray,
          status: Int,
        ) {
          Log.d(
            TAG,
            "GATT $address read ${characteristic.uuid} status=$status value=[${value.toHex()}]",
          )
          if (status == BluetoothGatt.GATT_SUCCESS) values[characteristic.uuid] = value
          readNext(gatt)
        }

        @Deprecated("Used below API 33")
        override fun onCharacteristicRead(
          gatt: BluetoothGatt,
          characteristic: BluetoothGattCharacteristic,
          status: Int,
        ) {
          @Suppress("DEPRECATION")
          onCharacteristicRead(
            gatt,
            characteristic,
            characteristic.value ?: ByteArray(0),
            status,
          )
        }
      }

    gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    if (gatt == null) finish(GattReadResult.Failed)
    cont.invokeOnCancellation { finish(GattReadResult.Failed) }
  }

  companion object {
    private const val TAG = "CassetteScan"
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
  }
}
