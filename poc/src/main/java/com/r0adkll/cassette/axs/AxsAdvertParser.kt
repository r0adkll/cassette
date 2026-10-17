package com.r0adkll.cassette.axs

/**
 * Decodes the BLE advertisements AXS components broadcast while awake. Layouts come from captures
 * of a Red AXS bike, esphome-sram-axs (docs/protocol.md) and SRAM's app (`SramServiceCoder`, the
 * 0x0933 record parser):
 *
 * - **Manufacturer data**, company `0x0933`: a list of `[type u16][len u8][payload]` records.
 *   `0x0400` is battery (mV u16, soc status, unknown, percent or `0xFF`), `0x0101` wraps an SRAM
 *   service frame, `0x0402` is the firmware version plus an ASCII build tag. Android's
 *   [android.bluetooth.le.ScanRecord] keeps only the last AD structure per company ID, so we walk
 *   the raw bytes and concatenate them.
 * - **Service data**, UUID `0xFE51`: an SRAM service frame (see [SramService]). Derailleurs include
 *   their device type; shifters and front derailleurs only send serial, model id and firmware.
 * - **Quarq power meters** add the cycling power service and are named `Quarq <id>`.
 */
object AxsAdvertParser {
  const val COMPANY_ID = 0x0933
  const val SERVICE_UUID_16 = 0xFE51

  private const val AD_SHORT_LOCAL_NAME = 0x08
  private const val AD_COMPLETE_LOCAL_NAME = 0x09
  private const val AD_SERVICE_DATA_16 = 0x16
  private const val AD_MANUFACTURER_DATA = 0xFF

  private const val RECORD_SERVICE = 0x0101
  private const val RECORD_BATTERY = 0x0400
  private const val RECORD_VERSION = 0x0402
  private const val PERCENT_UNSUPPORTED = 0xFF
  private val NAME_SERIAL = Regex("""^SRAM (\d+)$""")
  private val QUARQ_NAME = Regex("""^Quarq (\S+)$""", RegexOption.IGNORE_CASE)

  fun parse(raw: ByteArray, rssi: Int, address: String, timestampMillis: Long): AxsReading? {
    var name: String? = null
    var manufacturer = ByteArray(0)
    var serviceData: ByteArray? = null

    for ((type, data) in adStructures(raw)) {
      when (type) {
        AD_SHORT_LOCAL_NAME,
        AD_COMPLETE_LOCAL_NAME -> name = data.decodeToString()
        AD_MANUFACTURER_DATA ->
          if (data.size >= 2 && data.u16le(0) == COMPANY_ID)
            manufacturer += data.copyOfRange(2, data.size)
        AD_SERVICE_DATA_16 ->
          if (data.size >= 2 && data.u16le(0) == SERVICE_UUID_16)
            serviceData = data.copyOfRange(2, data.size)
      }
    }

    val records = manufacturerRecords(manufacturer)
    val battery =
      records[RECORD_BATTERY]
        ?.takeIf { it.size >= 5 }
        ?.let { payload ->
          AdvertBattery(
            millivolts = payload.u16le(0),
            percent = payload.u8(4).takeIf { it != PERCENT_UNSUPPORTED },
            reportedStatus = BatteryStatus.fromSoc(payload.u8(2)),
          )
        }
    val service =
      serviceData?.let(SramService::decode) ?: records[RECORD_SERVICE]?.let(SramService::decode)
    val versionRecord =
      records[RECORD_VERSION]
        ?.takeIf { it.size >= 3 }
        ?.let { "${it.u8(0)}.${it.u8(1)}.${it.u8(2)}" }
    val quarq = name?.let { QUARQ_NAME.matches(it) } == true || service?.quarqModelCode != null

    val frame =
      when {
        battery != null -> FrameType.STATUS
        quarq -> FrameType.POWER_METER
        service != null -> FrameType.BEACON
        else -> return null
      }
    val serial =
      service?.serial ?: name?.let { NAME_SERIAL.find(it) }?.groupValues?.get(1)?.toLongOrNull()
    val kind =
      ComponentKind.fromDeviceType(service?.deviceType).takeIf { it != ComponentKind.UNKNOWN }
        ?: ComponentKind.fromModelId(service?.modelId).takeIf {
          it != ComponentKind.UNKNOWN
        }
        ?: if (quarq) ComponentKind.POWER_METER else ComponentKind.UNKNOWN

    return AxsReading(
      // Power meters keep their name as identity: Quarq serials aren't the decimal SRAM ones.
      key =
        if (frame == FrameType.POWER_METER && name != null) name else serial?.toString() ?: address,
      serial = serial,
      address = address,
      name = name,
      rssi = rssi,
      timestampMillis = timestampMillis,
      frame = frame,
      kind = kind,
      battery = battery,
      firmware = service?.firmware ?: versionRecord,
      manufacturerHex = manufacturer.takeIf { it.isNotEmpty() }?.toHex(),
      serviceDataHex = serviceData?.toHex(),
      classByte = service?.deviceType,
      modelId = service?.modelId,
      bridge = service?.bridge == true,
    )
  }

