package com.r0adkll.cassette.ridetest

import android.content.Context
import android.util.Log
import com.r0adkll.cassette.axs.FrameType
import com.r0adkll.cassette.data.CaptureSource
import com.r0adkll.cassette.scan.ScanProfile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What happened during ride tests. Everything but session markers is only logged while a session is active,
 * except the CompanionDeviceManager spike's events ([alwaysLogged]), which are logged all the time so presence
 * can be followed over days.
 */
@Serializable
sealed interface RideEvent {
  val at: Long

  @Serializable
  @SerialName("session_started")
  data class SessionStarted(override val at: Long, val label: String, val profile: ScanProfile, val phoneBatteryPercent: Int?) : RideEvent

  @Serializable
  @SerialName("session_ended")
  data class SessionEnded(override val at: Long, val phoneBatteryPercent: Int?) : RideEvent

  /** The background scan was (re)armed; [result] 0 is success, otherwise a scan error code. */
  @Serializable
  @SerialName("armed")
  data class Armed(override val at: Long, val profile: ScanProfile, val result: Int, val reason: String) : RideEvent

  /** One background wake from the system-held scan. */
  @Serializable
  @SerialName("delivery")
  data class Delivery(override val at: Long, val results: Int, val sightings: List<Sighting>, val callbackType: Int, val error: Int) : RideEvent

  @Serializable
  @SerialName("gatt")
  data class Gatt(
    override val at: Long,
    val key: String,
    val kind: GattKind,
    val source: CaptureSource,
    val durationMillis: Long,
    val outcome: GattOutcome,
  ) : RideEvent

  /** A background wake ran out of its goAsync() budget before the GATT work finished. */
  @Serializable
  @SerialName("budget_exceeded")
  data class BudgetExceeded(override val at: Long) : RideEvent

  /**
   * CDM spike: the system reported a presence change for an association. [event] is the raw
   * `DevicePresenceEvent` constant, [eventName] its readable name. [processAgeMillis] is how long the process
   * had been running, so cold starts from a presence wake are visible.
   */
  @Serializable
  @SerialName("presence")
  data class Presence(
    override val at: Long,
    val associationId: Int,
    val address: String?,
    val event: Int,
    val eventName: String,
    val processAgeMillis: Long? = null,
  ) : RideEvent

  /** CDM spike: the hub read started by an "appeared" event, with the latency from that event to the result. */
  @Serializable
  @SerialName("presence_read")
  data class PresenceRead(
    override val at: Long,
    val associationId: Int,
    val address: String,
    val key: String,
    val path: PresenceReadPath,
    val appearedAt: Long,
    val durationMillis: Long,
    val outcome: GattOutcome,
    val detail: String? = null,
  ) : RideEvent

  /** CDM spike: association, observation and service lifecycle actions, plus failures. */
  @Serializable
  @SerialName("cdm")
  data class Cdm(override val at: Long, val action: String, val associationId: Int? = null, val detail: String? = null) : RideEvent

  /**
   * CDM spike: the address a normal scan heard a hub's status frame from, next to the associations' addresses,
   * so address changes (sleep, battery swap, next day) are visible. Logged once per process per hub and on change.
   */
  @Serializable
  @SerialName("hub_address")
  data class HubAddress(
    override val at: Long,
    val key: String,
    val scannedAddress: String,
    val previousAddress: String?,
    val associated: List<AssociatedAddress>,
    val matchedAssociationId: Int?,
  ) : RideEvent
}

@Serializable data class AssociatedAddress(val associationId: Int, val address: String?)

/** How the hub read after a presence "appeared" ran. */
enum class PresenceReadPath {
  /** Directly from the bound `CompanionDeviceService`. */
  BOUND_SERVICE,
  /** In a short `connectedDevice` foreground service started from the presence wake. */
  FOREGROUND_SERVICE,
}

/** The CDM spike's events, logged whether or not a ride test is running and kept when ride tests are cleared. */
val RideEvent.alwaysLogged: Boolean
  get() = this is RideEvent.Presence || this is RideEvent.PresenceRead || this is RideEvent.Cdm || this is RideEvent.HubAddress

@Serializable data class Sighting(val key: String, val frame: FrameType, val timestampMillis: Long)

enum class GattKind {
  HUB_TABLE,
  BATTERY_LEVEL,
}

enum class GattOutcome {
  OK,
  FAILED,
  UNSUPPORTED,
}

/** Append-only JSON-lines log in app storage; pull it with `adb shell run-as … cat files/ride-log.jsonl`. */
class RideLog(context: Context, scope: CoroutineScope) {
  private val file = File(context.filesDir, "ride-log.jsonl")
  private val json = Json { ignoreUnknownKeys = true }
  private val mutex = Mutex()
  private var loaded = false

  private val _events = MutableStateFlow<List<RideEvent>>(emptyList())
  val events: StateFlow<List<RideEvent>> = _events.asStateFlow()

  init {
    scope.launch { mutex.withLock { ensureLoaded() } }
  }

  suspend fun isSessionActive(): Boolean = mutex.withLock {
    ensureLoaded()
    _events.value.activeSession() != null
  }

  /** Logs [event] whether or not a ride test is running. */
  suspend fun append(event: RideEvent) = mutex.withLock {
    ensureLoaded()
    write(event)
  }

  /** Logs [event] only while a ride test is running, so normal use doesn't grow the file. */
  suspend fun appendIfActive(event: RideEvent) = mutex.withLock {
    ensureLoaded()
    if (_events.value.activeSession() != null) write(event)
  }

  /** Removes the events [remove] matches, rewriting the file with the rest. */
  suspend fun clear(remove: (RideEvent) -> Boolean = { true }) = mutex.withLock {
    ensureLoaded()
    val kept = _events.value.filterNot(remove)
    withContext(Dispatchers.IO) {
      if (kept.isEmpty()) file.delete() else file.writeText(kept.joinToString("") { json.encodeToString(RideEvent.serializer(), it) + "\n" })
    }
    _events.value = kept
  }

  private suspend fun write(event: RideEvent) {
    withContext(Dispatchers.IO) { file.appendText(json.encodeToString(RideEvent.serializer(), event) + "\n") }
    _events.update { it + event }
  }

  private suspend fun ensureLoaded() {
    if (loaded) return
    val lines = withContext(Dispatchers.IO) { if (file.exists()) file.readLines() else emptyList() }
    _events.value =
      lines.mapNotNull { line ->
        runCatching { json.decodeFromString(RideEvent.serializer(), line) }.onFailure { Log.w("CassetteScan", "Skipping bad ride log line", it) }.getOrNull()
      }
    loaded = true
  }
}

/** The session that has started but not ended, if any. */
fun List<RideEvent>.activeSession(): RideEvent.SessionStarted? {
  val lastStart = indexOfLast { it is RideEvent.SessionStarted }
  if (lastStart == -1) return null
  return if (drop(lastStart + 1).any { it is RideEvent.SessionEnded }) null else this[lastStart] as RideEvent.SessionStarted
}
