package com.r0adkll.cassette.protocol

/**
 * Something a live scan heard: an AXS reading, or a Shimano Di2 advert kept raw (decision 0029).
 */
sealed interface Advert {
  val address: String
  val name: String?
  val rssi: Int
  val timestampMillis: Long
}

/**
 * A Shimano Di2 advert (decision 0029). Nobody has published its layout, so it stays raw: what made
 * it look like Shimano and what it carried, for Diagnostics and the capture log.
 */
data class ShimanoAdvert(
  override val address: String,
  override val name: String?,
  override val rssi: Int,
  override val timestampMillis: Long,
  /** Every company `0x044A` payload joined, without the company id. */
  val manufacturerHex: String?,
  /** Advertised service UUIDs, lower-case and 128-bit. */
  val services: List<String>,
  /** Service data by its UUID (lower-case, 128-bit). */
  val serviceData: Map<String, String>,
  /** The whole advert where the platform hands it over (Android); Core Bluetooth doesn't. */
  val rawHex: String?,
) : Advert {
  /** Short forms of [services]: `18ef` for Shimano's and the standard ones. */
  val shortServices: List<String>
    get() = services.map(::shortUuid)
}

/**
 * Picks Shimano Di2 adverts out of a scan (decision 0029, docs/protocol.md §9). The markers come
 * from public projects, not captures of ours: company id `0x044A`, Shimano's `SHIMANO_BLE` service
 * UUIDs (`000018ef-5348-494d-414e-4f5f424c4500` and its siblings), and rear derailleur names
 * starting `RDR` (`RDR9250 …`).
 */
object ShimanoAdvertParser {
  const val COMPANY_ID = 0x044A

  /** The last 12 bytes of every Shimano UUID: ASCII `SHIMANO_BLE\0`. */
  const val UUID_SUFFIX = "-5348-494d-414e-4f5f424c4500"

  /** Shimano Bicycle Information: gears, D-Fly buttons. Reads need bonding (prior art). */
  const val SBI_SERVICE = "000018ef$UUID_SUFFIX"

  /** E-TUBE's settings service, behind Shimano's authentication. Cassette never opens it. */
  const val ETUBE_SERVICE = "000018ff$UUID_SUFFIX"

  private const val NAME_PREFIX = "RDR"

  private const val AD_INCOMPLETE_16 = 0x02
  private const val AD_COMPLETE_16 = 0x03
  private const val AD_INCOMPLETE_32 = 0x04
  private const val AD_COMPLETE_32 = 0x05
  private const val AD_INCOMPLETE_128 = 0x06
  private const val AD_COMPLETE_128 = 0x07
  private const val AD_SHORT_LOCAL_NAME = 0x08
  private const val AD_COMPLETE_LOCAL_NAME = 0x09
  private const val AD_SERVICE_DATA_16 = 0x16
  private const val AD_SERVICE_DATA_32 = 0x20
  private const val AD_SERVICE_DATA_128 = 0x21
  private const val AD_MANUFACTURER_DATA = 0xFF

  /** A whole advert, as Android hands it over; null when nothing in it looks like Shimano. */
  fun parse(raw: ByteArray, rssi: Int, address: String, timestampMillis: Long): ShimanoAdvert? {
    var name: String? = null
    var manufacturer: ByteArray? = null
    val services = mutableListOf<String>()
    val serviceData = linkedMapOf<String, ByteArray>()

    for ((type, data) in AxsAdvertParser.adStructures(raw)) {
      when (type) {
        AD_SHORT_LOCAL_NAME,
        AD_COMPLETE_LOCAL_NAME -> name = data.decodeToString()
        AD_MANUFACTURER_DATA ->
          if (data.size >= 2 && data.u16le(0) == COMPANY_ID)
            manufacturer = (manufacturer ?: ByteArray(0)) + data.copyOfRange(2, data.size)
        AD_INCOMPLETE_16,
        AD_COMPLETE_16 -> data.uuids(2).forEach(services::add)
        AD_INCOMPLETE_32,
        AD_COMPLETE_32 -> data.uuids(4).forEach(services::add)
        AD_INCOMPLETE_128,
        AD_COMPLETE_128 -> data.uuids(16).forEach(services::add)
        AD_SERVICE_DATA_16 ->
          data.serviceData(2)?.let { (uuid, value) -> serviceData[uuid] = value }
        AD_SERVICE_DATA_32 ->
          data.serviceData(4)?.let { (uuid, value) -> serviceData[uuid] = value }
        AD_SERVICE_DATA_128 ->
          data.serviceData(16)?.let { (uuid, value) -> serviceData[uuid] = value }
      }
    }
    return parse(name, manufacturer, services, serviceData, rssi, address, timestampMillis)
      ?.copy(rawHex = raw.trimPadding().toHex())
  }

