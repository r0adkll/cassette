package com.r0adkll.cassette.bikes.impl

import app.cash.turbine.test
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.data.db.createInMemoryDriver
import com.r0adkll.cassette.protocol.ComponentKind
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
    val repository = DatabaseBikeRepository(db, dispatchers)

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

    DatabaseBikeRepository(db, dispatchers).observeBikes().test {
      assertEquals(1, awaitItem().size)
      cancelAndIgnoreRemainingEvents()
    }
    DatabaseBikeRepository(db, dispatchers).observeOverview(BikeId(999)).test {
      assertNull(awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }
}
