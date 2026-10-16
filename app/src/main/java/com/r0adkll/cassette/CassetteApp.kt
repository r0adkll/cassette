package com.r0adkll.cassette

import android.app.Application
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.r0adkll.cassette.axs.AxsReading
import com.r0adkll.cassette.axs.FrameType
import com.r0adkll.cassette.axs.SystemBatteryTable
import com.r0adkll.cassette.cdm.CompanionPresence
import com.r0adkll.cassette.cdm.EVENT_BLE_APPEARED
import com.r0adkll.cassette.cdm.HubAddressTracker
import com.r0adkll.cassette.cdm.PresenceCaptureService
import com.r0adkll.cassette.cdm.matchAssociation
import com.r0adkll.cassette.cdm.presenceEventName
import com.r0adkll.cassette.data.CaptureSource
import com.r0adkll.cassette.data.ReadingStore
import com.r0adkll.cassette.data.needsGattBattery
import com.r0adkll.cassette.data.needsHubRead
import com.r0adkll.cassette.data.resolveIdentity
import com.r0adkll.cassette.scan.AxsScanner
import com.r0adkll.cassette.scan.BatteryGattReader
import com.r0adkll.cassette.scan.GattReadResult
import com.r0adkll.cassette.scan.ScanProfile
import com.r0adkll.cassette.ridetest.GattKind
import com.r0adkll.cassette.ridetest.GattOutcome
import com.r0adkll.cassette.ridetest.PresenceReadPath
import com.r0adkll.cassette.ridetest.RideEvent
import com.r0adkll.cassette.ridetest.RideLog
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CassetteApp : Application() {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val store by lazy { ReadingStore(this, scope) }
  val scanner by lazy { AxsScanner(this) }
  val gattReader by lazy { BatteryGattReader(this) }
  val rideLog by lazy { RideLog(this, scope) }
  /** CDM spike; null below API 36, where the request-based presence API doesn't exist. */
  val presence: CompanionPresence? by lazy { if (Build.VERSION.SDK_INT >= 36) CompanionPresence(this, scope, rideLog) else null }
  private val hubAddresses = HubAddressTracker()

  private val lastGattAttempt = ConcurrentHashMap<String, Long>()
  private val unsupported = ConcurrentHashMap.newKeySet<String>()

  /** Arms or stops the system-held scan. [profile] defaults to the last one used. */
  suspend fun setBackgroundCapture(enabled: Boolean, reason: String, profile: ScanProfile? = null) {
    val chosen = profile ?: store.state.first().backgroundProfile
    // Always stop first: re-registering the same PendingIntent with new settings isn't reliable.
    scanner.stopBackground()
    if (enabled) {
      val result = scanner.startBackground(chosen)
      val now = System.currentTimeMillis()
      store.setBackground(enabled = true, profile = chosen, armedAtMillis = now.takeIf { result == 0 }, error = result.takeIf { it != 0 })
      rideLog.appendIfActive(RideEvent.Armed(now, chosen, result, reason))
    } else {
      store.setBackground(enabled = false, profile = chosen, armedAtMillis = null, error = null)
    }
  }

  suspend fun rearmBackgroundIfEnabled(reason: String) {
    if (store.state.first().backgroundEnabled) setBackgroundCapture(enabled = true, reason = reason)
  }

  /** Starts a ride test: marks the session in the log and arms the background scan with [profile]. */
  suspend fun startRideTest(label: String, profile: ScanProfile) {
    rideLog.append(RideEvent.SessionStarted(System.currentTimeMillis(), label, profile, phoneBatteryPercent()))
    setBackgroundCapture(enabled = true, reason = "ride test started", profile = profile)
  }

  suspend fun stopRideTest() {
    rideLog.append(RideEvent.SessionEnded(System.currentTimeMillis(), phoneBatteryPercent()))
  }

  private fun phoneBatteryPercent(): Int? =
    getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }

  /**
   * Fills in battery that adverts don't carry, over short read-only GATT sessions:
   * - status-frame components (the rear derailleur) are asked for the system battery table, which covers the
   *   shifters and front derailleur too, so those beacon-frame components are never connected to;
   * - the power meter is asked for the standard Battery Level.
   * Readings must already be recorded so the store knows these components.
   */
  suspend fun refreshBatteries(readings: List<AxsReading>, source: CaptureSource) {
    val minAge = if (source == CaptureSource.LIVE) LIVE_GATT_INTERVAL_MILLIS else BACKGROUND_GATT_INTERVAL_MILLIS
    for (incoming in readings.distinctBy { it.key }) {
      val now = System.currentTimeMillis()
      val state = store.state.first()
      // Serial-less frames are keyed by address; resolve them so a hub isn't read twice under two keys.
      val reading = state.resolveIdentity(incoming)
      if (reading.frame == FrameType.STATUS) noteHubAddress(reading)
      if (reading.key in unsupported) continue
      val due =
        when (reading.frame) {
          FrameType.STATUS -> state.needsHubRead(reading.key, now, minAge)
          FrameType.POWER_METER -> state.needsGattBattery(reading.key, now, minAge)
          FrameType.BEACON, FrameType.HUB_ENTRY -> false
        }
      if (!due || !claimAttempt(reading.key, now)) continue

      if (reading.frame == FrameType.STATUS) readHubTable(reading.key, reading.address, source) else readBatteryLevel(reading, source)
    }
  }

  /** CDM spike: how long this process has been running, to tell cold starts from presence wakes. */
  fun processAgeMillis(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()

  /** CDM spike: a presence event from [com.r0adkll.cassette.cdm.HubPresenceService]. Called on the main thread. */
  fun onPresenceEvent(associationId: Int, event: Int) {
    val at = System.currentTimeMillis()
    val processAge = processAgeMillis()
    scope.launch {
      val presence = presence ?: return@launch
      val address = presence.addressFor(associationId)
      rideLog.append(RideEvent.Presence(at, associationId, address, event, presenceEventName(event), processAge))
      if (event != EVENT_BLE_APPEARED || address == null) return@launch
      when (presence.readPath.value) {
        PresenceReadPath.BOUND_SERVICE -> readHubFromPresence(associationId, address, at, PresenceReadPath.BOUND_SERVICE)
        PresenceReadPath.FOREGROUND_SERVICE -> {
          val started = runCatching { PresenceCaptureService.start(this@CassetteApp, associationId, address, at) }
          if (started.isFailure) {
            // Fall back to the bound service so the wake still gets a read; the detail records why.
            rideLog.append(RideEvent.Cdm(System.currentTimeMillis(), "fgs_start_failed", associationId, started.exceptionOrNull()?.toString()))
            readHubFromPresence(associationId, address, at, PresenceReadPath.BOUND_SERVICE, detail = "fgs start failed")
          }
        }
      }
    }
  }

  /** CDM spike: reads the hub table for an associated [address] after it appeared at [appearedAt], and logs how it went. */
  suspend fun readHubFromPresence(associationId: Int, address: String, appearedAt: Long, path: PresenceReadPath, detail: String? = null) {
    val state = store.state.first()
    // Use the hub's serial key when a scan has already heard it at this address, so the table lands on the right record.
    val key =
      state.devices.values.firstOrNull { it.latest.frame == FrameType.STATUS && it.latest.address.equals(address, ignoreCase = true) }?.latest?.key
        ?: address.uppercase()
    // A background scan delivery may be reading the hub right now; wait for it rather than connecting twice.
    var waited = 0L
    while (!claimAttempt(key, System.currentTimeMillis()) && waited < GATT_RETRY_MILLIS) {
      delay(1_000)
      waited += 1_000
    }
    val started = System.currentTimeMillis()
    val outcome = readHubTable(key, address.uppercase(), CaptureSource.PRESENCE)
    val now = System.currentTimeMillis()
    val note = listOfNotNull(detail, "waited ${waited} ms for another read".takeIf { waited > 0 }, "process age ${processAgeMillis()} ms").joinToString("; ")
    rideLog.append(RideEvent.PresenceRead(now, associationId, address, key, path, appearedAt, now - started, outcome, note))
  }

  /** CDM spike: logs a hub's scanned address next to the associated ones, once per process per hub and on change. */
  private suspend fun noteHubAddress(reading: AxsReading) {
    if (reading.address.isEmpty()) return
    val change = hubAddresses.observe(reading.key, reading.address) ?: return
    val associated = presence?.associatedAddresses().orEmpty()
    val matched = matchAssociation(change.address, associated.map { it.associationId to it.address })
    rideLog.append(RideEvent.HubAddress(System.currentTimeMillis(), change.key, change.address, change.previous, associated, matched))
  }

  private suspend fun readHubTable(key: String, address: String, source: CaptureSource): GattOutcome {
    val started = System.currentTimeMillis()
    // Firmware rarely changes and the background wake has a tight time budget, so only read it while the app is open.
    val wanted = listOfNotNull(SystemBatteryTable.CHARACTERISTIC, SystemBatteryTable.VERSIONS_CHARACTERISTIC.takeIf { source == CaptureSource.LIVE })
    val result = gattReader.read(address, wanted)
    val values = (result as? GattReadResult.Values)?.values
    val table = values?.get(SystemBatteryTable.CHARACTERISTIC)?.let(SystemBatteryTable::parse)
    val versions = values?.get(SystemBatteryTable.VERSIONS_CHARACTERISTIC)?.let(SystemBatteryTable::parseVersions)
    val outcome =
      when {
        result !is GattReadResult.Values -> GattOutcome.FAILED
        table == null -> GattOutcome.UNSUPPORTED
        else -> GattOutcome.OK
      }
    logGatt(key, GattKind.HUB_TABLE, source, started, outcome)
    when (outcome) {
      // Not a hub (e.g. a dropper post); don't keep connecting to it.
      GattOutcome.UNSUPPORTED -> unsupported += key
      GattOutcome.OK -> {
        Log.d(TAG, "Hub $key table: $table")
        store.recordHubTable(key, table!!, versions, System.currentTimeMillis(), source)
      }
      GattOutcome.FAILED -> Unit
    }
    return outcome
  }

  private suspend fun readBatteryLevel(reading: AxsReading, source: CaptureSource) {
    val started = System.currentTimeMillis()
    val result = gattReader.read(reading.address, listOf(BatteryGattReader.BATTERY_LEVEL))
    val level =
      (result as? GattReadResult.Values)?.values?.get(BatteryGattReader.BATTERY_LEVEL)?.firstOrNull()?.toInt()?.and(0xFF)?.takeIf { it <= 100 }
    val outcome =
      when {
        result !is GattReadResult.Values -> GattOutcome.FAILED
        level == null -> GattOutcome.UNSUPPORTED
        else -> GattOutcome.OK
      }
    logGatt(reading.key, GattKind.BATTERY_LEVEL, source, started, outcome)
    when (outcome) {
      GattOutcome.UNSUPPORTED -> unsupported += reading.key
      GattOutcome.OK -> store.recordGattBattery(reading.key, level!!, System.currentTimeMillis(), source)
      GattOutcome.FAILED -> Unit
    }
  }

  private suspend fun logGatt(key: String, kind: GattKind, source: CaptureSource, startedAt: Long, outcome: GattOutcome) {
    val now = System.currentTimeMillis()
    rideLog.appendIfActive(RideEvent.Gatt(now, key, kind, source, now - startedAt, outcome))
  }

  /** Guards concurrent deliveries and backs off after failures. */
  private fun claimAttempt(key: String, now: Long): Boolean {
    val previous = lastGattAttempt[key]
    if (previous != null && now - previous < GATT_RETRY_MILLIS) return false
    return if (previous == null) lastGattAttempt.putIfAbsent(key, now) == null else lastGattAttempt.replace(key, previous, now)
  }

  private companion object {
    const val LIVE_GATT_INTERVAL_MILLIS = 60_000L
    // Short rides need more than one read; a read costs a few seconds of radio time.
    const val BACKGROUND_GATT_INTERVAL_MILLIS = 10 * 60_000L
    // Below the ~30 s between rear derailleur wakes, so a failed read is retried on the next one.
    const val GATT_RETRY_MILLIS = 20_000L
    const val TAG = "CassetteScan"
  }
}
