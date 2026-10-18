package com.r0adkll.cassette.bikes.impl

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.api.ComponentDetail
import com.r0adkll.cassette.bikes.api.ComponentWithReading
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.core.ComponentId
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.data.db.toDomain
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.settings.api.CassetteSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DatabaseBikeRepository(
  private val db: CassetteDatabase,
  private val dispatchers: CassetteDispatchers,
  private val settings: CassetteSettings,
) : BikeRepository {

  override fun observeBikes(): Flow<List<Bike>> =
    db.bikeQueries.selectAll().asFlow().mapToList(dispatchers.io).map { rows ->
      rows.map { it.toDomain() }
    }

  override fun observeOverview(id: BikeId?): Flow<BikeOverview?> =
    combine(observeBikes(), settings.pinnedBike) { bikes, pinned ->
        // Default: the pinned bike if it still exists, else the most recently seen.
        val wanted = id ?: pinned
        val bike =
          wanted?.let { w -> bikes.firstOrNull { it.id == w } }
            ?: bikes.takeIf { id == null }?.firstOrNull()
        bike?.let { it to bikes.size }
      }
      .distinctUntilChanged()
      .flatMapLatest { selected ->
        if (selected == null) return@flatMapLatest flowOf(null)
        val (bike, count) = selected
        combine(
          db.componentQueries.selectForBike(bike.id.value).asFlow().mapToList(dispatchers.io),
          db.batterySampleQueries.latestForBike(bike.id.value).asFlow().mapToList(dispatchers.io),
        ) { components, samples ->
          val latest = samples.associate { it.component_id to it.toDomain() }
          BikeOverview(
            bike = bike,
            bikeCount = count,
            components =
              components
                .map { ComponentWithReading(it.toDomain(), latest[it.id]) }
                .sortedWith(compareBy({ bikeOrder(it.component.kind) }, { it.component.id.value })),
          )
        }
      }

  override fun observeComponent(id: ComponentId, since: Instant): Flow<ComponentDetail?> =
    db.componentQueries
      .selectById(id.value)
      .asFlow()
      .mapToOneOrNull(dispatchers.io)
      .flatMapLatest { row ->
        if (row == null) return@flatMapLatest flowOf(null)
        combine(
          db.bikeQueries.selectById(row.bike_id).asFlow().mapToOneOrNull(dispatchers.io),
          db.batterySampleQueries
            .historyForComponent(row.id, since.toEpochMilliseconds())
            .asFlow()
            .mapToList(dispatchers.io),
          db.batterySampleQueries
            .latestForComponent(row.id)
            .asFlow()
            .mapToOneOrNull(dispatchers.io),
        ) { bike, history, latest ->
          bike?.let {
            ComponentDetail(
              component = row.toDomain(),
              bike = it.toDomain(),
              latest = latest?.toDomain(),
              history = history.map { sample -> sample.toDomain() },
            )
          }
        }
      }

  override suspend fun updateBike(id: BikeId, name: String, type: BikeType) {
    withContext(dispatchers.io) {
      val row = db.bikeQueries.selectById(id.value).executeAsOneOrNull() ?: return@withContext
      db.bikeQueries.updateDetails(
        name = name,
        type = type.name,
        photoPath = row.photo_path,
        id = id.value,
      )
    }
  }

  override suspend fun deleteBike(id: BikeId) {
    withContext(dispatchers.io) { db.bikeQueries.delete(id.value) }
    if (settings.pinnedBike.first() == id) settings.setPinnedBike(null)
  }
}

/**
 * How a bike lists its components, front of the drivetrain to back of the extras: derailleurs, then
 * the controls, suspension and seatpost, then the power meter. Anything new goes last.
 */
internal fun bikeOrder(kind: ComponentKind): Int =
  when (kind) {
    ComponentKind.REAR_DERAILLEUR -> 0
    ComponentKind.FRONT_DERAILLEUR -> 1
    ComponentKind.LEFT_SHIFTER,
    ComponentKind.LEFT_REVERB_CONTROLLER,
    ComponentKind.LEFT_FLATBAR_CONTROLLER,
    ComponentKind.LEFT_POD -> 2
    ComponentKind.RIGHT_SHIFTER,
    ComponentKind.RIGHT_REVERB_CONTROLLER,
    ComponentKind.RIGHT_FLATBAR_CONTROLLER,
    ComponentKind.RIGHT_POD -> 3
    ComponentKind.CONTROLLER,
    ComponentKind.BLIPBOX,
    ComponentKind.WIRELESS_BLIPS -> 4
    ComponentKind.FORK -> 5
    ComponentKind.REAR_SHOCK -> 6
    ComponentKind.DROPPER_POST -> 7
    ComponentKind.POWER_METER -> 8
    else -> 9
  }
