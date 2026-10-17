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
)

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
  )
}
