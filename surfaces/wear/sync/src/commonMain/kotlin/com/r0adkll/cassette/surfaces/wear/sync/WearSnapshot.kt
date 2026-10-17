package com.r0adkll.cassette.surfaces.wear.sync

import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.ComponentWithReading
import com.r0adkll.cassette.core.BatteryReading
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.Component
import com.r0adkll.cassette.core.ComponentId
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.ComponentKind
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the watch shows: every bike, most recently seen first, with its current components and
 * their latest readings. Data, not wording: the watch words it against its own clock. Enums travel
 * as names and unknown ones read as unknown, so a phone and watch a release apart still agree.
 */
@Serializable
data class WearSnapshot(
  val version: Int = VERSION,
  /** The default bike (pinned, or the most recently seen); it feeds the tile and complications. */
  val defaultBikeId: Long?,
  val bikes: List<WearBike>,
) {
  companion object {
    const val VERSION = 1
  }
}

@Serializable
data class WearBike(
  val id: Long,
  val name: String,
  val type: String,
  val lastSeenAt: Long?,
  val hubReadAt: Long?,
  val components: List<WearComponent>,
)

@Serializable
data class WearComponent(
  val id: Long,
  val key: String,
  val kind: String,
  val firmware: String?,
  val latest: WearReading?,
)

@Serializable
data class WearReading(
  val at: Long,
  val millivolts: Int?,
  val percent: Int?,
  val reportedStatus: String?,
  val method: String,
)

/** The phone's refresh, so the watch can show it running. Times are epoch milliseconds. */
@Serializable
data class WearRefresh(
  val state: State,
  /** When the scan ends, while [State.Scanning]. */
  val until: Long? = null,
  /** When the state was set; also makes each publish a change the Data Layer delivers. */
  val at: Long,
) {
  enum class State {
    Idle,
    Scanning,
    /** The phone's Bluetooth is off or Cassette isn't allowed to scan. */
    NotReady,
  }
}

object WearCodec {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  fun encode(snapshot: WearSnapshot): ByteArray =
    json.encodeToString(WearSnapshot.serializer(), snapshot).encodeToByteArray()

  /** Null if [bytes] isn't a snapshot this version understands. */
  fun decodeSnapshot(bytes: ByteArray): WearSnapshot? = runCatching {
    json.decodeFromString(WearSnapshot.serializer(), bytes.decodeToString())
  }
    .getOrNull()

  fun encode(refresh: WearRefresh): ByteArray =
    json.encodeToString(WearRefresh.serializer(), refresh).encodeToByteArray()

  fun decodeRefresh(bytes: ByteArray): WearRefresh? = runCatching {
    json.decodeFromString(WearRefresh.serializer(), bytes.decodeToString())
  }
    .getOrNull()
}

fun wearSnapshot(overviews: List<BikeOverview>, defaultBikeId: BikeId?): WearSnapshot =
  WearSnapshot(defaultBikeId = defaultBikeId?.value, bikes = overviews.map { it.toWear() })

private fun BikeOverview.toWear(): WearBike =
  WearBike(
    id = bike.id.value,
    name = bike.name,
    type = bike.type.name,
    lastSeenAt = bike.lastSeenAt?.toEpochMilliseconds(),
    hubReadAt = bike.hubReadAt?.toEpochMilliseconds(),
    components =
      components.map { (component, latest) ->
        WearComponent(
          id = component.id.value,
          key = component.key,
          kind = component.kind.name,
          firmware = component.firmware,
          latest =
            latest?.let {
              WearReading(
                at = it.at.toEpochMilliseconds(),
                millivolts = it.millivolts,
                percent = it.percent,
                reportedStatus = it.reportedStatus?.name,
                method = it.method.name,
              )
            },
        )
      },
  )

/**
 * The bikes as the watch's summary code expects them, in the snapshot's order. Fields the snapshot
 * doesn't carry (photo, serials, history bookkeeping) are left empty: the watch doesn't show them.
 */
fun WearSnapshot.toOverviews(): List<BikeOverview> = bikes.map { bike ->
  val id = BikeId(bike.id)
  BikeOverview(
    bike =
      Bike(
        id = id,
        name = bike.name,
        type = enumOr(bike.type, BikeType.Other),
        photoPath = null,
        hubSerial = null,
        createdAt = Instant.fromEpochMilliseconds(0),
        lastSeenAt = bike.lastSeenAt?.let(Instant::fromEpochMilliseconds),
        hubReadAt = bike.hubReadAt?.let(Instant::fromEpochMilliseconds),
      ),
    bikeCount = bikes.size,
    components =
      bike.components.map { component ->
        val componentId = ComponentId(component.id)
        ComponentWithReading(
          component =
            Component(
              id = componentId,
              bikeId = id,
              key = component.key,
              serial = null,
              kind = enumOr(component.kind, ComponentKind.UNKNOWN),
              modelId = null,
              deviceType = null,
              firmware = component.firmware,
              actuations = null,
              addedAt = Instant.fromEpochMilliseconds(0),
              lastSeenAt = null,
              removedAt = null,
            ),
          latest =
            component.latest?.let {
              BatteryReading(
                componentId = componentId,
                at = Instant.fromEpochMilliseconds(it.at),
                millivolts = it.millivolts,
                percent = it.percent,
                reportedStatus =
                  it.reportedStatus?.let { status -> enumOr(status, BatteryStatus.UNKNOWN) },
                method = enumOr(it.method, ReadMethod.Advert),
              )
            },
        )
      },
  )
}

/** The bike the tile and complications show: the phone's default, else the first. */
fun WearSnapshot.defaultBike(): BikeOverview? =
  toOverviews().let { bikes ->
    bikes.firstOrNull { it.bike.id.value == defaultBikeId } ?: bikes.firstOrNull()
  }

private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
  enumValues<T>().firstOrNull { it.name == name } ?: fallback
