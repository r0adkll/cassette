package com.r0adkll.cassette.scan

import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import android.util.Log
import com.r0adkll.cassette.axs.AxsAdvertParser
import com.r0adkll.cassette.axs.toHex

/** An advert that looks SRAM-related but didn't decode as an AXS battery broadcast. */
data class NearbyCandidate(
  val address: String,
  val name: String?,
  val rssi: Int,
  val timestampMillis: Long,
  val reasons: List<String>,
  val rawHex: String,
)

private val CYCLING_POWER = ParcelUuid.fromString("00001818-0000-1000-8000-00805f9b34fb")
private val SRAM_SERVICE = ParcelUuid.fromString("0000fe51-0000-1000-8000-00805f9b34fb")
private val SRAM_NAMES = Regex("""^(sram|quarq|axs)""", RegexOption.IGNORE_CASE)

internal fun ScanResult.toCandidate(): NearbyCandidate? {
  val record = scanRecord ?: return null
  val name = record.deviceName
  val reasons = buildList {
    if (name != null && SRAM_NAMES.containsMatchIn(name)) add("name")
    if (record.getManufacturerSpecificData(AxsAdvertParser.COMPANY_ID) != null) add("sram-mfg")
    if (
      record.serviceData?.containsKey(SRAM_SERVICE) == true ||
        record.serviceUuids?.contains(SRAM_SERVICE) == true
    )
      add("sram-svc")
    if (record.serviceUuids?.contains(CYCLING_POWER) == true) add("cycling-power")
  }
  if (reasons.isEmpty()) return null
  val rawHex =
    AxsAdvertParser.adStructures(record.bytes).joinToString(" | ") { (type, data) ->
      "%02x: %s".format(type, data.toHex())
    }
  Log.d(
    "CassetteScan",
    "candidate ${device.address} name=$name rssi=$rssi $reasons raw=[$rawHex]",
  )
  return NearbyCandidate(device.address, name, rssi, System.currentTimeMillis(), reasons, rawHex)
}