  /** Splits raw advertising bytes into (AD type, payload) pairs, stopping at the zero padding. */
  internal fun adStructures(raw: ByteArray): List<Pair<Int, ByteArray>> = buildList {
    var i = 0
    while (i < raw.size) {
      val length = raw.u8(i)
      if (length == 0 || i + 1 + length > raw.size) break
      add(raw.u8(i + 1) to raw.copyOfRange(i + 2, i + 1 + length))
      i += 1 + length
    }
  }

  /**
   * `[type u16][len u8][payload]` records; a trailing partial record is ignored. The first record
   * of a type wins.
   */
  internal fun manufacturerRecords(data: ByteArray): Map<Int, ByteArray> = buildMap {
    var i = 0
    while (i + 3 <= data.size) {
      val type = data.u16le(i)
      val length = data.u8(i + 2)
      if (i + 3 + length > data.size) break
      if (type !in this) put(type, data.copyOfRange(i + 3, i + 3 + length))
      i += 3 + length
    }
  }
}

/**
 * SRAM's flags-driven service frame (`sram_service`). Fields appear in order, each only if its flag
 * is set: `flags`, `flags2` (bit0), 7 skipped bytes (bit1, Quarq), serial u32 (bit2, required),
 * model u16 (bit3), Quarq model code u16 (bit5), firmware 3 bytes (bit6), model flags u32 (bit7),
 * then device type u8 (flags2 bit1). flags2 bit0 marks the system's bridge component.
 */
internal data class SramService(
  val serial: Long,
  val modelId: Int?,
  val deviceType: Int?,
  val firmware: String?,
  val bridge: Boolean,
  val quarqModelCode: Int?,
) {
  companion object {
    fun decode(data: ByteArray): SramService? {
      if (data.isEmpty()) return null
      var i = 0
      val flags = data.u8(i++)
      val flags2 =
        if (flags and 0x01 != 0) data.getOrNull(i++)?.toInt()?.and(0xFF) ?: return null else 0
      if (flags and 0x02 != 0) i += 7
      if (flags and 0x04 == 0 || i + 4 > data.size) return null
      val serial = data.u32le(i).also { i += 4 }

      fun u16(): Int? =
        if (i + 2 <= data.size) data.u16le(i).also { i += 2 } else null.also { i = data.size }
      val model = if (flags and 0x08 != 0) u16() else null
      val quarqCode = if (flags and 0x20 != 0) u16() else null
      val firmware =
        if (flags and 0x40 != 0 && i + 3 <= data.size) {
          (if (quarqCode != null) "${data.u8(i + 2)}"
            else "${data.u8(i)}.${data.u8(i + 1)}.${data.u8(i + 2)}")
            .also { i += 3 }
        } else {
          null
        }
      if (flags and 0x80 != 0) i += 4
      val deviceType = if (flags2 and 0x02 != 0 && i < data.size) data.u8(i) else null

      return SramService(
        serial = serial,
        modelId = model ?: quarqCode?.and(0x3f),
        deviceType = deviceType,
        firmware = firmware,
        bridge = flags2 and 0x01 != 0,
        quarqModelCode = quarqCode,
      )
    }
  }
}

internal fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

internal fun ByteArray.u16le(index: Int): Int = u8(index) or (u8(index + 1) shl 8)

internal fun ByteArray.u32le(index: Int): Long =
  u16le(index).toLong() or (u16le(index + 2).toLong() shl 16)

internal fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
