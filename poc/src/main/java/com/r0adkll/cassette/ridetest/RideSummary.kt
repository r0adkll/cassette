package com.r0adkll.cassette.ridetest

import com.r0adkll.cassette.data.CaptureSource
import com.r0adkll.cassette.scan.ScanProfile

/** What a ride test showed: did the background scan keep delivering, and did battery reads work? */
data class RideSummary(
  val label: String,
  val profile: ScanProfile,
  val startedAt: Long,
  val endedAt: Long?,
  val durationMillis: Long,
  val wakes: Int,
  val wakesPerHour: Double,
  /** Longest stretch with no background wake between two wakes. */
  val longestGapMillis: Long,
  val longestGapStartedAt: Long?,
  /**
   * Time since the last wake (or session start) until the session ended or now. A long tail can
   * mean the scan died.
   */
  val silentTailMillis: Long,
  val components: List<ComponentActivity>,
  /** Background GATT reads only; live-scan reads while the app is open aren't counted. */
  val gattAttempts: Int,
  val gattOk: Int,
  val gattFailed: Int,
  val gattAverageMillis: Long?,
  val budgetExceeded: Int,
  val armFailures: List<Int>,
  val phoneBatteryUsed: Int?,
)

data class ComponentActivity(
  val key: String,
  val wakesHeardIn: Int,
  val firstHeardAt: Long,
  val lastHeardAt: Long,
  /**
   * Longest gap between hearing this component. Includes time the bike was asleep, so read it
   * alongside the ride.
   */
  val longestSilenceMillis: Long,
  /**
   * Successful reads from this component: the hub table for the rear derailleur, Battery Level for
   * the power meter.
   */
  val gattReadsOk: Int,
)

/** Summaries for every session, newest first. Events are assigned to sessions by time window. */
fun summarizeSessions(events: List<RideEvent>, nowMillis: Long): List<RideSummary> {
  val starts = events.withIndex().filter { it.value is RideEvent.SessionStarted }
  return starts
    .map { (index, start) ->
      val following = events.drop(index + 1)
      val nextStart = following.indexOfFirst { it is RideEvent.SessionStarted }.takeIf { it >= 0 }
      val window = if (nextStart == null) following else following.take(nextStart)
      val end = window.filterIsInstance<RideEvent.SessionEnded>().firstOrNull()
      val inSession = if (end == null) window else window.filter { it.at <= end.at }
      summarize(start as RideEvent.SessionStarted, end, inSession, nowMillis)
    }
    .sortedByDescending { it.startedAt }
}

internal fun summarize(
  start: RideEvent.SessionStarted,
  end: RideEvent.SessionEnded?,
  events: List<RideEvent>,
  nowMillis: Long,
): RideSummary {
  val endAt = end?.at ?: nowMillis
  val duration = (endAt - start.at).coerceAtLeast(0)
  val deliveries = events.filterIsInstance<RideEvent.Delivery>()
  val gatt = events.filterIsInstance<RideEvent.Gatt>()

  val wakeTimes = deliveries.map { it.at }.sorted()
  val gaps = wakeTimes.zipWithNext { a, b -> a to b - a }
  val longestGap = gaps.maxByOrNull { it.second }

  val sightingsByKey =
    deliveries
      .flatMap { delivery -> delivery.sightings.map { it.key to delivery.at } }
      .groupBy({ it.first }, { it.second })
  val components =
    sightingsByKey
      .map { (key, times) ->
        val sorted = times.sorted()
        ComponentActivity(
          key = key,
          wakesHeardIn = sorted.distinct().size,
          firstHeardAt = sorted.first(),
          lastHeardAt = sorted.last(),
          longestSilenceMillis = sorted.zipWithNext { a, b -> b - a }.maxOrNull() ?: 0,
          gattReadsOk = gatt.count { it.key == key && it.outcome == GattOutcome.OK },
        )
      }
      .sortedByDescending { it.wakesHeardIn }

  val backgroundGatt = gatt.filter { it.source == CaptureSource.BACKGROUND }
  return RideSummary(
    label = start.label,
    profile = start.profile,
    startedAt = start.at,
    endedAt = end?.at,
    durationMillis = duration,
    wakes = deliveries.size,
    wakesPerHour = if (duration > 0) deliveries.size * 3_600_000.0 / duration else 0.0,
    longestGapMillis = longestGap?.second ?: 0,
    longestGapStartedAt = longestGap?.first,
    silentTailMillis = endAt - (wakeTimes.lastOrNull() ?: start.at),
    components = components,
    gattAttempts = backgroundGatt.size,
    gattOk = backgroundGatt.count { it.outcome == GattOutcome.OK },
    gattFailed = backgroundGatt.count { it.outcome == GattOutcome.FAILED },
    gattAverageMillis =
      backgroundGatt.takeIf { it.isNotEmpty() }?.map { it.durationMillis }?.average()?.toLong(),
    budgetExceeded = events.count { it is RideEvent.BudgetExceeded },
    armFailures = events.filterIsInstance<RideEvent.Armed>().map { it.result }.filter { it != 0 },
    phoneBatteryUsed =
      start.phoneBatteryPercent?.let { begin ->
        end?.phoneBatteryPercent?.let { begin - it }
      },
  )
}
