package com.r0adkll.cassette.capture.api

/**
 * What a ride test showed: did the background scan keep delivering, and did reads work? Ported from
 * the POC.
 */
data class RideSummary(
  val label: String,
  val profile: String,
  val startedAt: Long,
  val endedAt: Long?,
  val durationMillis: Long,
  val wakes: Int,
  val wakesPerHour: Double,
  /** Longest stretch with no background wake between two wakes. */
  val longestGapMillis: Long,
  /** From the last wake (or the start) until the end or now; a long tail can mean the scan died. */
  val silentTailMillis: Long,
  val components: List<ComponentActivity>,
  /** Background reads only; reads while the app is open aren't counted. */
  val gattAttempts: Int,
  val gattOk: Int,
  val gattFailed: Int,
  val gattAverageMillis: Long?,
  val budgetExceeded: Int,
  val armFailures: List<String>,
  val phoneBatteryUsed: Int?,
  /** Wakes in each of [TIMELINE_BUCKETS] equal slices of the session, oldest first. */
  val timeline: List<Int>,
) {
  /** How the test went, from the wakes and reads (thresholds in [RideVerdict]). */
  val verdict: RideVerdict
    get() =
      when {
        wakes == 0 || armFailures.isNotEmpty() -> RideVerdict.Lost
        longestGapMillis > PATCHY_GAP_MILLIS || gattFailed > 0 || budgetExceeded > 0 ->
          RideVerdict.Patchy
        else -> RideVerdict.KeptUp
      }
}

/**
 * A ride test's outcome. Kept up: never quiet for more than 2 minutes and every background read
 * worked; battery levels change far more slowly than that. Patchy: a longer quiet stretch (a stop
 * long enough for the bike to sleep does this too), or reads that failed or ran out of time. Lost:
 * no wakes at all, or the scan couldn't be armed.
 */
enum class RideVerdict {
  KeptUp,
  Patchy,
  Lost,
}

const val TIMELINE_BUCKETS = 60

private const val PATCHY_GAP_MILLIS = 2 * 60_000L

data class ComponentActivity(
  val key: String,
  val wakesHeardIn: Int,
  /** Longest gap between hearing it; includes time the bike was asleep. */
  val longestSilenceMillis: Long,
  val readsOk: Int,
)

/**
 * Summaries for every session, newest first. Events belong to the session whose window they fall
 * in.
 */
fun summarizeSessions(events: List<CaptureEvent>, nowMillis: Long): List<RideSummary> =
  events
    .withIndex()
    .filter { it.value is CaptureEvent.SessionStarted }
    .map { (index, start) ->
      val following = events.drop(index + 1)
      val nextStart =
        following.indexOfFirst { it is CaptureEvent.SessionStarted }.takeIf { it >= 0 }
      val window = if (nextStart == null) following else following.take(nextStart)
      val end = window.filterIsInstance<CaptureEvent.SessionEnded>().firstOrNull()
      val inSession = if (end == null) window else window.filter { it.at <= end.at }
      summarize(start as CaptureEvent.SessionStarted, end, inSession, nowMillis)
    }
    .sortedByDescending { it.startedAt }

private fun summarize(
  start: CaptureEvent.SessionStarted,
  end: CaptureEvent.SessionEnded?,
  events: List<CaptureEvent>,
  nowMillis: Long,
): RideSummary {
  val endAt = end?.at ?: nowMillis
  val duration = (endAt - start.at).coerceAtLeast(0)
  val deliveries = events.filterIsInstance<CaptureEvent.Delivery>()
  val gatt = events.filterIsInstance<CaptureEvent.Gatt>()
  val wakeTimes = deliveries.map { it.at }.sorted()
  val components =
    deliveries
      .flatMap { delivery -> delivery.sightings.map { it.key to delivery.at } }
      .groupBy({ it.first }, { it.second })
      .map { (key, times) ->
        val sorted = times.distinct().sorted()
        ComponentActivity(
          key = key,
          wakesHeardIn = sorted.size,
          longestSilenceMillis = sorted.zipWithNext { a, b -> b - a }.maxOrNull() ?: 0,
          readsOk = gatt.count { it.key == key && it.outcome == GattOutcome.Ok },
        )
      }
      .sortedByDescending { it.wakesHeardIn }
  val background = gatt.filter { it.background }
  return RideSummary(
    label = start.label,
    profile = start.profile,
    startedAt = start.at,
    endedAt = end?.at,
    durationMillis = duration,
    wakes = deliveries.size,
    wakesPerHour = if (duration > 0) deliveries.size * 3_600_000.0 / duration else 0.0,
    longestGapMillis = wakeTimes.zipWithNext { a, b -> b - a }.maxOrNull() ?: 0,
    silentTailMillis = endAt - (wakeTimes.lastOrNull() ?: start.at),
    components = components,
    gattAttempts = background.size,
    gattOk = background.count { it.outcome == GattOutcome.Ok },
    gattFailed = background.count { it.outcome == GattOutcome.Failed },
    gattAverageMillis =
      background.takeIf { it.isNotEmpty() }?.map { it.durationMillis }?.average()?.toLong(),
    budgetExceeded = events.count { it is CaptureEvent.BudgetExceeded },
    armFailures =
      events.filterIsInstance<CaptureEvent.Armed>().filterNot { it.ok }.map { it.result },
    phoneBatteryUsed =
      start.phoneBatteryPercent?.let { begin -> end?.phoneBatteryPercent?.let { begin - it } },
    timeline = timeline(wakeTimes, start.at, duration),
  )
}

private fun timeline(wakeTimes: List<Long>, startAt: Long, duration: Long): List<Int> {
  val buckets = IntArray(TIMELINE_BUCKETS)
  if (duration > 0)
    for (at in wakeTimes) {
      val slice = ((at - startAt) * TIMELINE_BUCKETS / duration).toInt()
      buckets[slice.coerceIn(0, TIMELINE_BUCKETS - 1)]++
    }
  return buckets.toList()
}
