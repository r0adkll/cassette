package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.alerts.api.Alerts
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.capture.api.RefreshRun
import com.r0adkll.cassette.capture.api.Sighting
import com.r0adkll.cassette.data.ble.AdvertScanner
import com.r0adkll.cassette.data.ble.ArmResult
import com.r0adkll.cassette.data.ble.BackgroundDeliveryHandler
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.protocol.AxsReading
import com.r0adkll.cassette.settings.api.CassetteSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultCaptureController(
  private val scanner: AdvertScanner,
  private val recorder: CaptureRecorder,
  private val refresher: BatteryRefresher,
  private val db: CassetteDatabase,
  private val settings: CassetteSettings,
  private val log: CaptureLog,
  private val alerts: Alerts,
  private val clock: Clock,
) : CaptureController {

  override val isReady: Boolean
    get() = scanner.isReady

  override fun refresh(): Flow<RefreshProgress> = refresh(emptyFlow())

  private val _latestRefresh = MutableStateFlow<RefreshRun?>(null)
  override val latestRefresh: StateFlow<RefreshRun?> = _latestRefresh.asStateFlow()

  /** Stop and Add 60 s from outside the refresh's collector; dropped when none is running. */
  private val controls = MutableSharedFlow<Control>(extraBufferCapacity = 4)

  override fun stopRefresh() {
    controls.tryEmit(Control.Stop)
  }

  override fun addRefreshTime() {
    controls.tryEmit(Control.More)
  }

  override fun refresh(more: Flow<Unit>): Flow<RefreshProgress> {
    if (!scanner.isReady) return flow { emit(RefreshProgress.NotReady) }
    return channelFlow {
      val started = clock.now()
      val heardIds = mutableSetOf<Long>()
      val bikeIds = mutableSetOf<Long>()
      var hubRead = false
      var stopped = false
      var length = REFRESH_LENGTH
      val batches = Channel<AxsReading>(Channel.UNLIMITED)

      fun publish(progress: RefreshProgress, ended: Boolean = false) {
        _latestRefresh.value = RefreshRun(started, progress, clock.now().takeIf { ended })
      }

      fun addTime() {
        val elapsed = clock.now() - started
        length = minOf(length + REFRESH_LENGTH, elapsed + RefreshProgress.MaxSecondsLeft.seconds)
      }

      launch { scanner.live().collect { batches.send(it) } }
      launch { more.collect { addTime() } }
      launch {
        controls.collect {
          when (it) {
            Control.Stop -> stopped = true
            Control.More -> addTime()
          }
        }
      }

      try {
        while (clock.now() - started < length && !stopped) {
          // Drain whatever arrived in the last second.
          val batch = buildList {
            while (true) add(batches.tryReceive().getOrNull() ?: break)
          }
          if (batch.isNotEmpty()) {
            val heard = recorder.record(batch)
            heard.forEach {
              heardIds += it.component.id.value
              bikeIds += it.bike.id.value
            }
            if (refresher.refresh(heard, CaptureMode.Live).hubRead) hubRead = true
            // The app is open: battery alerts only; the pre-ride nudge is for background wakes.
            alerts.onCaptured(heard.map { it.bike.id }.toSet(), woke = emptySet())
          }
          if (stopped) break
          val left = (length - (clock.now() - started)).inWholeSeconds.toInt().coerceAtLeast(0)
          val scanning =
            RefreshProgress.Scanning(left, heardIds.size, length.inWholeSeconds.toInt())
          publish(scanning)
          send(scanning)
          if (hubRead && everythingReadSince(bikeIds, started.toEpochMilliseconds())) break
          delay(1.seconds)
        }
        val done = RefreshProgress.Done(heardIds.size, hubRead, stopped)
        publish(done, ended = true)
        send(done)
      } finally {
        // Cancelled by its collector (the bike screen's Stop): ended early all the same.
        if (_latestRefresh.value?.let { it.startedAt == started && it.endedAt == null } == true) {
          publish(RefreshProgress.Done(heardIds.size, hubRead, stopped = true), ended = true)
        }
      }
      // Stops the live scan.
      channel.close()
    }
  }

  /** Every current component of the bikes we heard has a reading from this refresh. */
  private fun everythingReadSince(bikeIds: Set<Long>, since: Long): Boolean =
    bikeIds.isNotEmpty() &&
      bikeIds.all { bikeId ->
        db.componentQueries.selectForBike(bikeId).executeAsList().all { component ->
          val latest = db.batterySampleQueries.latestForComponent(component.id).executeAsOneOrNull()
          latest != null && latest.at >= since
        }
      }

  override suspend fun ensureBackgroundCapture(reason: String) {
    val hasBike = db.bikeQueries.selectAll().executeAsList().isNotEmpty()
    val enabled = settings.backgroundCapture.first()
    if (!hasBike || !enabled) {
      scanner.stopBackground()
      val why = if (!hasBike) "Off: no bike yet" else "Off in Settings"
      settings.recordArm(why, clock.now())
      log.append(CaptureEvent.Armed(clock.now().toEpochMilliseconds(), "", why, reason, ok = false))
      return
    }
    val profile = settings.scanProfile.first()
    val result = scanner.startBackground(profile)
    val description =
      when (result) {
        ArmResult.Armed -> "Armed (${scanner.describe(profile)})"
        ArmResult.BluetoothOff -> "Not armed: Bluetooth is off"
        ArmResult.NoPermission -> "Not armed: Nearby devices not allowed"
        is ArmResult.Failed -> "Not armed: scan error ${result.code}"
      }
    settings.recordArm("$description · $reason", clock.now())
    log.append(
      CaptureEvent.Armed(
        clock.now().toEpochMilliseconds(),
        profile.name,
        description,
        reason,
        ok = result == ArmResult.Armed,
      )
    )
  }

  /** One background wake: record what was heard and read batteries that are due. */
  suspend fun onBackgroundDelivery(readings: List<AxsReading>, errorCode: Int = 0) {
    settings.recordDelivery(readings.size, clock.now())
    log.append(
      CaptureEvent.Delivery(
        at = clock.now().toEpochMilliseconds(),
        sightings =
          readings.map {
            Sighting(
              it.key,
              it.frame.name,
              it.timestampMillis,
              it.rssi,
              it.manufacturerHex,
              it.serviceDataHex,
            )
          },
        error = errorCode,
      )
    )
    val seenBefore =
      db.bikeQueries.selectAll().executeAsList().associate { it.id to it.last_seen_at }
    val heard = recorder.record(readings)
    refresher.refresh(heard, CaptureMode.Background)
    val bikes = heard.map { it.bike.id }.toSet()
    // Woke: heard now after a quiet spell (or never heard before).
    val cutoff = (clock.now() - ASLEEP_AFTER).toEpochMilliseconds()
    val woke = bikes.filter { seenBefore[it.value]?.let { seen -> seen < cutoff } ?: true }.toSet()
    alerts.onCaptured(bikes, woke)
  }

  private enum class Control {
    Stop,
    More,
  }

  private companion object {
    val REFRESH_LENGTH = 60.seconds
    /**
     * AXS components sleep after a few minutes still; this much quiet means the bike was put away.
     */
    val ASLEEP_AFTER = 30.minutes
  }
}

@ContributesBinding(AppScope::class)
@Inject
class CaptureBackgroundDeliveryHandler(
  private val controller: DefaultCaptureController,
  private val log: CaptureLog,
  private val clock: Clock,
) : BackgroundDeliveryHandler {
  override suspend fun onDelivery(readings: List<AxsReading>, errorCode: Int) =
    controller.onBackgroundDelivery(readings, errorCode)

  override suspend fun onBudgetExceeded() =
    log.append(CaptureEvent.BudgetExceeded(clock.now().toEpochMilliseconds()))
}
