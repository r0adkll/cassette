package com.r0adkll.cassette.data

import com.r0adkll.cassette.axs.AdvertBattery
import com.r0adkll.cassette.axs.AxsReading
import com.r0adkll.cassette.axs.BatteryStatus
import com.r0adkll.cassette.axs.ComponentKind
import com.r0adkll.cassette.axs.ComponentVersions
import com.r0adkll.cassette.axs.FrameType
import com.r0adkll.cassette.axs.SystemComponent
import com.r0adkll.cassette.axs.SystemTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureStateTest {
    @Test
    fun serialLessFrameMergesIntoKnownComponentAtSameAddress() {
        val state =
            CaptureState()
                .withReading(reading(serial = SERIAL, at = 0L), CaptureSource.LIVE)
                .withReading(
                    reading(serial = null, at = 10_000L, millivolts = 7885),
                    CaptureSource.LIVE,
                )

        assertEquals(setOf(SERIAL.toString()), state.devices.keys)
        val record = state.devices.getValue(SERIAL.toString())
        val latest = record.latest
        assertEquals(7885, record.battery?.millivolts)
        assertEquals(ComponentKind.REAR_DERAILLEUR, latest.kind)
        assertEquals(0x81, latest.classByte)
    }

    @Test
    fun addressKeyedRecordIsFoldedInOnceSerialArrives() {
        val state =
            CaptureState()
                .withReading(reading(serial = null, at = 0L), CaptureSource.BACKGROUND)
                .withReading(reading(serial = SERIAL, at = 10_000L), CaptureSource.BACKGROUND)

        assertEquals(setOf(SERIAL.toString()), state.devices.keys)
        assertEquals(listOf(SERIAL.toString()), state.history.map { it.key }.distinct())
    }

    @Test
    fun beaconFrameKeepsLastGattBatteryAndOnlyNeedsGattWhenStale() {
        val beacon =
            reading(serial = SERIAL, at = 0L).copy(frame = FrameType.BEACON, battery = null)
        val key = SERIAL.toString()

        val seen = CaptureState().withReading(beacon, CaptureSource.LIVE)
        assertTrue(seen.needsGattBattery(key, nowMillis = 0L, minAgeMillis = 60_000L))

        val read = seen.withGattBattery(key, percent = 77, at = 1_000L)
        val seenAgain = read.withReading(beacon.copy(timestampMillis = 30_000L), CaptureSource.LIVE)

        assertEquals(77, seenAgain.devices.getValue(key).battery?.percent)
        assertEquals(BatteryMethod.GATT, seenAgain.devices.getValue(key).battery?.method)
        assertFalse(seenAgain.needsGattBattery(key, nowMillis = 30_000L, minAgeMillis = 60_000L))
        assertTrue(seenAgain.needsGattBattery(key, nowMillis = 61_000L, minAgeMillis = 60_000L))
    }

    @Test
    fun statusFrameNeverNeedsGatt() {
        val state =
            CaptureState().withReading(reading(serial = SERIAL, at = 0L), CaptureSource.LIVE)
        assertFalse(
            state.needsGattBattery(SERIAL.toString(), nowMillis = Long.MAX_VALUE, minAgeMillis = 0L)
        )
    }

    @Test
    fun hubTableAppliesStatusShiftsAndFirmwareButLeavesHubBatteryToAdverts() {
        val table =
            SystemTable(
                masterSerial = SERIAL,
                rows =
                    listOf(
                        component(
                            0x80,
                            1002,
                            FD,
                            millivolts = 8087,
                            status = BatteryStatus.OK,
                            actuations = 0,
                        ),
                        component(
                            0x00,
                            1005,
                            LEFT,
                            millivolts = 2946,
                            status = BatteryStatus.LOW,
                            actuations = 0,
                        ),
                        component(
                            0x81,
                            1066,
                            SERIAL,
                            millivolts = 7884,
                            status = BatteryStatus.OK,
                            actuations = 12_324,
                        ),
                    ),
            )
        val versions =
            SystemTable(
                SERIAL,
                listOf(
                    ComponentVersions(0x80, 1002, FD, "2.7.3", softDevice = 132, bootloader = "1.0")
                ),
            )

        val state =
            CaptureState()
                .withReading(reading(serial = SERIAL, at = 0L), CaptureSource.LIVE)
                .withHubTable(
                    SERIAL.toString(),
                    table,
                    versions,
                    at = 1_000L,
                    source = CaptureSource.LIVE,
                )

        val fd = state.devices.getValue(FD.toString())
        assertEquals(ComponentKind.FRONT_DERAILLEUR, fd.latest.kind)
        assertEquals(FrameType.HUB_ENTRY, fd.latest.frame)
        assertEquals(BatteryMethod.HUB, fd.battery?.method)
        assertEquals("2.7.3", fd.hubFirmware)
        assertEquals(SERIAL, fd.systemSerial)

        val left = state.devices.getValue(LEFT.toString())
        assertEquals(ComponentKind.LEFT_SHIFTER, left.latest.kind)
        assertEquals(
            BatteryStatus.LOW,
            left.battery?.status,
        ) // reported status wins over the healthy voltage

        val hub = state.devices.getValue(SERIAL.toString())
        assertEquals(12_324L, hub.actuations)
        assertEquals(BatteryMethod.ADVERT, hub.battery?.method)
        assertEquals(81, hub.battery?.percent)
    }

    @Test
    fun hubReadUnderAddressCountsForSerialOnceKnown() {
        val state =
            CaptureState()
                .withReading(reading(serial = null, at = 0L), CaptureSource.BACKGROUND)
                .withHubTable(
                    ADDRESS,
                    SystemTable(SERIAL, emptyList()),
                    versions = null,
                    at = 1_000L,
                    source = CaptureSource.BACKGROUND,
                )
                .withReading(reading(serial = SERIAL, at = 10_000L), CaptureSource.BACKGROUND)

        assertFalse(
            state.needsHubRead(SERIAL.toString(), nowMillis = 20_000L, minAgeMillis = 60_000L)
        )
        assertEquals(setOf(SERIAL.toString()), state.devices.keys)
    }

    @Test
    fun beaconKeepsKindAndFirmwareLearnedEarlier() {
        val beacon =
            reading(serial = LEFT, at = 0L)
                .copy(
                    frame = FrameType.BEACON,
                    kind = ComponentKind.UNKNOWN,
                    battery = null,
                    firmware = null,
                    classByte = null,
                )
        val state =
            CaptureState()
                .withHubTable(
                    SERIAL.toString(),
                    SystemTable(
                        SERIAL,
                        listOf(component(0x00, 1005, LEFT, 2946, BatteryStatus.OK, 0)),
                    ),
                    null,
                    0L,
                    CaptureSource.LIVE,
                )
                .withReading(beacon.copy(timestampMillis = 10_000L), CaptureSource.LIVE)

        val left = state.devices.getValue(LEFT.toString())
        assertEquals(ComponentKind.LEFT_SHIFTER, left.latest.kind)
        assertEquals(0x00, left.latest.classByte)
        assertEquals(BatteryMethod.HUB, left.battery?.method)
    }

    private fun component(
        type: Int,
        model: Int,
        serial: Long,
        millivolts: Int,
        status: BatteryStatus,
        actuations: Long,
    ) =
        SystemComponent(
            type,
            model,
            serial,
            status,
            millivolts,
            updateTime = 0,
            runtime = 0,
            actuations = actuations,
        )

    private fun reading(serial: Long?, at: Long, millivolts: Int = 7883) =
        AxsReading(
            key = serial?.toString() ?: ADDRESS,
            serial = serial,
            address = ADDRESS,
            name = serial?.let { "SRAM $it" },
            rssi = -70,
            timestampMillis = at,
            frame = FrameType.STATUS,
            kind = if (serial != null) ComponentKind.REAR_DERAILLEUR else ComponentKind.UNKNOWN,
            battery = AdvertBattery(millivolts, percent = 81),
            firmware = "2.55.8",
            manufacturerHex = "",
            serviceDataHex = null,
            classByte = if (serial != null) 0x81 else null,
        )

    private companion object {
        const val SERIAL = 1405188718L
        const val ADDRESS = "F4:8A:10:D6:8A:97"
        const val FD = 1315137227L
        const val LEFT = 1132146225L
    }
}
