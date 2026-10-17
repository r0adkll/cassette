package com.r0adkll.cassette.axs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Status-frame samples are the captures published in esphome-sram-axs docs/protocol.md; beacon,
 * version and power meter samples are from a Red AXS bike (rear/front derailleur, two shifters,
 * Quarq).
 */
class AxsAdvertParserTest {
    private val standardManufacturer = bytes("00 00 01 02 00 04 05 b6 1f 03 80 64")
    private val rearDerailleurService = bytes("0d 03 9f fc 91 3c 32 04 81")

    @Test
    fun decodesStatusFrameRecordsAndServiceFrame() {
        val raw =
            adStructure(0xFF, bytes("33 09") + standardManufacturer) +
                adStructure(0x16, bytes("51 fe") + rearDerailleurService) +
                adStructure(0x09, "SRAM 1016200351".encodeToByteArray()) +
                ByteArray(10) // Android pads scan records with zeros

        val reading = parse(raw)!!

        assertEquals(FrameType.STATUS, reading.frame)
        assertEquals(1016200351L, reading.serial)
        assertEquals("1016200351", reading.key)
        assertEquals(
            AdvertBattery(millivolts = 8118, percent = 100, reportedStatus = BatteryStatus.OK),
            reading.battery,
        )
        assertEquals(ComponentKind.REAR_DERAILLEUR, reading.kind)
        assertEquals(0x81, reading.classByte)
        assertEquals(1074, reading.modelId)
        assertTrue(reading.bridge)
        assertNull(reading.firmware) // bytes 2–3 are an unknown record, not firmware
    }

    @Test
    fun readsServiceFrameFromManufacturerRecordWhenServiceDataMissing() {
        // Second 0x0933 AD structure: record 0x0101 (len 9) wrapping the service frame, plus a
        // trailing byte.
        val raw =
            adStructure(0xFF, bytes("33 09") + standardManufacturer) +
                adStructure(0xFF, bytes("33 09 01 01 09") + rearDerailleurService + bytes("01"))

        val reading = parse(raw)!!

        assertEquals(1016200351L, reading.serial)
        assertEquals(ComponentKind.REAR_DERAILLEUR, reading.kind)
        assertEquals(8118, reading.battery?.millivolts)
    }

    @Test
    fun readsFirmwareFromVersionRecord() {
        // Red RD: record 0x0402 = version 2.55.8 + build tag "g99fff4".
        val raw =
            adStructure(0xFF, bytes("33 09 00 00 01 02 00 04 05 cb 1e 03 80 51")) +
                adStructure(0xFF, bytes("33 09 02 04 0a 02 37 08 67 39 39 66 66 66 34 02"))

        val reading = parse(raw)!!

        assertEquals("2.55.8", reading.firmware)
        assertEquals(
            AdvertBattery(millivolts = 7883, percent = 81, reportedStatus = BatteryStatus.OK),
            reading.battery,
        )
    }

    @Test
    fun coinCellStatusFrameUsesDeviceTypeForSide() {
        // Synthetic frame (esphome's coin-cell values): flags2 0x02 = device type only (no bridge).
        // Type 0x00 and model 1034 (0x040a) are both a left drop-bar shifter.
        val raw =
            adStructure(0xFF, bytes("33 09 00 00 01 02 00 04 05 c0 0a 03 80 ff")) +
                adStructure(0x16, bytes("51 fe 0d 02 9f fc 91 3c 0a 04 00"))

        val reading = parse(raw)!!

        assertEquals(
            AdvertBattery(millivolts = 2752, percent = null, reportedStatus = BatteryStatus.OK),
            reading.battery,
        )
        assertEquals(ComponentKind.LEFT_SHIFTER, reading.kind)
        assertEquals(false, reading.bridge)
    }

    @Test
    fun decodesRedBeaconsAndResolvesKindFromModelId() {
        fun beacon(service: String) =
            parse(bytes("02 01 06 03 02 51 fe") + adStructure(0x16, bytes("51 fe $service")))!!

        val right = beacon("4c f2 fe 7a 43 ec 03 02 07 03")
        assertEquals(FrameType.BEACON, right.frame)
        assertEquals(1132134130L, right.serial)
        assertEquals(1004, right.modelId)
        assertEquals(ComponentKind.RIGHT_SHIFTER, right.kind)
        assertEquals("2.7.3", right.firmware)
        assertNull(right.battery)

        assertEquals(ComponentKind.LEFT_SHIFTER, beacon("4c 31 2e 7b 43 ed 03 02 07 03").kind)
        assertEquals(ComponentKind.FRONT_DERAILLEUR, beacon("4c cb 66 63 4e ea 03 02 07 03").kind)
    }

    @Test
    fun decodesQuarqPowerMeter() {
        val raw =
            bytes("03 19 84 04 02 01 06 05 03 18 18 51 fe") +
                adStructure(0x09, "Quarq AGN12387".encodeToByteArray()) +
                adStructure(
                    0x16,
                    bytes("51 fe e6 00 00 00 00 00 00 00 63 30 cd 00 07 95 65 0b 0a 12 00 00 00"),
                )

        val reading = parse(raw)!!

        assertEquals(FrameType.POWER_METER, reading.frame)
        assertEquals(ComponentKind.POWER_METER, reading.kind)
        assertEquals("Quarq AGN12387", reading.key)
        assertEquals(7, reading.modelId) // quarq model code & 0x3f: DZero
        assertNull(reading.battery)
    }

    @Test
    fun zeroPercentIsUnknownNotCritical() {
        assertEquals(BatteryStatus.UNKNOWN, batteryStatus(millivolts = null, percent = 0))
        assertEquals(BatteryStatus.CRITICAL, batteryStatus(millivolts = null, percent = 5))
        assertEquals(BatteryStatus.LOW, batteryStatus(millivolts = null, percent = 20))
    }

    @Test
    fun fallsBackToNameThenAddressForIdentity() {
        val manufacturerOnly = adStructure(0xFF, bytes("33 09") + standardManufacturer)

        assertEquals(
            "42",
            parse(manufacturerOnly + adStructure(0x09, "SRAM 42".encodeToByteArray()))!!.key,
        )
        assertEquals(ADDRESS, parse(manufacturerOnly)!!.key)
    }

    @Test
    fun ignoresOtherManufacturersAndTruncatedPayloads() {
        assertNull(parse(adStructure(0xFF, bytes("4c 00") + standardManufacturer)))
        assertNull(parse(adStructure(0xFF, bytes("33 09 00 00 01 02"))))
        assertNull(parse(adStructure(0x16, bytes("51 fe 4c f2"))))
        assertNull(parse(bytes("1f ff 33 09"))) // length byte overruns the buffer
    }

    private fun parse(raw: ByteArray) =
        AxsAdvertParser.parse(raw, rssi = -60, address = ADDRESS, timestampMillis = 0L)

    private fun adStructure(type: Int, data: ByteArray) =
        byteArrayOf((data.size + 1).toByte(), type.toByte()) + data

    private fun bytes(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val ADDRESS = "AA:BB:CC:DD:EE:FF"
    }
}
