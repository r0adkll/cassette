package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.data.ble.AdvertScanner
import com.r0adkll.cassette.data.ble.ArmResult
import com.r0adkll.cassette.data.ble.BackgroundDeliveryHandler
import com.r0adkll.cassette.data.ble.ScanProfile
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.protocol.AxsReading
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultCaptureController(
    private val scanner: AdvertScanner,
    private val recorder: CaptureRecorder,
    private val refresher: BatteryRefresher,
    private val db: CassetteDatabase,
    private val clock: Clock,
) : CaptureController {

    override val isReady: Boolean
        get() = scanner.isReady

    override fun refresh(): Flow<RefreshProgress> {
        if (!scanner.isReady) return flow { emit(RefreshProgress.NotReady) }
        return channelFlow {
            val started = clock.now()
            val heardIds = mutableSetOf<Long>()
            val bikeIds = mutableSetOf<Long>()
            var hubRead = false
            val batches = Channel<AxsReading>(Channel.UNLIMITED)

            launch { scanner.live().collect { batches.send(it) } }

            withTimeoutOrNull(REFRESH_LENGTH) {
                while (true) {
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
                    }
                    val left =
                        (REFRESH_LENGTH - (clock.now() - started))
                            .inWholeSeconds
                            .toInt()
                            .coerceAtLeast(0)
                    send(RefreshProgress.Scanning(left, heardIds.size))
                    if (hubRead && everythingReadSince(bikeIds, started.toEpochMilliseconds()))
                        break
                    delay(1.seconds)
                }
            }
            send(RefreshProgress.Done(heardIds.size, hubRead))
            // Stops the live scan.
            channel.close()
        }
    }

    /** Every current component of the bikes we heard has a reading from this refresh. */
    private fun everythingReadSince(bikeIds: Set<Long>, since: Long): Boolean =
        bikeIds.isNotEmpty() &&
            bikeIds.all { bikeId ->
                db.componentQueries.selectForBike(bikeId).executeAsList().all { component ->
                    val latest =
                        db.batterySampleQueries
                            .latestForComponent(component.id)
                            .executeAsOneOrNull()
                    latest != null && latest.at >= since
                }
            }

    override suspend fun ensureBackgroundCapture(reason: String) {
        val hasBike = db.bikeQueries.selectAll().executeAsList().isNotEmpty()
        if (!hasBike) {
            scanner.stopBackground()
            return
        }
        val result = scanner.startBackground(BACKGROUND_PROFILE)
        if (result != ArmResult.Armed)
            println("CassetteScan: background scan not armed ($reason): $result")
    }

    /** One background wake: record what was heard and read batteries that are due. */
    suspend fun onBackgroundDelivery(readings: List<AxsReading>) {
        val heard = recorder.record(readings)
        refresher.refresh(heard, CaptureMode.Background)
    }

    private companion object {
        val REFRESH_LENGTH = 60.seconds
        /**
         * Validated on two rides (no gaps over 46 s); ride tests compare the others (decision
         * 0009).
         */
        val BACKGROUND_PROFILE = ScanProfile.AllLowPower
    }
}

@ContributesBinding(AppScope::class)
@Inject
class CaptureBackgroundDeliveryHandler(private val controller: DefaultCaptureController) :
    BackgroundDeliveryHandler {
    override suspend fun onDelivery(
        readings: List<com.r0adkll.cassette.protocol.AxsReading>,
        errorCode: Int,
    ) = controller.onBackgroundDelivery(readings)
}
