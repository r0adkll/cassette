package com.r0adkll.cassette.protocol

/**
 * A hub table: the hub's serial (the rear derailleur on road), which identifies its system, and one
 * row per component.
 */
data class SystemTable<T>(val masterSerial: Long, val rows: List<T>)

/** One component's row in the hub's battery table (`sram_dynamic_status` in SRAM's app). */
data class SystemComponent(
  val deviceType: Int,
  val modelId: Int,
  val serial: Long,
  /** The component's own battery state; null when it reports "unknown". */
  val reportedStatus: BatteryStatus?,
  val millivolts: Int,
  val updateTime: Long,
  /**
   * Labelled runtime in SRAM's code, but decodes to implausible values on a Red RD; treat as
   * unverified.
   */
  val runtime: Long,
  val actuations: Long,
) {
  val kind: ComponentKind
    get() =
      ComponentKind.fromDeviceType(deviceType).takeIf { it != ComponentKind.UNKNOWN }
        ?: ComponentKind.fromModelId(modelId)
}

/** One component's row in the hub's firmware table (`sram_static_versions_v2`). */
data class ComponentVersions(
  val deviceType: Int,
  val modelId: Int,
  val serial: Long,
  val firmware: String,
  val softDevice: Int,
  val bootloader: String,
)

/**
 * A system's hub (the rear derailleur on road; a dropper or rear shock can head one on a mountain
 * bike, decision 0028). Its `d9050003` (battery) and `d9050004` (firmware) characteristics cover
 * every component paired with it and are readable without pairing or SRAM's encrypted session.
 *
 * Both start `[type][count][hub serial u32]`. Battery rows (24 bytes): device type, model u16,
 * serial u32, soc s8, mV u32, update time u32, runtime u32, actuation count u32. Firmware rows (16
 * bytes): device type, model u16, serial u32, app version u32 (bytes 3.2.1), softdevice u16,
 * bootloader u16 (hi.lo), bridge protocol u8.
 */
object SystemBatteryTable {
  const val CHARACTERISTIC = "d9050003-90aa-4c7c-b036-1e01fb8eb7ee"
  const val VERSIONS_CHARACTERISTIC = "d9050004-90aa-4c7c-b036-1e01fb8eb7ee"

  private const val BATTERY_TABLE = 0x01
  private const val VERSION_TABLE = 0x02
  private const val HEADER_SIZE = 6
  private const val BATTERY_ROW = 24
  private const val VERSION_ROW = 16

  fun parse(value: ByteArray): SystemTable<SystemComponent>? =
    rows(value, BATTERY_TABLE, BATTERY_ROW) { offset ->
      SystemComponent(
        deviceType = value.u8(offset),
        modelId = value.u16le(offset + 1),
        serial = value.u32le(offset + 3),
        reportedStatus = BatteryStatus.fromSoc(value.u8(offset + 7)),
        millivolts = value.u32le(offset + 8).toInt(),
        updateTime = value.u32le(offset + 12),
        runtime = value.u32le(offset + 16),
        actuations = value.u32le(offset + 20),
      )
    }

  fun parseVersions(value: ByteArray): SystemTable<ComponentVersions>? =
    rows(value, VERSION_TABLE, VERSION_ROW) { offset ->
      ComponentVersions(
        deviceType = value.u8(offset),
        modelId = value.u16le(offset + 1),
        serial = value.u32le(offset + 3),
        firmware = "${value.u8(offset + 10)}.${value.u8(offset + 9)}.${value.u8(offset + 8)}",
        softDevice = value.u16le(offset + 11),
        bootloader = "${value.u8(offset + 14)}.${value.u8(offset + 13)}",
      )
    }

  private fun <T> rows(
    value: ByteArray,
    type: Int,
    rowSize: Int,
    row: (offset: Int) -> T,
  ): SystemTable<T>? {
    if (value.size < HEADER_SIZE || value.u8(0) != type) return null
    val count = value.u8(1)
    if (value.size < HEADER_SIZE + count * rowSize) return null
    return SystemTable(value.u32le(2), (0 until count).map { row(HEADER_SIZE + it * rowSize) })
  }
}

/**
 * Rough state of charge for an AXS battery pack, interpolated from (mV, %) pairs reported in
 * component adverts: 7881/81 from a Red AXS bike, 7479/19, 7523/23, 8118/100 from esphome-sram-axs
 * captures, and 7370/10, 7360/0 from a tester's fork running flat, where SRAM's percent drops from
 * 10 straight to 0 (the pack works on to about 6.3 V). SRAM's app has no such curve; it only shows
 * the component's reported status.
 */
fun estimatePackPercent(millivolts: Int): Int {
  val points = listOf(7_360 to 0, 7_370 to 10, 7_479 to 19, 7_523 to 23, 7_881 to 81, 8_118 to 100)
  if (millivolts <= points.first().first) return 0
  val upper = points.indexOfFirst { it.first >= millivolts }
  if (upper == -1) return 100
  val (aMv, aPct) = points[upper - 1]
  val (bMv, bPct) = points[upper]
  return (aPct + (millivolts - aMv).toDouble() / (bMv - aMv) * (bPct - aPct)).toInt()
}
