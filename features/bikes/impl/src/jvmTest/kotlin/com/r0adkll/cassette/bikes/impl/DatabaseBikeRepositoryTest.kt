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
      assertEquals("Road bike", overview.bike.name)
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
  fun componentsAreListedInBikeOrderNotInTheOrderTheyWereFound() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val dispatchers = CassetteDispatchers(dispatcher, dispatcher, dispatcher)
    // As add bike saved the validation bike: power meter first, rear derailleur last.
    db.bikeQueries.insert("Kish Ti", "Road", null, 1405188718, 0, 0)
    val bike = db.bikeQueries.lastInsertedId().executeAsOne()
    listOf("POWER_METER", "FRONT_DERAILLEUR", "RIGHT_SHIFTER", "LEFT_SHIFTER", "REAR_DERAILLEUR")
      .forEachIndexed { i, kind ->
        db.componentQueries.insert(bike, "k$i", null, kind, null, null, null, null, 0, 0)
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
  fun componentDetailWithHistory() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val dispatchers = CassetteDispatchers(dispatcher, dispatcher, dispatcher)
    DatabaseDeveloperTools(db, dispatchers, clock).loadSampleBike()
    val rd = db.componentQueries.selectByKey("1405188718").executeAsOne()
    db.batterySampleQueries.insert(rd.id, 1_000, 7_950, 92, "OK", "Advert")

    DatabaseBikeRepository(db, dispatchers, InMemorySettings())
      .observeComponent(ComponentId(rd.id), Instant.fromEpochMilliseconds(0))
      .test {
        val detail = awaitItem()!!
        assertEquals("Road bike", detail.bike.name)
        assertEquals(78, detail.latest?.percent)
        assertEquals(listOf(92, 78), detail.history.map { it.percent })
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
      assertEquals("Road bike", awaitItem()?.bike?.name)
      cancelAndIgnoreRemainingEvents()
    }
    assertNull(settings.pinnedBike.value)
  }
}
