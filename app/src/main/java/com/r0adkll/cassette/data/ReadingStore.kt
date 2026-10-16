package com.r0adkll.cassette.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.r0adkll.cassette.axs.AxsReading
import com.r0adkll.cassette.axs.BatteryStatus
import com.r0adkll.cassette.axs.ComponentKind
import com.r0adkll.cassette.axs.FrameType
import com.r0adkll.cassette.axs.ComponentVersions
import com.r0adkll.cassette.axs.SystemComponent
import com.r0adkll.cassette.axs.SystemTable
import com.r0adkll.cassette.axs.batteryStatus
import com.r0adkll.cassette.scan.ScanProfile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class CaptureState(
  val devices: Map<String, DeviceRecord> = emptyMap(),
  val history: List<HistoryPoint> = emptyList(),
  val backgroundEnabled: Boolean = false,
  val backgroundProfile: ScanProfile = ScanProfile.ALL_LOW_POWER,
  val backgroundArmedAtMillis: Long? = null,
  val backgroundArmError: Int? = null,
  val lastBackgroundDeliveryMillis: Long? = null,
  val backgroundDeliveries: Int = 0,
  /** User-assigned names keyed by component key, e.g. "Left shifter". */
  val labels: Map<String, String> = emptyMap(),
  /** When each hub (rear derailleur) last had its battery table read. */
  val hubReadAtMillis: Map<String, Long> = emptyMap(),
)

@Serializable
data class DeviceRecord(
  val latest: AxsReading,
  /** Last known battery, kept across frames that carry none. */
  val battery: BatterySample?,
  val firstSeenMillis: Long,
  val sightings: Int,
  val lastSource: CaptureSource,
  /** Shift count from the hub table (`actuation_count`). */
  val actuations: Long? = null,
  /** Firmware from the hub's version table, for components whose adverts we may never hear. */
  val hubFirmware: String? = null,
  /** Serial of the hub (rear derailleur) whose table lists this component: the bike it belongs to. */
  val systemSerial: Long? = null,
)

@Serializable
data class BatterySample(
  val timestampMillis: Long,
  val millivolts: Int?,
  val percent: Int?,
  val method: BatteryMethod,
  /** The component's own status ("Battery good"); preferred over anything we derive from voltage. */
  val reported: BatteryStatus? = null,
) {
  val status: BatteryStatus
    get() = reported ?: batteryStatus(millivolts, percent)
}

enum class BatteryMethod {
  /** Decoded from a status advert. */
  ADVERT,
  /** Read from the Battery Level characteristic over a short GATT connection. */
  GATT,
  /** From the rear derailleur's battery table, which covers every paired component. */
  HUB,
}

@Serializable
data class HistoryPoint(
  val key: String,
  val timestampMillis: Long,
  val millivolts: Int?,
  val percent: Int?,
  val method: BatteryMethod,
  val source: CaptureSource,
)

enum class CaptureSource {
  LIVE,
  BACKGROUND,
}

/** Latest reading per component plus a sparse battery history, persisted as JSON. */
class ReadingStore(context: Context, scope: CoroutineScope) {
  private val dataStore: DataStore<CaptureState> =
    DataStoreFactory.create(
      serializer = CaptureStateSerializer,
      corruptionHandler = ReplaceFileCorruptionHandler { CaptureState() },
      scope = scope,
      produceFile = { File(context.filesDir, "capture-v2.json") },
    )

  val state: Flow<CaptureState> = dataStore.data

  suspend fun record(readings: List<AxsReading>, source: CaptureSource) {
    if (readings.isEmpty()) return
    dataStore.updateData { current -> readings.fold(current) { state, reading -> state.withReading(reading, source) } }
  }

  suspend fun recordBackgroundDelivery(readings: List<AxsReading>, atMillis: Long) {
    dataStore.updateData { current ->
      readings
        .fold(current) { state, reading -> state.withReading(reading, CaptureSource.BACKGROUND) }
        .copy(lastBackgroundDeliveryMillis = atMillis, backgroundDeliveries = current.backgroundDeliveries + 1)
    }
  }

  suspend fun recordGattBattery(key: String, percent: Int, atMillis: Long, source: CaptureSource) {
    dataStore.updateData { it.withGattBattery(key, percent, atMillis, source) }
  }

