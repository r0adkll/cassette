package com.r0adkll.cassette.bikes.api

import com.r0adkll.cassette.core.BatteryReading
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.Component
import com.r0adkll.cassette.core.ComponentId
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

interface BikeRepository {
  /** Most recently seen first. */
  fun observeBikes(): Flow<List<Bike>>

  /**
   * A bike with its current components and their latest readings. Null [id] follows the default
   * bike; emits null when there is no such bike.
   */
  fun observeOverview(id: BikeId?): Flow<BikeOverview?>

  /** A component with its bike, latest reading and history since [since]; null if it's gone. */
  fun observeComponent(id: ComponentId, since: Instant): Flow<ComponentDetail?>

  /** Every bike's overview, most recently seen first (the rail, the chips, Settings' garage). */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeOverviews(): Flow<List<BikeOverview>> =
    observeBikes().flatMapLatest { bikes ->
      if (bikes.isEmpty()) flowOf(emptyList())
      else combine(bikes.map { observeOverview(it.id) }) { it.filterNotNull() }
    }

  /** Renames the bike and sets its type; its photo stays. Nothing happens if it's gone. */
  suspend fun updateBike(id: BikeId, name: String, type: BikeType)

  /** Deletes the bike with its components and history. */
  suspend fun deleteBike(id: BikeId)
}

data class ComponentDetail(
  val component: Component,
  val bike: Bike,
  val latest: BatteryReading?,
  /** Oldest first. */
  val history: List<BatteryReading>,
)

data class BikeOverview(
  val bike: Bike,
  val bikeCount: Int,
  val components: List<ComponentWithReading>,
)

data class ComponentWithReading(val component: Component, val latest: BatteryReading?)

/**
 * Developer actions while capture is being ported from the POC. They live with diagnostics and stay
 * in every build (decision 0005).
 */
interface DeveloperTools {
  /** Adds a bike holding the validation bike's real readings from 2026-10-16. */
  suspend fun loadSampleBike()

  /**
   * Adds a made-up bike beside any others, for testing several: a random name and type, its
   * components and their levels (some low or critical), and a month of history. Like the sample, no
   * real hub or component can match it.
   */
  suspend fun addRandomBike()

  /**
   * Records a low reading (7.40 V, reported low) for the bike's front derailleur, or its first
   * component, to exercise alerts without hardware. Returns false if the bike has no components.
   */
  suspend fun simulateLowBattery(bike: BikeId): Boolean
}
