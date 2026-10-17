package com.r0adkll.cassette.data.db

import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.ComponentKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CassetteDatabaseTest {
  private val driver = createInMemoryDriver()
  private val db = CassetteDatabase(driver)

  @AfterTest fun close() = driver.close()

  @Test
  fun latestReadingPerComponent() {
    val bike = insertBike(hubSerial = 1405188718)
    val rd = insertComponent(bike, "1405188718", ComponentKind.REAR_DERAILLEUR)
    val left = insertComponent(bike, "1132146225", ComponentKind.LEFT_SHIFTER)

    sample(rd, at = 1_000, mV = 7_900)
    sample(rd, at = 2_000, mV = 7_870)
    sample(left, at = 1_500, mV = 2_910, status = null)

    val latest = db.batterySampleQueries.latestForBike(bike).executeAsList().map { it.toDomain() }

    assertEquals(2, latest.size)
    val rdLatest = latest.single { it.componentId.value == rd }
    assertEquals(7_870, rdLatest.millivolts)
    assertEquals(BatteryStatus.OK, rdLatest.reportedStatus)
    assertEquals(ReadMethod.Hub, rdLatest.method)
    assertNull(latest.single { it.componentId.value == left }.reportedStatus)
  }

  @Test
  fun mostRecentlySeenBikeComesFirst() {
    val road = insertBike(hubSerial = 1)
    val gravel = insertBike(hubSerial = 2, name = "Gravel bike")
    db.bikeQueries.markSeen(at = 5_000, id = gravel)
    db.bikeQueries.markSeen(at = 4_000, id = road)
    // An older sighting never moves last-seen backwards.
    db.bikeQueries.markSeen(at = 1_000, id = gravel)

    val bikes = db.bikeQueries.selectAll().executeAsList().map { it.toDomain() }

    assertEquals(listOf("Gravel bike", "Road bike"), bikes.map { it.name })
    assertEquals(5_000, bikes.first().lastSeenAt?.toEpochMilliseconds())
  }

  @Test
  fun componentsMissingFor30DaysAreRemovedAndComeBackWhenSeen() {
    val bike = insertBike(hubSerial = 1)
    val fd = insertComponent(bike, "1315137227", ComponentKind.FRONT_DERAILLEUR, seenAt = 1_000)
    insertComponent(bike, "1405188718", ComponentKind.REAR_DERAILLEUR, seenAt = 9_000)

    db.componentQueries.markMissingRemoved(at = 10_000, bikeId = bike, cutoff = 5_000)
    assertEquals(1, db.componentQueries.selectForBike(bike).executeAsList().size)

    db.componentQueries.markSeen(
      at = 11_000,
      kind = null,
      modelId = null,
      deviceType = null,
      firmware = "2.7.3",
      actuations = null,
      id = fd,
    )
    val back = db.componentQueries.selectByKey("1315137227").executeAsOne().toDomain()
    assertNull(back.removedAt)
    assertEquals("2.7.3", back.firmware)
    assertEquals(ComponentKind.FRONT_DERAILLEUR, back.kind)
  }

  @Test
  fun downsampleKeepsTheLastReadingOfEachOldDay() {
    val bike = insertBike(hubSerial = 1)
    val rd = insertComponent(bike, "1405188718", ComponentKind.REAR_DERAILLEUR)
    val day = 86_400_000L
    sample(rd, at = 1 * day + 100, mV = 7_950)
    sample(rd, at = 1 * day + 200, mV = 7_940)
    sample(rd, at = 2 * day + 100, mV = 7_930)
    sample(rd, at = 40 * day, mV = 7_800)
    sample(rd, at = 40 * day + 1, mV = 7_790)

    db.batterySampleQueries.downsampleBefore(cutoff = 10 * day)

    val left =
      db.batterySampleQueries.historyForComponent(rd, since = 0).executeAsList().map {
        it.millivolts
      }
    assertEquals(listOf(7_940L, 7_930L, 7_800L, 7_790L), left)
  }

  @Test
  fun deletingABikeDeletesItsComponentsAndHistory() {
    val bike = insertBike(hubSerial = 1)
    val rd = insertComponent(bike, "1405188718", ComponentKind.REAR_DERAILLEUR)
    sample(rd, at = 1_000, mV = 7_900)
    db.alertStateQueries.upsert(rd, BatteryStatus.LOW.name, 1_000)

    db.bikeQueries.delete(bike)

    assertNull(db.componentQueries.selectByKey("1405188718").executeAsOneOrNull())
    assertNull(db.batterySampleQueries.latestForComponent(rd).executeAsOneOrNull())
    assertNull(db.alertStateQueries.select(rd).executeAsOneOrNull())
  }

  @Test
  fun unknownEnumNamesFromANewerBuildDegradeSafely() {
    val bike = insertBike(hubSerial = 1)
    db.componentQueries.insert(bike, "x", null, "HOVERBOARD", null, null, null, null, 0, null)
    db.bikeQueries.updateDetails(
      name = "Road bike",
      type = "Unicycle",
      photoPath = null,
      id = bike,
    )

    assertEquals(
      ComponentKind.UNKNOWN,
      db.componentQueries.selectByKey("x").executeAsOne().toDomain().kind,
    )
    assertEquals(BikeType.Other, db.bikeQueries.selectById(bike).executeAsOne().toDomain().type)
  }

  private fun insertBike(hubSerial: Long, name: String = "Road bike"): Long {
    db.bikeQueries.insert(name, BikeType.Road.name, null, hubSerial, 0, null)
    return db.bikeQueries.lastInsertedId().executeAsOne()
  }

  private fun insertComponent(
    bike: Long,
    key: String,
    kind: ComponentKind,
    seenAt: Long? = null,
  ): Long {
    db.componentQueries.insert(
      bike,
      key,
      key.toLongOrNull(),
      kind.name,
      null,
      null,
      null,
      null,
      0,
      seenAt,
    )
    return db.componentQueries.lastInsertedId().executeAsOne()
  }

  private fun sample(
    component: Long,
    at: Long,
    mV: Long,
    status: BatteryStatus? = BatteryStatus.OK,
  ) {
    db.batterySampleQueries.insert(component, at, mV, null, status?.name, ReadMethod.Hub.name)
  }
}
