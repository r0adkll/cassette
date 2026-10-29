package com.r0adkll.cassette.capture.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What capture did, as a capped JSON-lines log: arms, background wakes with the raw adverts heard,
 * GATT reads, adding bikes and ride-test sessions. Shared in "Share diagnostics" (decisions 0005,
 * 0011).
 */
interface CaptureLog {
  val events: Flow<List<CaptureEvent>>

  suspend fun append(event: CaptureEvent)

  suspend fun clear()

  /** The raw log, for sharing. */
  suspend fun export(): String
}

/** Times are epoch milliseconds. */
@Serializable
sealed interface CaptureEvent {
  val at: Long

  @Serializable
  @SerialName("session_started")
  data class SessionStarted(
    override val at: Long,
    val label: String,
    val profile: String,
    val phoneBatteryPercent: Int?,
    /** The everyday profile to put back when the test ends, if the test tried another one. */
    val restoreProfile: String? = null,
  ) : CaptureEvent

  @Serializable
  @SerialName("session_ended")
  data class SessionEnded(override val at: Long, val phoneBatteryPercent: Int?) : CaptureEvent

  /** The background scan was armed or stopped; [result] says which, or why not. */
  @Serializable
  @SerialName("armed")
  data class Armed(
    override val at: Long,
    val profile: String,
    val result: String,
    val reason: String,
    val ok: Boolean,
  ) : CaptureEvent

  /** One background wake. */
  @Serializable
  @SerialName("delivery")
  data class Delivery(override val at: Long, val sightings: List<Sighting>, val error: Int) :
    CaptureEvent

  @Serializable
  @SerialName("gatt")
  data class Gatt(
    override val at: Long,
    val key: String,
    val kind: GattKind,
    val background: Boolean,
    val durationMillis: Long,
    val outcome: GattOutcome,
    /** The table's raw bytes, for hub reads. */
    val hex: String? = null,
  ) : CaptureEvent

  /**
   * Add bike and Add a system (decision 0028): each hub Found heard and how its system started, the
   * rider's switches, when they continued and what was saved. The hub reads are `gatt` events.
   */
  @Serializable
  @SerialName("add_bike")
  data class AddBike(override val at: Long, val detail: String) : CaptureEvent

  /** A background wake ran out of time before its reads finished. */
  @Serializable
  @SerialName("budget_exceeded")
  data class BudgetExceeded(override val at: Long) : CaptureEvent

  /** Wear sync (decision 0013): a snapshot published, or a watch asking for a refresh. */
  @Serializable
  @SerialName("wear")
  data class Wear(override val at: Long, val action: WearAction, val detail: String) : CaptureEvent

  /** iOS widgets (decision 0017): their snapshot written to the App Group, or why it wasn't. */
  @Serializable
  @SerialName("widgets")
  data class Widgets(override val at: Long, val detail: String, val ok: Boolean) : CaptureEvent

  /** The iOS Live Activity during a refresh (decision 0017): started, ended, or why it couldn't. */
  @Serializable
  @SerialName("live_activity")
  data class LiveActivity(override val at: Long, val detail: String, val ok: Boolean) : CaptureEvent
}

@Serializable
data class Sighting(
  val key: String,
  val frame: String,
  val heardAt: Long,
  val rssi: Int,
  val mfg: String? = null,
  val svc: String? = null,
)

enum class WearAction {
  /** A snapshot went to the Data Layer; the detail says how many bikes, or why it failed. */
  Published,
  /** A watch asked the phone to refresh; the detail names the watch. */
  RefreshRequested,
}

enum class GattKind {
  HubTable,
  BatteryLevel,
}

enum class GattOutcome {
  Ok,
  Failed,
  Unsupported,
}

/** The session that has started but not ended, if any. */
fun List<CaptureEvent>.activeSession(): CaptureEvent.SessionStarted? {
  val lastStart = indexOfLast { it is CaptureEvent.SessionStarted }
  if (lastStart == -1) return null
  return if (drop(lastStart + 1).any { it is CaptureEvent.SessionEnded }) null
  else this[lastStart] as CaptureEvent.SessionStarted
}

/** The phone's own battery, for ride tests; null where unknown. */
fun interface PhoneBattery {
  fun percent(): Int?
}

/**
 * Builds a diagnostics bundle (capture log, database snapshot, device info) and hands it to the
 * platform's share sheet. Nothing is uploaded (decision 0011).
 */
fun interface DiagnosticsSharer {
  /** False if the bundle couldn't be built. */
  suspend fun share(): Boolean
}
