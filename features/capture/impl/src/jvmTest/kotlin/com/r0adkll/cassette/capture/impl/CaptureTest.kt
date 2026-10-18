package com.r0adkll.cassette.capture.impl

import app.cash.turbine.test
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.data.ble.BATTERY_LEVEL_CHARACTERISTIC
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.data.db.createInMemoryDriver
import com.r0adkll.cassette.data.db.toDomain
import com.r0adkll.cassette.protocol.AdvertBattery
import com.r0adkll.cassette.protocol.AxsReading
import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.protocol.FrameType
import com.r0adkll.cassette.protocol.SystemBatteryTable
import com.r0adkll.cassette.settings.test.InMemorySettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
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
  private val captureLog = InMemoryCaptureLog()
  private val alerts = RecordingAlerts()
  private val recorder = CaptureRecorder(db, dispatchers)
  private val refresher = BatteryRefresher(recorder, gatt, db, captureLog, clock)

  @AfterTest fun close() = driver.close()

  @Test
  fun recordsKnownComponentsAndIgnoresOthers() = runTest {
    val bike = bikeWithHub()

    val heard = recorder.record(listOf(rdStatus(), stranger()))

    assertEquals(listOf(Fixtures.HUB.toString()), heard.map { it.component.key })
    val rd = db.componentQueries.selectByKey(Fixtures.HUB.toString()).executeAsOne()
    assertEquals(Fixtures.RD_ADDRESS, rd.address)
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

    assertEquals(Fixtures.HUB.toString(), heard.single().component.key)
  }

  @Test
  fun hubTableBringsInEveryComponentWithBatteryAndFirmware() = runTest {
    bikeWithHub()
    gatt.values =
      mapOf(
        SystemBatteryTable.CHARACTERISTIC to Fixtures.RED_TABLE,
        SystemBatteryTable.VERSIONS_CHARACTERISTIC to Fixtures.RED_VERSIONS,
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
    val logged =
      captureLog.list.value
        .filterIsInstance<com.r0adkll.cassette.capture.api.CaptureEvent.Gatt>()
        .single()
    assertEquals(com.r0adkll.cassette.capture.api.GattOutcome.Ok, logged.outcome)
    assertTrue(logged.hex!!.startsWith("01 04 6e 7a c1 53"))
  }

  @Test
  fun hubIsNotReadAgainUntilItIsDue() = runTest {
    bikeWithHub()
    gatt.values = mapOf(SystemBatteryTable.CHARACTERISTIC to Fixtures.RED_TABLE)
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
    gatt.values = mapOf(SystemBatteryTable.CHARACTERISTIC to Fixtures.RED_TABLE)
    val scanner = FakeScanner()
    val controller =
      DefaultCaptureController(
        scanner,
        recorder,
        refresher,
        db,
        InMemorySettings(),
        captureLog,
        alerts,
        clock,
      )

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
  fun addSixtySecondsLengthensTheScanUpToTheCap() = runTest {
    bikeWithHub()
    val controller =
      DefaultCaptureController(
        FakeScanner(),
        recorder,
        refresher,
        db,
        InMemorySettings(),
        captureLog,
        alerts,
        clock,
      )
    val more = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()

    controller.refresh(more).test {
      assertEquals(60, assertIs<RefreshProgress.Scanning>(awaitItem()).totalSeconds)
      more.emit(Unit)
      var item = assertIs<RefreshProgress.Scanning>(awaitItem())
      assertEquals(120, item.totalSeconds)
      // Three more would leave 300 s to go; the cap keeps it to 180.
      repeat(3) { more.emit(Unit) }
      item = assertIs<RefreshProgress.Scanning>(awaitItem())
      assertEquals(RefreshProgress.MaxSecondsLeft, item.secondsLeft)

      now += kotlin.time.Duration.parse("3m")
      var last: RefreshProgress = awaitItem()
      while (last is RefreshProgress.Scanning) last = awaitItem()
      assertIs<RefreshProgress.Done>(last)
      awaitComplete()
    }
  }

  @Test
  fun backgroundCaptureNeedsABike() = runTest {
    val scanner = FakeScanner()
    val controller =
      DefaultCaptureController(
        scanner,
        recorder,
        refresher,
        db,
        InMemorySettings(),
        captureLog,
        alerts,
        clock,
      )
    controller.ensureBackgroundCapture("test")
    assertNull(scanner.armed)

    bikeWithHub()
    controller.ensureBackgroundCapture("test")
    assertNotNull(scanner.armed)
  }

  @Test
  fun backgroundWakesTellAlertsWhichBikesWoke() = runTest {
    val bike = bikeWithHub()
    db.bikeQueries.markSeen((now - kotlin.time.Duration.parse("2h")).toEpochMilliseconds(), bike)
    val controller =
      DefaultCaptureController(
        FakeScanner(),
        recorder,
        refresher,
        db,
        InMemorySettings(),
        captureLog,
        alerts,
        clock,
      )

    controller.onBackgroundDelivery(listOf(rdStatus()))
    now += kotlin.time.Duration.parse("1m")
    controller.onBackgroundDelivery(listOf(rdStatus(at = now)))

    val id = com.r0adkll.cassette.core.BikeId(bike)
    assertEquals(listOf(setOf(id) to setOf(id), setOf(id) to emptySet()), alerts.captured)
  }

  private fun bikeWithHub(): Long {
    db.bikeQueries.insert("Road bike", BikeType.Road.name, null, Fixtures.HUB, 0, null)
    val bike = db.bikeQueries.lastInsertedId().executeAsOne()
    db.componentQueries.insert(
      bike,
      Fixtures.HUB.toString(),
      Fixtures.HUB,
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

  private fun rdStatus(serial: Long? = Fixtures.HUB, at: Instant = now) =
    AxsReading(
      key = serial?.toString() ?: Fixtures.RD_ADDRESS,
      serial = serial,
      address = Fixtures.RD_ADDRESS,
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

  private fun stranger() = rdStatus().copy(key = "999", serial = 999, address = "11:22:33:44:55:66")

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

  @Test
  fun backgroundCaptureFollowsSettings() = runTest {
    bikeWithHub()
    val scanner = FakeScanner()
    val settings = InMemorySettings()
    val controller =
      DefaultCaptureController(
        scanner,
        recorder,
        refresher,
        db,
        settings,
        captureLog,
        alerts,
        clock,
      )

    settings.setScanProfile(com.r0adkll.cassette.data.ble.ScanProfile.Batched30s)
    controller.ensureBackgroundCapture("test")
    assertEquals(com.r0adkll.cassette.data.ble.ScanProfile.Batched30s, scanner.armed)
    assertTrue(settings.captureHealth.value.armResult!!.startsWith("Armed"))

    settings.setBackgroundCapture(false)
    controller.ensureBackgroundCapture("test")
    assertNull(scanner.armed)
    assertEquals("Off in Settings", settings.captureHealth.value.armResult)
  }
}
