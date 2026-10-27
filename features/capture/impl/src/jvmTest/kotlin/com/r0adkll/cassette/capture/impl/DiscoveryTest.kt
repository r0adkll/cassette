package com.r0adkll.cassette.capture.impl

import app.cash.turbine.test
import com.r0adkll.cassette.capture.api.FoundHub
import com.r0adkll.cassette.capture.api.FoundPowerMeter
import com.r0adkll.cassette.capture.api.HubDetails
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.CassetteDispatchers
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

class DiscoveryTest {
  private val driver = createInMemoryDriver()
  private val db = CassetteDatabase(driver)
  private val dispatchers =
    CassetteDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined)
  private val now = Instant.fromEpochMilliseconds(1_792_200_000_000)
  private val clock =
    object : Clock {
      override fun now() = this@DiscoveryTest.now
    }
  private val scanner = FakeScanner()
  private val gatt = FakeGatt()
  private val captureLog = InMemoryCaptureLog()
  private val alerts = RecordingAlerts()
  private val recorder = CaptureRecorder(db, dispatchers)
  private val controller =
    DefaultCaptureController(
      scanner,
      recorder,
      BatteryRefresher(recorder, gatt, db, captureLog, clock),
      db,
      InMemorySettings(),
      captureLog,
      alerts,
      clock,
    )
  private val discovery =
    DefaultBikeDiscovery(scanner, gatt, recorder, controller, db, dispatchers, clock)

  @AfterTest fun close() = driver.close()

  @Test
  fun findsHubsAndPowerMetersStrongestFirst() = runTest {
    discovery.discover().test {
      awaitItem() // nothing yet
      scanner.adverts.emit(hub(serial = 7, rssi = -80))
      awaitItem()
      scanner.adverts.emit(hub(serial = Fixtures.HUB, rssi = -55))
      val found = awaitItem()
      assertEquals(listOf(Fixtures.HUB, 7L), found.hubs.map { it.serial })
      assertEquals(78, found.hubs.first().battery?.percent)

      scanner.adverts.emit(quarq("Quarq AGN12387", -60))
      assertEquals(listOf("Quarq AGN12387"), awaitItem().powerMeters.map { it.name })
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun shiftersAndBeaconsAreNotHubs() = runTest {
    discovery.discover().test {
      awaitItem()
      scanner.adverts.emit(hub(serial = 1132134130).copy(frame = FrameType.BEACON, bridge = false))
      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun addingABikeSavesEveryComponentAndArmsCapture() = runTest {
    gatt.values =
      mapOf(
        SystemBatteryTable.CHARACTERISTIC to Fixtures.RED_TABLE,
        SystemBatteryTable.VERSIONS_CHARACTERISTIC to Fixtures.RED_VERSIONS,
      )
    val hub = discovery.readHub(foundHub())
    assertNotNull(hub)
    assertEquals(
      listOf(
        ComponentKind.REAR_DERAILLEUR,
        ComponentKind.FRONT_DERAILLEUR,
        ComponentKind.RIGHT_SHIFTER,
        ComponentKind.LEFT_SHIFTER,
      ),
      hub.components.map { it.kind },
    )
    assertEquals(78, hub.components.first().percent)

    val bikeId =
      discovery.addBike(
        "  Road bike ",
        BikeType.Road,
        listOf(hub),
        FoundPowerMeter("Quarq AGN12387", PM_ADDRESS, 7, -60),
      )

    val bike = db.bikeQueries.selectById(bikeId.value).executeAsOne().toDomain()
    assertEquals("Road bike", bike.name)
    assertEquals(Fixtures.HUB, bike.hubSerial)
    val components =
      db.componentQueries.selectForBike(bikeId.value).executeAsList().map { it.toDomain() }
    assertEquals(5, components.size)
    assertEquals(
      Fixtures.RD_ADDRESS,
      components.single { it.kind == ComponentKind.REAR_DERAILLEUR }.address,
    )
    assertEquals(PM_ADDRESS, components.single { it.kind == ComponentKind.POWER_METER }.address)
    val rd = components.single { it.kind == ComponentKind.REAR_DERAILLEUR }
    assertEquals(
      78,
      db.batterySampleQueries.latestForComponent(rd.id.value).executeAsOne().percent?.toInt(),
    )
    assertNotNull(scanner.armed)

    // Once added, the bike's hub is no longer offered, only known.
    discovery.discover().test {
      assertEquals(emptyList(), awaitItem().hubs)
      scanner.adverts.emit(hub(serial = Fixtures.HUB, rssi = -50))
      val found = awaitItem()
      assertEquals(emptyList(), found.hubs)
      assertEquals(listOf(Fixtures.HUB to bikeId), found.known.map { it.hub.serial to it.bike })
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun aBikeWithTwoHubsIsTwoSystems() = runTest {
    val bikeId =
      discovery.addBike(
        "Mountain bike",
        BikeType.Mtb,
        listOf(
          details(Fixtures.DROPPER, Fixtures.DROPPER_TABLE),
          details(Fixtures.SHOCK, Fixtures.SHOCK_TABLE),
        ),
        null,
      )

    // Known by the first hub; both are its systems.
    assertEquals(
      Fixtures.DROPPER,
      db.bikeQueries.selectById(bikeId.value).executeAsOne().hub_serial,
    )
    assertEquals(
      listOf(Fixtures.DROPPER, Fixtures.SHOCK),
      db.hubQueries.selectForBike(bikeId.value).executeAsList().map { it.serial },
    )
    val systems =
      db.componentQueries.selectForBike(bikeId.value).executeAsList().associate {
        it.serial to it.hub_serial
      }
    assertEquals(
      mapOf<Long?, Long?>(
        Fixtures.DROPPER to Fixtures.DROPPER,
        Fixtures.FORK to Fixtures.DROPPER,
        Fixtures.POD to Fixtures.DROPPER,
        Fixtures.SHOCK to Fixtures.SHOCK,
        Fixtures.MTB_RD to Fixtures.SHOCK,
        Fixtures.CONTROLLER to Fixtures.SHOCK,
      ),
      systems,
    )
  }

  @Test
  fun aSystemJoinsABikeItsMissing() = runTest {
    // The tester's bike as Cassette first saved it: the dropper's system only.
    val bikeId =
      discovery.addBike(
        "Mountain bike",
        BikeType.Mtb,
        listOf(details(Fixtures.DROPPER, Fixtures.DROPPER_TABLE)),
        null,
      )
    assertEquals(3, db.componentQueries.selectForBike(bikeId.value).executeAsList().size)

    discovery.addSystems(bikeId, listOf(details(Fixtures.SHOCK, Fixtures.SHOCK_TABLE)))

    assertEquals(6, db.componentQueries.selectForBike(bikeId.value).executeAsList().size)
    assertEquals(2, db.hubQueries.selectForBike(bikeId.value).executeAsList().size)
    assertEquals(
      Fixtures.SHOCK_ADDRESS,
      db.componentQueries.selectByKey(Fixtures.SHOCK.toString()).executeAsOne().address,
    )
  }

  @Test
  fun anotherBikesHubDoesNotMove() = runTest {
    val road =
      discovery.addBike(
        "Road bike",
        BikeType.Road,
        listOf(details(Fixtures.SHOCK, Fixtures.SHOCK_TABLE)),
        null,
      )
    val mtb =
      discovery.addBike(
        "Mountain bike",
        BikeType.Mtb,
        listOf(details(Fixtures.DROPPER, Fixtures.DROPPER_TABLE)),
        null,
      )

    discovery.addSystems(mtb, listOf(details(Fixtures.SHOCK, Fixtures.SHOCK_TABLE)))

    assertEquals(road.value, db.hubQueries.selectBySerial(Fixtures.SHOCK).executeAsOne().bike_id)
    assertEquals(3, db.componentQueries.selectForBike(mtb.value).executeAsList().size)
  }

  @Test
  fun aFailedHubReadReturnsNull() = runTest {
    gatt.fail = true
    assertNull(discovery.readHub(foundHub()))
  }

  private fun details(serial: Long, table: ByteArray) =
    HubDetails(
      FoundHub(
        serial = serial,
        address =
          if (serial == Fixtures.SHOCK) Fixtures.SHOCK_ADDRESS else Fixtures.DROPPER_ADDRESS,
        kind = ComponentKind.UNKNOWN,
        modelId = null,
        rssi = -60,
        battery = null,
        firmware = null,
      ),
      SystemBatteryTable.parse(table)!!,
      null,
    )

  private fun foundHub() =
    FoundHub(
      serial = Fixtures.HUB,
      address = Fixtures.RD_ADDRESS,
      kind = ComponentKind.REAR_DERAILLEUR,
      modelId = 1066,
      rssi = -55,
      battery = AdvertBattery(7_870, 78, BatteryStatus.OK),
      firmware = "2.55.8",
    )

  private fun hub(serial: Long, rssi: Int = -60) =
    AxsReading(
      key = serial.toString(),
      serial = serial,
      address = if (serial == Fixtures.HUB) Fixtures.RD_ADDRESS else "AA:BB:CC:DD:EE:$serial",
      name = null,
      rssi = rssi,
      timestampMillis = now.toEpochMilliseconds(),
      frame = FrameType.STATUS,
      kind = ComponentKind.REAR_DERAILLEUR,
      battery = AdvertBattery(7_870, 78, BatteryStatus.OK),
      firmware = "2.55.8",
      manufacturerHex = null,
      serviceDataHex = null,
      classByte = 0x81,
      modelId = 1066,
      bridge = true,
    )

  private fun quarq(name: String, rssi: Int) =
    AxsReading(
      key = name,
      serial = null,
      address = PM_ADDRESS,
      name = name,
      rssi = rssi,
      timestampMillis = now.toEpochMilliseconds(),
      frame = FrameType.POWER_METER,
      kind = ComponentKind.POWER_METER,
      battery = null,
      firmware = null,
      manufacturerHex = null,
      serviceDataHex = null,
      modelId = 7,
    )

  private companion object {
    const val PM_ADDRESS = "C0:FF:EE:00:00:01"
  }
}
