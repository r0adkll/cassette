package com.r0adkll.cassette.bikes.impl

import com.r0adkll.cassette.bikes.api.DeveloperTools
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.ComponentKind
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
@Inject
class DatabaseDeveloperTools(
  private val db: CassetteDatabase,
  private val dispatchers: CassetteDispatchers,
  private val clock: Clock,
) : DeveloperTools {

  override suspend fun loadSampleBike() =
    withContext(dispatchers.io) {
      val seen = (clock.now() - 2.minutes).toEpochMilliseconds()
      db.transaction {
        db.bikeQueries.selectByHubSerial(SAMPLE_HUB).executeAsOneOrNull()?.let {
          db.bikeQueries.delete(it.id)
        }
        db.bikeQueries.insert("Sample bike", BikeType.Road.name, null, SAMPLE_HUB, seen, seen)
        insertComponents(SAMPLE_COMPONENTS, SAMPLE_KEY_PREFIX, seen)
      }
    }

  override suspend fun addRandomBike() =
    withContext(dispatchers.io) {
      db.transaction {
        val bikes = db.bikeQueries.selectAll().executeAsList()
        val taken = bikes.map { it.name }.toSet()
        val (name, type) =
          RANDOM_BIKES.filter { it.first !in taken }.randomOrNull()
            ?: (generateSequence(bikes.size + 1) { it + 1 }
              .map { "Test bike $it" }
              .first {
                it !in taken
              } to BikeType.Other)
        // Below the sample's serial and every earlier random bike's, so none collide.
        val hub =
          minOf(SAMPLE_HUB, bikes.mapNotNull { it.hub_serial }.minOrNull() ?: SAMPLE_HUB) - 1
        val seen = (clock.now() - Random.nextLong(10, 5 * 24 * 60).minutes).toEpochMilliseconds()
        db.bikeQueries.insert(name, type.name, null, hub, seen, seen)
        insertComponents(randomComponents(type), "$SAMPLE_KEY_PREFIX$hub:", seen)
      }
    }

  /** The new bike's components, each with a month of daily reads up to [seen]. */
  private fun insertComponents(samples: List<Sample>, keyPrefix: String, seen: Long) {
    val bike = db.bikeQueries.lastInsertedId().executeAsOne()
    samples.forEach { c ->
      // Sample keys never match a real component, so capture can't attach to them, and loading
      // the sample never touches a bike the user added.
      // The serial is only shown, never matched, so the sample can show a real-looking one.
      db.componentQueries.insert(
        bike,
        keyPrefix + c.key,
        c.key.toLongOrNull(),
        c.kind.name,
        c.modelId,
        c.deviceType,
        c.firmware,
        null,
        seen,
        seen,
      )
      val component = db.componentQueries.lastInsertedId().executeAsOne()
      // A month of daily reads before this one, so the charts and estimates have a history.
      for (days in HISTORY_DAYS downTo 0) {
        db.batterySampleQueries.insert(
          component,
          seen - days.days.inWholeMilliseconds,
          c.millivolts?.let { c.millivoltsAt(days) },
          c.percent?.let { c.percentAt(days) },
          c.status?.name,
          c.method.name,
        )
      }
    }
  }

  /**
   * A rear derailleur and the rest of a [type] of bike, some parts only sometimes, each mostly good
   * but now and then low or critical, so a few bikes show every status.
   */
  private fun randomComponents(type: BikeType): List<Sample> = buildList {
    add(randomPack(ComponentKind.REAR_DERAILLEUR, 1066, 0x81, "2.55.8", reportsPercent = true))
    if (type == BikeType.Mtb) {
      add(randomCoinCell(ComponentKind.RIGHT_POD, null, null))
      if (Random.nextInt(10) < 4) add(randomCoinCell(ComponentKind.LEFT_POD, null, null))
    } else {
      val frontChance = if (type == BikeType.Road) 6 else 3
      if (Random.nextInt(10) < frontChance)
        add(randomPack(ComponentKind.FRONT_DERAILLEUR, 1002, 0x80, "2.7.3", reportsPercent = false))
      add(randomCoinCell(ComponentKind.LEFT_SHIFTER, 1005, 0x00))
      add(randomCoinCell(ComponentKind.RIGHT_SHIFTER, 1004, 0x01))
    }
    if (Random.nextInt(10) < 4)
      add(
        Sample(
          "Quarq ${randomSerial()}",
          ComponentKind.POWER_METER,
          7,
          null,
          null,
          null,
          randomPercent(randomStatus()),
          null,
          ReadMethod.Gatt,
        )
      )
  }

  /** A derailleur's rechargeable pack; only the rear derailleur reports a percentage. */
  private fun randomPack(
    kind: ComponentKind,
    modelId: Long,
    deviceType: Long,
    firmware: String,
    reportsPercent: Boolean,
  ): Sample {
    val status = randomStatus()
    val percent = randomPercent(status)
    return Sample(
      randomSerial(),
      kind,
      modelId,
      deviceType,
      firmware,
      7_400L + percent * 9,
      percent.takeIf { reportsPercent },
      status,
      ReadMethod.Hub,
    )
  }

  private fun randomCoinCell(kind: ComponentKind, modelId: Long?, deviceType: Long?): Sample {
    val status = randomStatus()
    val millivolts =
      when (status) {
        BatteryStatus.CRITICAL -> Random.nextLong(2_350, 2_500)
        BatteryStatus.LOW -> Random.nextLong(2_550, 2_700)
        else -> Random.nextLong(2_800, 3_000)
      }
    return Sample(
      randomSerial(),
      kind,
      modelId,
      deviceType,
      "2.7.3",
      millivolts,
      null,
      status,
      ReadMethod.Hub,
    )
  }

  private fun randomStatus(): BatteryStatus =
    when (Random.nextInt(10)) {
      0 -> BatteryStatus.CRITICAL
      1,
      2 -> BatteryStatus.LOW
      else -> BatteryStatus.OK
    }

  private fun randomPercent(status: BatteryStatus): Long =
    when (status) {
      BatteryStatus.CRITICAL -> Random.nextLong(2, 10)
      BatteryStatus.LOW -> Random.nextLong(10, 26)
      else -> Random.nextLong(40, 101)
    }

  private fun randomSerial(): String = Random.nextLong(1_100_000_000, 1_500_000_000).toString()

  override suspend fun simulateLowBattery(bike: BikeId): Boolean =
    withContext(dispatchers.io) {
      val components = db.componentQueries.selectForBike(bike.value).executeAsList()
      val target =
        components.firstOrNull { it.kind == ComponentKind.FRONT_DERAILLEUR.name }
          ?: components.firstOrNull()
          ?: return@withContext false
      db.batterySampleQueries.insert(
        target.id,
        clock.now().toEpochMilliseconds(),
        7_400,
        null,
        BatteryStatus.LOW.name,
        ReadMethod.Hub.name,
      )
      true
    }

  private class Sample(
    val key: String,
    val kind: ComponentKind,
    val modelId: Long?,
    val deviceType: Long?,
    val firmware: String?,
    val millivolts: Long?,
    val percent: Long?,
    val status: BatteryStatus?,
    val method: ReadMethod,
  ) {
    private val charged =
      kind == ComponentKind.REAR_DERAILLEUR || kind == ComponentKind.FRONT_DERAILLEUR

    /**
     * The voltage [days] before today: the derailleur packs were charged [CHARGED_DAYS_AGO] days
     * ago and have fallen since, from a drained pack before; coin cells drift down a millivolt a
     * day.
     */
    fun millivoltsAt(days: Int): Long =
      when {
        !charged -> millivolts!! + days
        days > CHARGED_DAYS_AGO -> 7_600L + (days - CHARGED_DAYS_AGO - 1) * 20
        else -> millivolts!! + days * (8_350 - millivolts) / CHARGED_DAYS_AGO
      }

    /**
     * The percentage [days] before today, as [millivoltsAt]; the power meter loses one in three
     * days.
     */
    fun percentAt(days: Int): Long =
      when {
        !charged -> (percent!! + days / 3).coerceAtMost(100)
        days > CHARGED_DAYS_AGO -> 37L + (days - CHARGED_DAYS_AGO - 1) * 25 / 17
        else -> percent!! + days * (98 - percent) / CHARGED_DAYS_AGO
      }
  }

  private companion object {
    /** Not a real hub serial (they're unsigned), so it can't collide with an added bike. */
    const val SAMPLE_HUB = -1L
    const val SAMPLE_KEY_PREFIX = "sample:"
    const val HISTORY_DAYS = 30
    const val CHARGED_DAYS_AGO = 12

    /** Names for random bikes, with their types; once they're all taken, "Test bike 4" and on. */
    val RANDOM_BIKES =
      listOf(
        "Race bike" to BikeType.Road,
        "Winter bike" to BikeType.Road,
        "TT bike" to BikeType.Road,
        "Gravel bike" to BikeType.Gravel,
        "Cyclocross bike" to BikeType.Gravel,
        "Bikepacking rig" to BikeType.Gravel,
        "Trail bike" to BikeType.Mtb,
        "Enduro bike" to BikeType.Mtb,
        "Hardtail" to BikeType.Mtb,
        "Commuter" to BikeType.Other,
      )

    /** The validation bike (Red AXS) as read on 2026-10-16. */
    val SAMPLE_COMPONENTS =
      listOf(
        Sample(
          "1405188718",
          ComponentKind.REAR_DERAILLEUR,
          1066,
          0x81,
          "2.55.8",
          7_870,
          78,
          BatteryStatus.OK,
          ReadMethod.Hub,
        ),
        Sample(
          "1315137227",
          ComponentKind.FRONT_DERAILLEUR,
          1002,
          0x80,
          "2.7.3",
          8_090,
          null,
          BatteryStatus.OK,
          ReadMethod.Hub,
        ),
        Sample(
          "1132146225",
          ComponentKind.LEFT_SHIFTER,
          1005,
          0x00,
          "2.7.3",
          2_910,
          null,
          BatteryStatus.OK,
          ReadMethod.Hub,
        ),
        Sample(
          "1132134130",
          ComponentKind.RIGHT_SHIFTER,
          1004,
          0x01,
          "2.7.3",
          2_890,
          null,
          BatteryStatus.OK,
          ReadMethod.Hub,
        ),
        Sample(
          "Quarq AGN12387",
          ComponentKind.POWER_METER,
          7,
          null,
          null,
          null,
          85,
          null,
          ReadMethod.Gatt,
        ),
      )
  }
}
