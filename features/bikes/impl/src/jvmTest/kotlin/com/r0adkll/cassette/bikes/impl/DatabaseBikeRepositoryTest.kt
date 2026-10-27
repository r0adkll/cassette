package com.r0adkll.cassette.bikes.impl

import app.cash.turbine.test
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.core.ComponentId
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.data.db.createInMemoryDriver
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.settings.test.InMemorySettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class DatabaseBikeRepositoryTest {
  private val driver = createInMemoryDriver()
  private val db = CassetteDatabase(driver)
  private val clock =
    object : Clock {
      override fun now() = Instant.fromEpochMilliseconds(1_792_200_000_000)
    }

  @AfterTest fun close() = driver.close()

  @Test
  fun overviewFollowsTheDefaultBike() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val dispatchers = CassetteDispatchers(dispatcher, dispatcher, dispatcher)
    val repository = DatabaseBikeRepository(db, dispatchers, InMemorySettings())

    repository.observeOverview(null).test {
      assertNull(awaitItem())

      DatabaseDeveloperTools(db, dispatchers, clock).loadSampleBike()

      val overview = awaitItem()!!
      assertEquals("Sample bike", overview.bike.name)
      assertEquals(1, overview.bikeCount)
      assertEquals(
        listOf(
          ComponentKind.REAR_DERAILLEUR,
          ComponentKind.FRONT_DERAILLEUR,
          ComponentKind.LEFT_SHIFTER,
          ComponentKind.RIGHT_SHIFTER,
          ComponentKind.POWER_METER,
        ),
        overview.components.map { it.component.kind },
      )
      assertEquals(78, overview.components.first().latest?.percent)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun loadingTheSampleTwiceReplacesIt() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val dispatchers = CassetteDispatchers(dispatcher, dispatcher, dispatcher)
    val tools = DatabaseDeveloperTools(db, dispatchers, clock)
    tools.loadSampleBike()
    tools.loadSampleBike()

    DatabaseBikeRepository(db, dispatchers, InMemorySettings()).observeBikes().test {
      assertEquals(1, awaitItem().size)
      cancelAndIgnoreRemainingEvents()
    }
    DatabaseBikeRepository(db, dispatchers, InMemorySettings()).observeOverview(BikeId(999)).test {
      assertNull(awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun randomBikesAddUpBesideTheSample() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val dispatchers = CassetteDispatchers(dispatcher, dispatcher, dispatcher)
    val tools = DatabaseDeveloperTools(db, dispatchers, clock)
    tools.loadSampleBike()
    tools.addRandomBike()
    tools.addRandomBike()
    // Reloading the sample replaces only the sample.
    tools.loadSampleBike()

    val bikes = db.bikeQueries.selectAll().executeAsList()
    assertEquals(3, bikes.size)
    assertEquals(3, bikes.map { it.name }.toSet().size)
    assertEquals(3, bikes.map { it.hub_serial }.toSet().size)
    // No real hub has a negative serial, so capture can't match one.
    assertTrue(bikes.all { it.hub_serial!! < 0 })
    bikes
      .filter { it.name != "Sample bike" }
      .forEach { bike ->
        val components = db.componentQueries.selectForBike(bike.id).executeAsList()
        assertTrue(components.any { it.kind == ComponentKind.REAR_DERAILLEUR.name })
        assertTrue(components.all { it.key.startsWith("sample:") })
      }
  }

  @Test
  fun componentsAreListedInBikeOrderNotInTheOrderTheyWereFound() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val dispatchers = CassetteDispatchers(dispatcher, dispatcher, dispatcher)
    // As add bike saved the validation bike: power meter first, rear derailleur last.
    db.bikeQueries.insert("Kish Ti", "Road", null, 1405188718, 0, 0)
    val bike = db.bikeQueries.lastInsertedId().executeAsOne()
    listOf("POWER_METER", "FRONT_DERAILLEUR", "RIGHT_SHIFTER", "LEFT_SHIFTER", "REAR_DERAILLEUR")
      .forEachIndexed { i, kind ->
        db.componentQueries.insert(bike, "k$i", null, kind, null, null, null, null, 0, 0, null)
      }

    DatabaseBikeRepository(db, dispatchers, InMemorySettings()).observeOverview(null).test {
      assertEquals(
        listOf(
          ComponentKind.REAR_DERAILLEUR,
          ComponentKind.FRONT_DERAILLEUR,
          ComponentKind.LEFT_SHIFTER,
          ComponentKind.RIGHT_SHIFTER,
          ComponentKind.POWER_METER,
        ),
        awaitItem()!!.components.map { it.component.kind },
      )
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun theSampleNeverTouchesAnAddedBike() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val dispatchers = CassetteDispatchers(dispatcher, dispatcher, dispatcher)
    // The user's own bike: the validation bike's real hub and rear derailleur.
    db.bikeQueries.insert("Road bike", "Road", null, 1405188718, 0, 0)
    val road = db.bikeQueries.lastInsertedId().executeAsOne()
    db.componentQueries.insert(
      road,
      "1405188718",
      1405188718,
      "REAR_DERAILLEUR",
      null,
      null,
      null,
      null,
      0,
      0,
      null,
    )

    DatabaseDeveloperTools(db, dispatchers, clock).loadSampleBike()

    val bikes = db.bikeQueries.selectAll().executeAsList().map { it.name }.sorted()
    assertEquals(listOf("Road bike", "Sample bike"), bikes)
    assertEquals(road, db.componentQueries.selectByKey("1405188718").executeAsOne().bike_id)
  }

  @Test
  fun removingASystemKeepsTheBikesOtherSystem() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val dispatchers = CassetteDispatchers(dispatcher, dispatcher, dispatcher)
    val repository = DatabaseBikeRepository(db, dispatchers, InMemorySettings())
    // The tester's MTB: the dropper's system (it was added with) and the rear shock's.
    db.bikeQueries.insert("Mountain bike", "Mtb", null, DROPPER, 0, null)
    val bike = db.bikeQueries.lastInsertedId().executeAsOne()
    db.hubQueries.insert(DROPPER, bike, 0)
    db.hubQueries.insert(SHOCK, bike, 1)
    listOf(DROPPER to DROPPER, 1151498210L to DROPPER, SHOCK to SHOCK, 1224168767L to SHOCK)
      .forEach { (serial, hub) ->
        db.componentQueries.insert(
          bike,
          "$serial",
          serial,
          "FORK",
          null,
          null,
          null,
          null,
          0,
          0,
          hub,
        )
      }
    db.componentQueries.insert(
      bike,
      "Quarq AGD60899",
      null,
      "POWER_METER",
      7,
      null,
      null,
      null,
      0,
      0,
      null,
    )

    repository.removeSystem(BikeId(bike), DROPPER)

    assertEquals(
      listOf("1013571428", "1224168767", "Quarq AGD60899"),
      db.componentQueries.selectForBike(bike).executeAsList().map { it.key },
    )
    assertEquals(listOf(SHOCK), db.hubQueries.selectForBike(bike).executeAsList().map { it.serial })
    // Known by its other system now, so the dropper is free to start a bike of its own.
    assertEquals(SHOCK, db.bikeQueries.selectById(bike).executeAsOne().hub_serial)
  }

  @Test
  fun componentDetailWithHistory() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val dispatchers = CassetteDispatchers(dispatcher, dispatcher, dispatcher)
    DatabaseDeveloperTools(db, dispatchers, clock).loadSampleBike()
    val rd = db.componentQueries.selectByKey("sample:1405188718").executeAsOne()
    db.batterySampleQueries.insert(rd.id, 1_000, 7_950, 92, "OK", "Advert")

    DatabaseBikeRepository(db, dispatchers, InMemorySettings())
      .observeComponent(ComponentId(rd.id), Instant.fromEpochMilliseconds(0))
      .test {
        val detail = awaitItem()!!
        assertEquals("Sample bike", detail.bike.name)
        assertEquals(78, detail.latest?.percent)
        // The read at 1 s, then the sample's month: charged 12 days ago, at 78% today.
        val history = detail.history.map { it.percent }
        assertEquals(listOf(92, 62), history.take(2))
        assertEquals(listOf(37, 98), history.subList(18, 20))
        assertEquals(78, history.last())
        assertEquals(32, history.size)
        cancelAndIgnoreRemainingEvents()
      }
  }

  @Test
  fun aPinnedBikeIsTheDefaultUntilItIsDeleted() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val dispatchers = CassetteDispatchers(dispatcher, dispatcher, dispatcher)
    val settings = InMemorySettings()
    val repository = DatabaseBikeRepository(db, dispatchers, settings)
    DatabaseDeveloperTools(db, dispatchers, clock).loadSampleBike()
    db.bikeQueries.insert("Gravel bike", "Gravel", null, 2, 0, 0)
    val gravel = BikeId(db.bikeQueries.lastInsertedId().executeAsOne())
    settings.setPinnedBike(gravel)

    repository.observeOverview(null).test {
      assertEquals("Gravel bike", awaitItem()?.bike?.name)
      repository.deleteBike(gravel)
      assertEquals("Sample bike", awaitItem()?.bike?.name)
      cancelAndIgnoreRemainingEvents()
    }
    assertNull(settings.pinnedBike.value)
  }

  private companion object {
    const val DROPPER = 1121114092L
    const val SHOCK = 1013571428L
  }
}
