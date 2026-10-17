package com.r0adkll.cassette.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.pm.PackageManager
import android.util.Log
import com.r0adkll.cassette.protocol.toHex
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Ported from the POC's BatteryGattReader. */
@SuppressLint("MissingPermission") // read() checks the connect permission first.
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AndroidGattClient(private val application: Application) : GattClient {
    override val isReady: Boolean
        get() =
            application.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                application.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled ==
                    true

    override suspend fun read(
        address: String,
        characteristics: List<String>,
        timeout: Duration,
    ): GattResult {
        if (!isReady) return GattResult.Failed
        val adapter =
            application.getSystemService(BluetoothManager::class.java)?.adapter
                ?: return GattResult.Failed
        val device =
            runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return GattResult.Failed
        val wanted = characteristics.map(UUID::fromString)
        val result = withTimeoutOrNull(timeout) { connectAndRead(device, wanted) }
        if (result == null) Log.d(TAG, "GATT $address timed out")
        return result ?: GattResult.Failed
    }

    private suspend fun connectAndRead(device: BluetoothDevice, wanted: List<UUID>): GattResult =
        suspendCancellableCoroutine { cont ->
            val address = device.address
            val finished = AtomicBoolean(false)
            var gatt: BluetoothGatt? = null
            // Android allows one outstanding GATT operation at a time, so reads are chained.
            val queue = ArrayDeque<BluetoothGattCharacteristic>()
            val values = mutableMapOf<String, ByteArray>()
            var services = emptyList<String>()

            fun finish(result: GattResult) {
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
                finish(GattResult.Values(values, services))
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
                            status != BluetoothGatt.GATT_SUCCESS -> finish(GattResult.Failed)
                            newState == BluetoothProfile.STATE_CONNECTED ->
                                if (!gatt.discoverServices()) finish(GattResult.Failed)
                            newState == BluetoothProfile.STATE_DISCONNECTED ->
                                finish(GattResult.Failed)
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) return finish(GattResult.Failed)
                        services = gatt.services.map { it.uuid.toString() }
                        val available =
                            gatt.services.flatMap { it.characteristics }.associateBy { it.uuid }
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
                        if (status == BluetoothGatt.GATT_SUCCESS)
                            values[characteristic.uuid.toString()] = value
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

            gatt = device.connectGatt(application, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) finish(GattResult.Failed)
            cont.invokeOnCancellation { finish(GattResult.Failed) }
        }
}
