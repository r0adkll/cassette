package com.r0adkll.cassette.capture.impl

import app.cash.turbine.test
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.data.ble.AdvertScanner
import com.r0adkll.cassette.data.ble.ArmResult
import com.r0adkll.cassette.data.ble.BATTERY_LEVEL_CHARACTERISTIC
import com.r0adkll.cassette.data.ble.GattClient
import com.r0adkll.cassette.data.ble.GattResult
import com.r0adkll.cassette.data.ble.ScanProfile
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.data.db.createInMemoryDriver
import com.r0adkll.cassette.data.db.toDomain
import com.r0adkll.cassette.protocol.AdvertBattery
import com.r0adkll.cassette.protocol.AxsReading
import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.protocol.FrameType
import com.r0adkll.cassette.protocol.SystemBatteryTable
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest

class CaptureTest {
    private val driver = createInMemoryDriver()
    private val db = CassetteDatabase(driver)
    private val dispatchers =
        CassetteDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined)
    private var now = Instant.fromEpochMilliseconds(1_792_200_000_000)
    private val clock =
        object : Clock {
            override fun now() = this@CaptureTest.now
        }
    private val gatt = FakeGatt()
    private val recorder = CaptureRecorder(db, dispatchers)
    private val refresher = BatteryRefresher(recorder, gatt, db, clock)

    @AfterTest fun close() = driver.close()

    @Test
    fun recordsKnownComponentsAndIgnoresOthers() = runTest {
        val bike = bikeWithHub()

        val heard = recorder.record(listOf(rdStatus(), stranger()))

        assertEquals(listOf(HUB.toString()), heard.map { it.component.key })
        val rd = db.componentQueries.selectByKey(HUB.toString()).executeAsOne()
        assertEquals(RD_ADDRESS, rd.address)
        val sample = db.batterySampleQueries.latestForComponent(rd.id).executeAsOne().toDomain()
        assertEquals(78, sample.percent)
        assertEquals(ReadMethod.Advert, sample.method)
        assertEquals(
            now.toEpochMilliseconds(),
            db.bikeQueries.selectById(bike).executeAsOne().last_seen_at,
        )
    }

    @Test
    fun serialLessFramesResolveByAddress() = runTest {
        bikeWithHub()
        recorder.record(listOf(rdStatus()))

        val heard = recorder.record(listOf(rdStatus(serial = null, at = now + 1.minutes)))

        assertEquals(HUB.toString(), heard.single().component.key)
    }

    @Test
    fun hubTableBringsInEveryComponentWithBatteryAndFirmware() = runTest {
        bikeWithHub()
        gatt.values =
            mapOf(
                SystemBatteryTable.CHARACTERISTIC to RED_TABLE,
                SystemBatteryTable.VERSIONS_CHARACTERISTIC to RED_VERSIONS,
            )

        val outcome = refresher.refresh(recorder.record(listOf(rdStatus())), CaptureMode.Live)

        assertTrue(outcome.hubRead)
        val components = db.componentQueries.selectForBike(1).executeAsList().map { it.toDomain() }
        assertEquals(
            setOf(
                ComponentKind.REAR_DERAILLEUR,
                ComponentKind.FRONT_DERAILLEUR,
                ComponentKind.LEFT_SHIFTER,
                ComponentKind.RIGHT_SHIFTER,
            ),
            components.map { it.kind }.toSet(),
        )
        val left = components.single { it.kind == ComponentKind.LEFT_SHIFTER }
        assertEquals("2.7.3", left.firmware)
        val leftSample =
            db.batterySampleQueries.latestForComponent(left.id.value).executeAsOne().toDomain()
        assertEquals(2_946, leftSample.millivolts)
        assertEquals(BatteryStatus.OK, leftSample.reportedStatus)
        assertEquals(ReadMethod.Hub, leftSample.method)
        assertEquals(
            12_324L,
            components.single { it.kind == ComponentKind.REAR_DERAILLEUR }.actuations,
        )
        assertEquals(1, gatt.reads)
    }

    @Test
    fun hubIsNotReadAgainUntilItIsDue() = runTest {
        bikeWithHub()
        gatt.values = mapOf(SystemBatteryTable.CHARACTERISTIC to RED_TABLE)
        refresher.refresh(recorder.record(listOf(rdStatus())), CaptureMode.Background)

        now += 5.minutes
        refresher.refresh(recorder.record(listOf(rdStatus(at = now))), CaptureMode.Background)
        assertEquals(1, gatt.reads)

        now += 6.minutes
        refresher.refresh(recorder.record(listOf(rdStatus(at = now))), CaptureMode.Background)
        assertEquals(2, gatt.reads)
    }

    @Test
    fun powerMeterBatteryLevel() = runTest {
        val bike = bikeWithHub()
        db.componentQueries.insert(
            bike,
            "Quarq AGN12387",
            null,
            ComponentKind.POWER_METER.name,
            7,
            null,
            null,
            null,
            0,
            null,
        )
        gatt.values = mapOf(BATTERY_LEVEL_CHARACTERISTIC to byteArrayOf(85))

        val outcome = refresher.refresh(recorder.record(listOf(quarq())), CaptureMode.Live)

        assertTrue(outcome.powerMeterRead)
        val pm = db.componentQueries.selectByKey("Quarq AGN12387").executeAsOne()
        assertEquals(85L, db.batterySampleQueries.latestForComponent(pm.id).executeAsOne().percent)
    }

    @Test
    fun aFailedReadBacksOff() = runTest {
        bikeWithHub()
        gatt.fail = true
        refresher.refresh(recorder.record(listOf(rdStatus())), CaptureMode.Live)
        refresher.refresh(recorder.record(listOf(rdStatus(at = now))), CaptureMode.Live)
        assertEquals(1, gatt.reads)
        assertNull(db.bikeQueries.selectById(1).executeAsOne().hub_read_at)
    }

    @Test
    fun refreshEndsOnceEverythingIsRead() = runTest {
        bikeWithHub()
        gatt.values = mapOf(SystemBatteryTable.CHARACTERISTIC to RED_TABLE)
        val scanner = FakeScanner()
        val controller = DefaultCaptureController(scanner, recorder, refresher, db, clock)

        controller.refresh().test {
            scanner.adverts.emit(rdStatus())
            var item = awaitItem()
            while (item is RefreshProgress.Scanning) item = awaitItem()
            val done = assertIs<RefreshProgress.Done>(item)
            assertTrue(done.hubRead)
            awaitComplete()
        }
    }

    @Test
    fun backgroundCaptureNeedsABike() = runTest {
        val scanner = FakeScanner()
        val controller = DefaultCaptureController(scanner, recorder, refresher, db, clock)
        controller.ensureBackgroundCapture("test")
        assertNull(scanner.armed)

        bikeWithHub()
        controller.ensureBackgroundCapture("test")
        assertNotNull(scanner.armed)
    }

    private fun bikeWithHub(): Long {
        db.bikeQueries.insert("Road bike", BikeType.Road.name, null, HUB, 0, null)
        val bike = db.bikeQueries.lastInsertedId().executeAsOne()
        db.componentQueries.insert(
            bike,
            HUB.toString(),
            HUB,
            ComponentKind.REAR_DERAILLEUR.name,
            1066,
            0x81,
            null,
            null,
            0,
            null,
        )
        return bike
    }

    private fun rdStatus(serial: Long? = HUB, at: Instant = now) =
        AxsReading(
            key = serial?.toString() ?: RD_ADDRESS,
            serial = serial,
            address = RD_ADDRESS,
            name = null,
            rssi = -60,
            timestampMillis = at.toEpochMilliseconds(),
            frame = FrameType.STATUS,
            kind = if (serial == null) ComponentKind.UNKNOWN else ComponentKind.REAR_DERAILLEUR,
            battery = AdvertBattery(7_870, 78, BatteryStatus.OK),
            firmware = null,
            manufacturerHex = null,
            serviceDataHex = null,
            classByte = 0x81,
            modelId = 1066,
            bridge = true,
        )

    private fun stranger() =
        rdStatus().copy(key = "999", serial = 999, address = "11:22:33:44:55:66")

    private fun quarq() =
        AxsReading(
            key = "Quarq AGN12387",
            serial = null,
            address = "C0:FF:EE:00:00:01",
            name = "Quarq AGN12387",
            rssi = -60,
            timestampMillis = now.toEpochMilliseconds(),
            frame = FrameType.POWER_METER,
            kind = ComponentKind.POWER_METER,
            battery = null,
            firmware = null,
            manufacturerHex = null,
            serviceDataHex = null,
            modelId = 7,
        )

    private class FakeGatt : GattClient {
        var values: Map<String, ByteArray> = emptyMap()
        var fail = false
        var reads = 0
        override val isReady = true

        override suspend fun read(
            address: String,
            characteristics: List<String>,
            timeout: Duration,
        ): GattResult {
            reads++
            return if (fail) GattResult.Failed
            else GattResult.Values(values.filterKeys { it in characteristics }, emptyList())
        }
    }

    private class FakeScanner : AdvertScanner {
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

    private companion object {
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
    }
}
