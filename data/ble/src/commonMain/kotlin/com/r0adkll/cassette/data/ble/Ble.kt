package com.r0adkll.cassette.data.ble

import com.r0adkll.cassette.protocol.AxsReading
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow

/**
 * AXS advert scanning. Android implements it today; Core Bluetooth comes with iOS (milestone 3).
 */
interface AdvertScanner {
    /** Bluetooth is on and the scan permission is granted. */
    val isReady: Boolean

    /**
     * An unfiltered, low-latency scan while the app is open. Completes with an error if not
     * [isReady].
     */
    fun live(): Flow<AxsReading>

    /**
     * Arms the system-held background scan, which delivers to [BackgroundDeliveryHandler] even when
     * the app isn't running. Re-arming replaces the previous scan.
     */
    fun startBackground(profile: ScanProfile): ArmResult

    fun stopBackground()
}

sealed interface ArmResult {
    data object Armed : ArmResult

    data object BluetoothOff : ArmResult

    data object NoPermission : ArmResult

    /** The platform rejected the scan; [code] is its error code, for diagnostics. */
    data class Failed(val code: Int) : ArmResult
}

/** Background scan configurations; ride tests compare them (decision 0009). */
enum class ScanProfile {
    /** Every advert heard in the low-power window wakes the app. */
    AllLowPower,
    /** Scans more often: better coverage, more phone battery. */
    AllBalanced,
    /** The controller holds results and wakes the app at most every 30 s. */
    Batched30s,
    /** One wake when a component appears and one when it goes quiet. */
    FirstAndLost,
}

/** Short, read-only GATT sessions: connect, read, disconnect. No pairing, no writes. */
interface GattClient {
    val isReady: Boolean

    suspend fun read(
        address: String,
        characteristics: List<String>,
        timeout: Duration = 12.seconds,
    ): GattResult
}

sealed interface GattResult {
    /**
     * Values for the requested characteristics the device has; absent ones are missing from the
     * map.
     */
    class Values(val values: Map<String, ByteArray>, val services: List<String>) : GattResult

    data object Failed : GattResult
}

/** Standard Battery Level (Quarq power meters answer it directly). */
const val BATTERY_LEVEL_CHARACTERISTIC = "00002a19-0000-1000-8000-00805f9b34fb"

/** Receives what the background scan heard. The app's capture feature implements it. */
fun interface BackgroundDeliveryHandler {
    /** Called on a background thread with a time budget of about 8 seconds. */
    suspend fun onDelivery(readings: List<AxsReading>, errorCode: Int)
}
