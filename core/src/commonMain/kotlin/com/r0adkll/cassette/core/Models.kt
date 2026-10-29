package com.r0adkll.cassette.core

import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.ComponentKind
import kotlin.jvm.JvmInline
import kotlin.time.Instant

@JvmInline value class BikeId(val value: Long)

@JvmInline value class ComponentId(val value: Long)

enum class BikeType {
  Road,
  Gravel,
  Mtb,
  Other,
}

/**
 * A bike: one or more AXS systems, each known by its [Hub] (the rear derailleur on road), plus
 * anything attached to it by hand, such as a power meter (decisions 0002 and 0028).
 */
data class Bike(
  val id: BikeId,
  val name: String,
  val type: BikeType,
  /** Local file of the optional bike photo; never uploaded (decision 0010). */
  val photoPath: String?,
  /** The hub it was added with; null until a hub has been read. Every system is a [Hub]. */
  val hubSerial: Long?,
  val createdAt: Instant,
  val lastSeenAt: Instant?,
  /** When one of its hubs' battery tables was last read. */
  val hubReadAt: Instant? = null,
)

/**
 * One of a bike's systems (decision 0028): its hub, the component whose battery and firmware tables
 * list the system's components.
 */
data class Hub(
  val serial: Long,
  val bikeId: BikeId,
  val addedAt: Instant,
  /** When its battery table was last read. */
  val readAt: Instant?,
)

/** A component on a bike. Components absent from the hub table for 30 days get [removedAt]. */
data class Component(
  val id: ComponentId,
  val bikeId: BikeId,
  /**
   * Stable identity across sightings: the serial when known, otherwise the advertised name (Quarq),
   * matching the POC's reading key.
   */
  val key: String,
  val serial: Long?,
  val kind: ComponentKind,
  val modelId: Int?,
  val deviceType: Int?,
  val firmware: String?,
  val actuations: Long?,
  val addedAt: Instant,
  val lastSeenAt: Instant?,
  val removedAt: Instant?,
  /** Last BLE address heard. */
  val address: String? = null,
  /** The [Hub] whose table lists it, which is its system; null for a power meter. */
  val hubSerial: Long? = null,
)

/** How a battery reading reached us. */
enum class ReadMethod {
  /** Decoded from an advertisement (status frames). */
  Advert,
  /** Read from the component's own GATT battery characteristic (Quarq). */
  Gatt,
  /** A row in the hub's battery table. */
  Hub,
}

/**
 * One battery reading. History keeps these for a year, one per day after 30 days (decision 0010).
 */
data class BatteryReading(
  val componentId: ComponentId,
  val at: Instant,
  val millivolts: Int?,
  /**
   * Only where the component reports a percentage; estimates are never stored here (decision 0003).
   */
  val percent: Int?,
  /** The component's own status, when it reports one. */
  val reportedStatus: BatteryStatus?,
  val method: ReadMethod,
)
