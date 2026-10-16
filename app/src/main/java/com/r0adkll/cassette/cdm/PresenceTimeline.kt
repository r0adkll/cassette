package com.r0adkll.cassette.cdm

import com.r0adkll.cassette.ridetest.RideEvent

/** One presence event for the CDM spike card, with the hub read an "appeared" event started (if it finished). */
data class PresenceRow(val event: RideEvent.Presence, val read: RideEvent.PresenceRead?) {
  /** Time from the presence event to the hub read's result. */
  val latencyMillis: Long?
    get() = read?.let { it.at - event.at }
}

/** The newest [limit] presence events, newest first, each paired with the hub read it started. */
fun presenceTimeline(events: List<RideEvent>, limit: Int = 20): List<PresenceRow> {
  // A read records the time of the event that started it, which pairs them exactly.
  val reads = events.filterIsInstance<RideEvent.PresenceRead>().groupBy { it.associationId to it.appearedAt }
  return events
    .filterIsInstance<RideEvent.Presence>()
    .sortedByDescending { it.at }
    .take(limit)
    .map { presence -> PresenceRow(presence, reads[presence.associationId to presence.at]?.minByOrNull { it.at }) }
}

/** Readable names for `DevicePresenceEvent` constants (values from the API 36 SDK). */
fun presenceEventName(event: Int): String =
  when (event) {
    EVENT_BLE_APPEARED -> "ble_appeared"
    EVENT_BLE_DISAPPEARED -> "ble_disappeared"
    2 -> "bt_connected"
    3 -> "bt_disconnected"
    4 -> "self_managed_appeared"
    5 -> "self_managed_disappeared"
    else -> "unknown_$event"
  }

const val EVENT_BLE_APPEARED = 0
const val EVENT_BLE_DISAPPEARED = 1

/**
 * Decides when a scanned hub address is worth logging: the first time a hub is heard in this process, and
 * whenever its address changes. Thread-safe, since the live scan and background deliveries both feed it.
 */
class HubAddressTracker {
  private val lastAddress = mutableMapOf<String, String>()

  /** Returns a [Change] to log, or null when [address] is what was last logged for [key]. */
  @Synchronized
  fun observe(key: String, address: String): Change? {
    val normalized = address.uppercase()
    val previous = lastAddress[key]
    if (previous == normalized) return null
    lastAddress[key] = normalized
    return Change(key, normalized, previous)
  }

  data class Change(val key: String, val address: String, val previous: String?)
}

/** Which association, if any, has [address]. Addresses are compared case-insensitively. */
fun matchAssociation(address: String, associations: List<Pair<Int, String?>>): Int? =
  associations.firstOrNull { (_, mac) -> mac != null && mac.equals(address, ignoreCase = true) }?.first
