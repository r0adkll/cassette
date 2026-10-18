package com.r0adkll.cassette.data.ble

import com.r0adkll.cassette.protocol.AxsReading
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.time.Duration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Core Bluetooth scans (decision 0016). iOS sets the background scan's cadence, so every
 * [ScanProfile] arms the same `FE51` scan.
 */
@ContributesBinding(AppScope::class)
@Inject
class IosAdvertScanner(private val central: IosCentral) : AdvertScanner {
  override val isReady: Boolean
    get() = central.state.value.let { it == BluetoothState.On || it == BluetoothState.Starting }

  override fun live(): Flow<AxsReading> = callbackFlow {
    if (!central.addLiveListener(channel)) {
      close(IllegalStateException("Bluetooth is off or not allowed"))
      return@callbackFlow
    }
    awaitClose { central.removeLiveListener(channel) }
  }

  override fun startBackground(profile: ScanProfile): ArmResult = central.arm()

  override fun stopBackground() = central.disarm()
}

/** Short, read-only GATT sessions by peripheral identifier. */
@ContributesBinding(AppScope::class)
@Inject
class IosGattClient(private val central: IosCentral) : GattClient {
  override val isReady: Boolean
    get() = central.state.value.let { it == BluetoothState.On || it == BluetoothState.Starting }

  override suspend fun read(
    address: String,
    characteristics: List<String>,
    timeout: Duration,
  ): GattResult = central.read(address, characteristics, timeout)
}
