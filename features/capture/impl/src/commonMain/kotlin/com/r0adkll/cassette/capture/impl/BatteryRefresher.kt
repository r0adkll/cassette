package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.GattKind
import com.r0adkll.cassette.capture.api.GattOutcome
import com.r0adkll.cassette.data.ble.BATTERY_LEVEL_CHARACTERISTIC
import com.r0adkll.cassette.data.ble.GattClient
import com.r0adkll.cassette.data.ble.GattResult
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.protocol.FrameType
import com.r0adkll.cassette.protocol.SystemBatteryTable
import com.r0adkll.cassette.protocol.toHex
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CaptureMode(val minAge: Duration) {
  /** The app is open: read again after a minute. */
  Live(60.seconds),
  /** Background wakes: a read costs radio time, so every 10 minutes. */
  Background(10.minutes),
}

data class RefreshOutcome(val hubRead: Boolean, val powerMeterRead: Boolean)

/**
 * Fills in battery that adverts don't carry, over short read-only GATT sessions:
 * - the hub (rear derailleur) is asked for its battery table, which covers the shifters and front
 *   derailleur, so those are never connected to;
 * - the power meter is asked for the standard Battery Level.
 */
@SingleIn(AppScope::class)
@Inject
class BatteryRefresher(
  private val recorder: CaptureRecorder,
  private val gatt: GattClient,
  private val db: CassetteDatabase,
  private val log: CaptureLog,
  private val clock: Clock,
) {
  private val lastAttempt = mutableMapOf<Long, kotlin.time.Instant>()
  private val unsupported = mutableSetOf<Long>()
  private val lock = Mutex()

  suspend fun refresh(heard: List<Heard>, mode: CaptureMode): RefreshOutcome {
    var hubRead = false
    var powerMeterRead = false
    for (item in heard.distinctBy { it.component.id }) {
      val component = item.component
      val address = component.address ?: continue
      val isHub =
        item.reading.frame == FrameType.STATUS &&
          component.serial != null &&
          component.serial == item.bike.hubSerial
      val isPowerMeter = item.reading.frame == FrameType.POWER_METER
      if (!isHub && !isPowerMeter) continue

      val now = clock.now()
      val lastRead =
        if (isHub) item.bike.hubReadAt
        else
          db.batterySampleQueries
            .latestForComponent(component.id.value)
            .executeAsOneOrNull()
            ?.at
            ?.let {
              kotlin.time.Instant.fromEpochMilliseconds(it)
            }
      if (lastRead != null && now - lastRead < mode.minAge) continue
      if (!claim(component.id.value, now)) continue

      if (isHub) {
        // Firmware rarely changes and background wakes have a tight budget.
        val wanted =
          listOfNotNull(
            SystemBatteryTable.CHARACTERISTIC,
            SystemBatteryTable.VERSIONS_CHARACTERISTIC.takeIf {
              mode == CaptureMode.Live
            },
          )
        val started = clock.now()
        val values = (gatt.read(address, wanted) as? GattResult.Values)?.values
        val raw = values?.get(SystemBatteryTable.CHARACTERISTIC)
        val table = raw?.let(SystemBatteryTable::parse)
        logRead(
          component.key,
          GattKind.HubTable,
          mode,
          started,
          values,
          table != null,
          raw?.toHex(),
        )
        if (values == null) continue
        if (table == null) {
          // Answered, but no table: not a hub; don't keep connecting.
          lock.withLock { unsupported += component.id.value }
          continue
        }
        val versions =
          values[SystemBatteryTable.VERSIONS_CHARACTERISTIC]?.let(SystemBatteryTable::parseVersions)
        recorder.applyHubTable(item.bike, table, versions, clock.now())
        hubRead = true
      } else {
        val started = clock.now()
        val values =
          (gatt.read(address, listOf(BATTERY_LEVEL_CHARACTERISTIC)) as? GattResult.Values)?.values
        val level =
          values?.get(BATTERY_LEVEL_CHARACTERISTIC)?.firstOrNull()?.toInt()?.and(0xFF)?.takeIf {
            it <= 100
          }
        logRead(component.key, GattKind.BatteryLevel, mode, started, values, level != null, null)
        if (values == null) continue
        if (level == null) {
          lock.withLock { unsupported += component.id.value }
          continue
        }
        recorder.applyBatteryLevel(component, level, clock.now())
        powerMeterRead = true
      }
    }
    return RefreshOutcome(hubRead, powerMeterRead)
  }

  private suspend fun logRead(
    key: String,
    kind: GattKind,
    mode: CaptureMode,
    started: Instant,
    values: Map<String, ByteArray>?,
    parsed: Boolean,
    hex: String?,
  ) {
    val outcome =
      when {
        values == null -> GattOutcome.Failed
        !parsed -> GattOutcome.Unsupported
        else -> GattOutcome.Ok
      }
    log.append(
      CaptureEvent.Gatt(
        at = clock.now().toEpochMilliseconds(),
        key = key,
        kind = kind,
        background = mode == CaptureMode.Background,
        durationMillis = (clock.now() - started).inWholeMilliseconds,
        outcome = outcome,
        hex = hex,
      )
    )
  }

  /** Guards concurrent deliveries and backs off after a failed read. */
  private suspend fun claim(componentId: Long, now: kotlin.time.Instant): Boolean = lock.withLock {
    if (componentId in unsupported) return@withLock false
    val previous = lastAttempt[componentId]
    if (previous != null && now - previous < RETRY_AFTER) return@withLock false
    lastAttempt[componentId] = now
    true
  }

  private companion object {
    /**
     * Below the ~30 s between rear derailleur wakes, so a failed read is retried on the next one.
     */
    val RETRY_AFTER = 20.seconds
  }
}
