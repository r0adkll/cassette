package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.data.ble.AdvertScanner
import com.r0adkll.cassette.data.ble.ArmResult
import com.r0adkll.cassette.data.ble.GattClient
import com.r0adkll.cassette.data.ble.GattResult
import com.r0adkll.cassette.data.ble.ScanProfile
import com.r0adkll.cassette.protocol.AxsReading
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

internal class FakeGatt : GattClient {
  var values: Map<String, ByteArray> = emptyMap()
  /** Per address, for several hubs; [values] answers any other address. */
  var byAddress: Map<String, Map<String, ByteArray>> = emptyMap()
  var fail = false
  var reads = 0
  val addresses = mutableListOf<String>()
  override val isReady = true

  override suspend fun read(
    address: String,
    characteristics: List<String>,
    timeout: Duration,
  ): GattResult {
    reads++
    addresses += address
    val answer = byAddress[address] ?: values
    return if (fail) GattResult.Failed
    else GattResult.Values(answer.filterKeys { it in characteristics }, emptyList())
  }
}

internal class FakeScanner : AdvertScanner {
  val adverts = MutableSharedFlow<AxsReading>(replay = 10)
  var armed: ScanProfile? = null
  override val isReady = true

  override fun live(): Flow<AxsReading> = adverts

  override fun startBackground(profile: ScanProfile): ArmResult {
    armed = profile
    return ArmResult.Armed
  }

  override fun stopBackground() {
    armed = null
  }
}

internal object Fixtures {
  const val HUB = 1405188718L
  const val RD_ADDRESS = "AA:BB:CC:DD:EE:01"

  /** A real read of the hub's battery table (see SystemBatteryTableTest). */
  val RED_TABLE =
    bytes(
      "01 04 6e 7a c1 53 80 ea 03 cb 66 63 4e 03 97 1f 00 00 00 00 00 00 " +
        "00 00 00 00 d0 07 00 00 01 ec 03 f2 fe 7a 43 03 78 0b 00 00 00 00 " +
        "00 00 00 00 00 00 00 00 00 00 00 ed 03 31 2e 7b 43 03 82 0b 00 00 " +
        "00 00 00 00 00 00 00 00 00 00 00 00 81 2a 04 6e 7a c1 53 03 cc 1e " +
        "00 00 00 00 00 00 d2 42 ed f3 24 30 00 00"
    )
  val RED_VERSIONS =
    bytes(
      "02 04 6e 7a c1 53 81 2a 04 6e 7a c1 53 00 08 37 02 4c 01 04 01 01 " +
        "80 ea 03 cb 66 63 4e 00 03 07 02 00 00 00 00 00 01 ec 03 f2 fe 7a " +
        "43 00 03 07 02 00 00 00 00 ff 00 ed 03 31 2e 7b 43 00 03 07 02 00 " +
        "00 00 00 ff"
    )

  fun bytes(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()

  /** The tester's MTB (decision 0028): two hubs, each with its own system. */
  const val DROPPER = 1121114092L
  const val FORK = 1151498210L
  const val POD = 1346282619L
  const val SHOCK = 1013571428L
  const val MTB_RD = 1224168767L
  const val CONTROLLER = 1224155901L
  const val DROPPER_ADDRESS = "E2:E5:15:02:7D:74"
  const val SHOCK_ADDRESS = "E9:3C:A9:99:5A:51"

  val DROPPER_TABLE =
    batteryTable(DROPPER, Row(0x84, DROPPER, 7_579), Row(0x82, FORK, 7_718), Row(115, POD, 2_701))
  val SHOCK_TABLE =
    batteryTable(
      SHOCK,
      Row(0x83, SHOCK, 8_096),
      Row(0x81, MTB_RD, 8_249),
      Row(4, CONTROLLER, 2_996),
    )

  class Row(val deviceType: Int, val serial: Long, val millivolts: Int)

  /** A battery table (`d9050003`) listing [rows], every one reporting ok. */
  fun batteryTable(hub: Long, vararg rows: Row): ByteArray {
    val bytes = ByteArray(6 + rows.size * 24)
    bytes[0] = 1
    bytes[1] = rows.size.toByte()
    bytes.putU32(2, hub)
    rows.forEachIndexed { i, row ->
      val at = 6 + i * 24
      bytes[at] = row.deviceType.toByte()
      bytes.putU32(at + 3, row.serial)
      bytes[at + 7] = 3
      bytes.putU32(at + 8, row.millivolts.toLong())
    }
    return bytes
  }

  private fun ByteArray.putU32(at: Int, value: Long) {
    for (i in 0 until 4) this[at + i] = (value shr (8 * i)).toByte()
  }
}

internal class InMemoryCaptureLog : com.r0adkll.cassette.capture.api.CaptureLog {
  val list =
    kotlinx.coroutines.flow.MutableStateFlow(
      emptyList<com.r0adkll.cassette.capture.api.CaptureEvent>()
    )
  override val events = list

  override suspend fun append(event: com.r0adkll.cassette.capture.api.CaptureEvent) {
    list.value = list.value + event
  }

  override suspend fun clear() {
    list.value = emptyList()
  }

  override suspend fun export() = list.value.joinToString("\n")
}

internal class RecordingAlerts : com.r0adkll.cassette.alerts.api.Alerts {
  val captured =
    mutableListOf<
      Pair<Set<com.r0adkll.cassette.core.BikeId>, Set<com.r0adkll.cassette.core.BikeId>>
    >()

  override suspend fun onCaptured(
    bikes: Set<com.r0adkll.cassette.core.BikeId>,
    woke: Set<com.r0adkll.cassette.core.BikeId>,
  ) {
    captured += bikes to woke
  }

  override suspend fun checkReminders() = Unit

  override suspend fun snoozePreRide(
    bike: com.r0adkll.cassette.core.BikeId,
    until: kotlin.time.Instant,
  ) = Unit
}
