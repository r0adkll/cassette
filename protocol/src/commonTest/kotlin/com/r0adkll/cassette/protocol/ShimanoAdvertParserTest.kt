package com.r0adkll.cassette.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * No Shimano captures yet (decision 0029): these adverts are built from the markers public projects
 * match on, so they check the parsing, not Shimano's layout.
 */
class ShimanoAdvertParserTest {
  /** `000018ef-5348-494d-414e-4f5f424c4500`, little-endian as an AD list carries it. */
  private val sbiService = bytes("00 45 4c 42 5f 4f 4e 41 4d 49 48 53 ef 18 00 00")

  @Test
  fun findsShimanoServiceUuid() {
    val raw =
      adStructure(0x01, bytes("06")) +
        adStructure(0x07, sbiService) +
        adStructure(0x09, "RDR9250 12345".encodeToByteArray()) +
        ByteArray(8)

    val advert = parse(raw)!!

    assertEquals(listOf(ShimanoAdvertParser.SBI_SERVICE), advert.services)
    assertEquals(listOf("18ef"), advert.shortServices)
    assertEquals("RDR9250 12345", advert.name)
    assertNull(advert.manufacturerHex)
    // The zero padding is dropped.
    assertEquals(raw.copyOfRange(0, raw.size - 8).toHex(), advert.rawHex)
  }

  @Test
  fun findsCompanyIdAndJoinsItsPayloads() {
    val raw =
      adStructure(0xFF, bytes("4a 04 01 02")) +
        adStructure(0xFF, bytes("4a 04 03")) +
        adStructure(0x03, bytes("0f 18 0a 18"))

    val advert = parse(raw)!!

    assertEquals("01 02 03", advert.manufacturerHex)
    assertEquals(listOf("180f", "180a"), advert.shortServices)
  }

  @Test
  fun findsRearDerailleurNameAlone() {
    assertNotNull(parse(adStructure(0x09, "RDR7150".encodeToByteArray())))
  }

  @Test
  fun keepsServiceData() {
    val raw =
      adStructure(0x09, "RDR8150".encodeToByteArray()) + adStructure(0x16, bytes("0f 18 57"))

    assertEquals(mapOf("0000180f-0000-1000-8000-00805f9b34fb" to "57"), parse(raw)!!.serviceData)
  }

  @Test
  fun ignoresAxsAndOtherAdverts() {
    val axs =
      adStructure(0xFF, bytes("33 09 00 04 05 b6 1f 03 80 64")) +
        adStructure(0x09, "SRAM 1016200351".encodeToByteArray())
    assertNull(parse(axs))
    assertNull(parse(adStructure(0x03, bytes("0f 18"))))
    assertNull(parse(ByteArray(0)))
  }

  @Test
  fun readsCoreBluetoothParts() {
    val advert =
      ShimanoAdvertParser.parse(
        name = null,
        manufacturer = null,
        services = listOf("000018EF-5348-494D-414E-4F5F424C4500", "180F"),
        serviceData = emptyMap(),
        rssi = -70,
        address = "0C1A2B3C-0000-0000-0000-000000000000",
        timestampMillis = 0L,
      )!!

    assertEquals(listOf("18ef", "180f"), advert.shortServices)
    assertNull(advert.rawHex)
  }

  private fun parse(raw: ByteArray) =
    ShimanoAdvertParser.parse(raw, rssi = -60, address = "AA:BB:CC:DD:EE:FF", timestampMillis = 0L)

  private fun adStructure(type: Int, data: ByteArray) =
    byteArrayOf((data.size + 1).toByte(), type.toByte()) + data

  private fun bytes(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