  suspend fun recordHubTable(
    hubKey: String,
    table: SystemTable<SystemComponent>,
    versions: SystemTable<ComponentVersions>?,
    atMillis: Long,
    source: CaptureSource,
  ) {
    dataStore.updateData { it.withHubTable(hubKey, table, versions, atMillis, source) }
  }

  suspend fun setBackground(enabled: Boolean, profile: ScanProfile, armedAtMillis: Long?, error: Int?) {
    dataStore.updateData {
      it.copy(backgroundEnabled = enabled, backgroundProfile = profile, backgroundArmedAtMillis = armedAtMillis, backgroundArmError = error)
    }
  }

  suspend fun setLabel(key: String, label: String?) {
    dataStore.updateData { state ->
      state.copy(labels = if (label.isNullOrBlank()) state.labels - key else state.labels + (key to label.trim()))
    }
  }

  suspend fun clearReadings() {
    dataStore.updateData {
      it.copy(devices = emptyMap(), history = emptyList(), hubReadAtMillis = emptyMap(), lastBackgroundDeliveryMillis = null, backgroundDeliveries = 0)
    }
  }
}

internal fun CaptureState.withReading(incoming: AxsReading, source: CaptureSource): CaptureState {
  val state = if (incoming.serial != null) withoutAddressDuplicate(incoming) else this
  val reading = state.resolveIdentity(incoming)
  return state.withResolvedReading(reading, source)
}

/**
 * Some frames carry no serial (no service data, no name), so the parser falls back to the BLE address.
 * Attribute those to the component already known at that address, keeping its identity and class.
 */
internal fun CaptureState.resolveIdentity(reading: AxsReading): AxsReading {
  if (reading.serial != null) return reading
  val known = devices.values.map { it.latest }.firstOrNull { it.serial != null && it.address == reading.address } ?: return reading
  return reading.copy(
    key = known.key,
    serial = known.serial,
    kind = if (reading.kind == ComponentKind.UNKNOWN) known.kind else reading.kind,
    classByte = reading.classByte ?: known.classByte,
    name = reading.name ?: known.name,
  )
}

/** Folds state saved under the address (before the serial was known) into the serial-keyed component. */
private fun CaptureState.withoutAddressDuplicate(reading: AxsReading): CaptureState {
  val address = reading.address
  if (address == reading.key || (address !in devices && address !in hubReadAtMillis && address !in labels)) return this
  return copy(
    devices = devices - address,
    history = history.map { if (it.key == address) it.copy(key = reading.key) else it },
    // Without this the hub would be read again as soon as its serial shows up.
    hubReadAtMillis = hubReadAtMillis.moveKey(address, reading.key) { old, new -> maxOf(old, new) },
    labels = labels.moveKey(address, reading.key) { old, _ -> old },
  )
}

private fun <V> Map<String, V>.moveKey(from: String, to: String, merge: (existing: V, moved: V) -> V): Map<String, V> {
  val moved = this[from] ?: return this
  val existing = this[to]
  return this - from + (to to if (existing != null) merge(existing, moved) else moved)
}

private fun CaptureState.withResolvedReading(reading: AxsReading, source: CaptureSource): CaptureState {
  val existing = devices[reading.key]
  // Components advertise several times a second; keep one write per component every few seconds.
  if (existing != null && reading.timestampMillis - existing.latest.timestampMillis < DEVICE_WRITE_INTERVAL_MILLIS) return this

  // Not every frame says everything (beacons carry no device type, some RD frames no service data);
  // keep what earlier frames or the hub table told us.
  val previous = existing?.latest
  val latest =
    reading.copy(
      kind = reading.kind.takeIf { it != ComponentKind.UNKNOWN } ?: previous?.kind ?: reading.kind,
      classByte = reading.classByte ?: previous?.classByte,
      modelId = reading.modelId ?: previous?.modelId,
      firmware = reading.firmware ?: previous?.firmware,
    )
  val record =
    (existing ?: DeviceRecord(latest, battery = null, firstSeenMillis = reading.timestampMillis, sightings = 0, lastSource = source))
      .let { it.copy(latest = latest, sightings = it.sightings + 1, lastSource = source) }
  val updated = copy(devices = devices + (reading.key to record))
  val battery = reading.battery ?: return updated
  return updated.withBattery(
    reading.key,
    BatterySample(reading.timestampMillis, battery.millivolts, battery.percent, BatteryMethod.ADVERT, battery.reportedStatus),
    source,
  )
}