  /**
   * An advert already split into its parts, as Core Bluetooth delivers it: [manufacturer] is the
   * company `0x044A` payload without the company id, or null when it had none; UUIDs in any case
   * and length.
   */
  fun parse(
    name: String?,
    manufacturer: ByteArray?,
    services: List<String>,
    serviceData: Map<String, ByteArray>,
    rssi: Int,
    address: String,
    timestampMillis: Long,
  ): ShimanoAdvert? {
    val uuids = services.map(::fullUuid)
    val data = serviceData.mapKeys { fullUuid(it.key) }
    val shimano =
      manufacturer != null ||
        uuids.any(::isShimanoUuid) ||
        data.keys.any(::isShimanoUuid) ||
        name?.startsWith(NAME_PREFIX) == true
    if (!shimano) return null
    return ShimanoAdvert(
      address = address,
      name = name,
      rssi = rssi,
      timestampMillis = timestampMillis,
      manufacturerHex = manufacturer?.toHex(),
      services = uuids,
      serviceData = data.mapValues { it.value.toHex() },
      rawHex = null,
    )
  }

  fun isShimanoUuid(uuid: String): Boolean = uuid.lowercase().endsWith(UUID_SUFFIX)

  /** Little-endian UUIDs of [size] bytes each, as AD service lists carry them. */
  private fun ByteArray.uuids(size: Int): List<String> =
    (0 until this.size / size).map { copyOfRange(it * size, it * size + size).leUuid() }

  private fun ByteArray.serviceData(size: Int): Pair<String, ByteArray>? =
    if (this.size < size) null else copyOfRange(0, size).leUuid() to copyOfRange(size, this.size)

  /** Android pads a scan record with zeros past its last AD structure. */
  private fun ByteArray.trimPadding(): ByteArray {
    var end = 0
    while (end < size) {
      val length = u8(end)
      if (length == 0 || end + 1 + length > size) break
      end += 1 + length
    }
    return copyOfRange(0, end)
  }
}

private const val BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

/** A little-endian 2-, 4- or 16-byte UUID as lower-case 128-bit text. */
private fun ByteArray.leUuid(): String {
  val hex = reversedArray().joinToString("") { u8Hex(it) }
  return when (size) {
    2 -> "0000$hex$BASE_SUFFIX"
    4 -> "$hex$BASE_SUFFIX"
    else ->
      "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
        "${hex.substring(16, 20)}-${hex.substring(20)}"
  }
}

private fun u8Hex(byte: Byte) = (byte.toInt() and 0xFF).toString(16).padStart(2, '0')

/** Lower-case, 128-bit: Core Bluetooth shortens standard UUIDs to "180F". */
internal fun fullUuid(uuid: String): String {
  val s = uuid.lowercase()
  return when (s.length) {
    4 -> "0000$s$BASE_SUFFIX"
    8 -> "$s$BASE_SUFFIX"
    else -> s
  }
}

/** `180f` for standard UUIDs, `18ef` for Shimano's; anything else stays whole. */
fun shortUuid(uuid: String): String {
  val s = fullUuid(uuid)
  return when {
    s.startsWith("0000") && (s.endsWith(BASE_SUFFIX) || ShimanoAdvertParser.isShimanoUuid(s)) ->
      s.substring(4, 8)
    else -> s
  }
}
