package com.r0adkll.cassette.surfaces.widgetkit

import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.summary.BikeSummary
import com.r0adkll.cassette.bikes.summary.toSummary
import com.r0adkll.cassette.bikes.summary.weakest
import com.r0adkll.cassette.common.compose.components.word
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.STALE_AFTER
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the widgets show (decision 0017): every bike, most recently seen first, worded by
 * `BikeSummary` as it reads now and as it will once the bike goes stale. The Swift extension can't
 * run the summary code, so it only words the clock ("Seen 2 min ago", "Not seen in 16 days.").
 * Times are epoch milliseconds; tones are [com.r0adkll.cassette.common.compose.theme.StatusTone]
 * names.
 */
@Serializable
data class WidgetSnapshot(
  val version: Int = VERSION,
  val writtenAt: Long,
  /** The default bike (pinned, or the most recently seen), for widgets with no bike picked. */
  val defaultBikeId: Long?,
  val bikes: List<WidgetBike>,
) {
  companion object {
    const val VERSION = 1
  }
}

@Serializable
data class WidgetBike(
  val id: Long,
  val name: String,
  val lastSeenAt: Long?,
  val current: WidgetStatus,
  /** When [stale] takes over from [current]; null when the bike is already stale or never seen. */
  val staleAt: Long? = null,
  val stale: WidgetStatus? = null,
)

@Serializable
data class WidgetStatus(
  val tone: String,
  /** The status word: "GOOD", "LOW", "CHARGE NOW", "OLD". */
  val word: String,
  val headline: String,
  val summary: String,
  /** The component with the lowest level among those with a reading. */
  val weakestId: Long?,
  val components: List<WidgetComponent>,
)

@Serializable
data class WidgetComponent(
  val id: Long,
  val code: String,
  val name: String,
  val value: String,
  val level: Float,
  val tone: String,
  val word: String,
)

object WidgetCodec {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  fun encode(snapshot: WidgetSnapshot): String =
    json.encodeToString(WidgetSnapshot.serializer(), snapshot)

  fun decode(text: String): WidgetSnapshot? = runCatching {
    json.decodeFromString(WidgetSnapshot.serializer(), text)
  }
    .getOrNull()
}

fun widgetSnapshot(
  overviews: List<BikeOverview>,
  defaultBikeId: BikeId?,
  now: Instant,
): WidgetSnapshot =
  WidgetSnapshot(
    writtenAt = now.toEpochMilliseconds(),
    defaultBikeId = defaultBikeId?.value,
    bikes = overviews.map { it.toWidget(now) },
  )

private fun BikeOverview.toWidget(now: Instant): WidgetBike {
  // isStale is "more than 14 days", so the first stale moment is a millisecond past it.
  val staleAt = bike.lastSeenAt?.plus(STALE_AFTER + 1.milliseconds)?.takeIf { it > now }
  return WidgetBike(
    id = bike.id.value,
    name = bike.name,
    lastSeenAt = bike.lastSeenAt?.toEpochMilliseconds(),
    current = toSummary(now).toWidget(),
    staleAt = staleAt?.toEpochMilliseconds(),
    stale = staleAt?.let { toSummary(it).toWidget() },
  )
}

private fun BikeSummary.toWidget(): WidgetStatus =
  WidgetStatus(
    tone = status.tone.name,
    word = status.tone.word,
    headline = status.headline,
    summary = status.summary,
    weakestId = components.weakest()?.id,
    components =
      components.map {
        WidgetComponent(
          id = it.id,
          code = it.code,
          name = it.name,
          value = it.value,
          level = it.level,
          tone = it.tone.name,
          word = it.tone.word,
        )
      },
  )
