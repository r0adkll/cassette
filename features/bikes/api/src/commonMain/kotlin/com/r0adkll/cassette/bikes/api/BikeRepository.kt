package com.r0adkll.cassette.bikes.api

import com.r0adkll.cassette.core.BatteryReading
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.Component
import com.r0adkll.cassette.core.ComponentId
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

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
}