private fun CaptureState.withBattery(key: String, sample: BatterySample, source: CaptureSource): CaptureState {
  val record = devices[key] ?: return this
  val lastPoint = history.lastOrNull { it.key == key }
  val newHistory =
    if (lastPoint == null || sample.timestampMillis - lastPoint.timestampMillis >= HISTORY_INTERVAL_MILLIS) {
      (history + HistoryPoint(key, sample.timestampMillis, sample.millivolts, sample.percent, sample.method, source)).takeLast(MAX_HISTORY)
    } else {
      history
    }
  return copy(devices = devices + (key to record.copy(battery = sample)), history = newHistory)
}

internal fun CaptureState.withGattBattery(key: String, percent: Int, at: Long, source: CaptureSource = CaptureSource.LIVE): CaptureState =
  withBattery(key, BatterySample(at, millivolts = null, percent = percent, BatteryMethod.GATT), source)

/**
 * Applies the hub's tables: battery, status and shift count for every component, plus firmware when [versions]
 * was read. The hub's own row skips battery (its adverts carry voltage, percent and status) but keeps the rest.
 */
internal fun CaptureState.withHubTable(
  hubKey: String,
  table: SystemTable<SystemComponent>,
  versions: SystemTable<ComponentVersions>?,
  at: Long,
  source: CaptureSource,
): CaptureState {
  val firmwareBySerial = versions?.rows?.associate { it.serial to it.firmware }.orEmpty()
  val updated =
    table.rows.fold(this) { state, component ->
      val key = component.serial.toString()
      val existing = state.devices[key]
      val kind = component.kind
      val latest =
        existing?.latest?.let {
          it.copy(
            kind = kind.takeIf { k -> k != ComponentKind.UNKNOWN } ?: it.kind,
            classByte = component.deviceType,
            modelId = component.modelId,
          )
        }
          ?: AxsReading(
            key = key,
            serial = component.serial,
            address = "", // Never heard directly; an empty address keeps it out of address-based identity matching.
            name = null,
            rssi = 0,
            timestampMillis = at,
            frame = FrameType.HUB_ENTRY,
            kind = kind,
            battery = null,
            firmware = null,
            manufacturerHex = null,
            serviceDataHex = null,
            classByte = component.deviceType,
            modelId = component.modelId,
          )
      val record =
        (existing ?: DeviceRecord(latest, battery = null, firstSeenMillis = at, sightings = 0, lastSource = source)).copy(
          latest = latest,
          actuations = component.actuations,
          hubFirmware = firmwareBySerial[component.serial] ?: existing?.hubFirmware,
          systemSerial = table.masterSerial,
        )
      val withRecord = state.copy(devices = state.devices + (key to record))
      if (component.serial == table.masterSerial) {
        withRecord
      } else {
        withRecord.withBattery(key, BatterySample(at, component.millivolts, percent = null, BatteryMethod.HUB, component.reportedStatus), source)
      }
    }
  // Mark both keys: a serial-less frame may have triggered this read under the hub's address.
  return updated.copy(hubReadAtMillis = hubReadAtMillis + (hubKey to at) + (table.masterSerial.toString() to at))
}

fun CaptureState.needsHubRead(hubKey: String, nowMillis: Long, minAgeMillis: Long): Boolean {
  val last = hubReadAtMillis[hubKey] ?: return true
  return nowMillis - last >= minAgeMillis
}

/** True when this component's adverts carry no battery and we haven't read it over GATT recently. */
fun CaptureState.needsGattBattery(key: String, nowMillis: Long, minAgeMillis: Long): Boolean {
  val record = devices[key] ?: return false
  if (record.latest.battery != null) return false
  val battery = record.battery ?: return true
  return nowMillis - battery.timestampMillis >= minAgeMillis
}

private const val DEVICE_WRITE_INTERVAL_MILLIS = 5_000L
private const val HISTORY_INTERVAL_MILLIS = 10 * 60_000L
private const val MAX_HISTORY = 2_000

private object CaptureStateSerializer : Serializer<CaptureState> {
  private val json = Json { ignoreUnknownKeys = true }

  override val defaultValue = CaptureState()

  override suspend fun readFrom(input: InputStream): CaptureState =
    try {
      json.decodeFromString(input.readBytes().decodeToString())
    } catch (e: SerializationException) {
      throw CorruptionException("Unreadable capture state", e)
    }

  override suspend fun writeTo(t: CaptureState, output: OutputStream) {
    output.write(json.encodeToString(t).encodeToByteArray())
  }
}
