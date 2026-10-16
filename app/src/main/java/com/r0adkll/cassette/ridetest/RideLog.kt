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

/** What happened during ride tests. Everything but session markers is only logged while a session is active. */
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
}

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

  suspend fun append(event: RideEvent) = mutex.withLock {
    ensureLoaded()
    write(event)
  }

  /** Logs [event] only while a ride test is running, so normal use doesn't grow the file. */
  suspend fun appendIfActive(event: RideEvent) = mutex.withLock {
    ensureLoaded()
    if (_events.value.activeSession() != null) write(event)
  }

  suspend fun clear() = mutex.withLock {
    withContext(Dispatchers.IO) { file.delete() }
    _events.value = emptyList()
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
