package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.core.Component
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.data.db.toDomain
import com.r0adkll.cassette.protocol.AxsReading
import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.protocol.ComponentVersions
import com.r0adkll.cassette.protocol.SystemComponent
import com.r0adkll.cassette.protocol.SystemTable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A reading matched to a component of a known bike. */
data class Heard(val reading: AxsReading, val component: Component, val bike: Bike)

/**
 * Writes what we hear into the database. Only components of known bikes are recorded; everything else
 * is someone else's bike (add-bike discovers new ones from the live scan).
 */
@SingleIn(AppScope::class)
@Inject
class CaptureRecorder(private val db: CassetteDatabase, private val dispatchers: CassetteDispatchers) {
    private val lastWrite = mutableMapOf<Long, Long>()
    private val lock = Mutex()

    suspend fun record(readings: List<AxsReading>): List<Heard> =
        withContext(dispatchers.io) { lock.withLock { readings.mapNotNull(::recordOne) } }

    private fun recordOne(reading: AxsReading): Heard? {
        val row = findComponent(reading) ?: return null
        val bike = db.bikeQueries.selectById(row.bike_id).executeAsOneOrNull() ?: return null
        val at = reading.timestampMillis
        // Components advertise several times a second; one write per component every few seconds.
        val recent = lastWrite[row.id]?.let { at - it < WRITE_INTERVAL.inWholeMilliseconds } == true
        if (!recent) {
            db.transaction {
                db.componentQueries.markSeen(
                    at = at,
                    // Not every frame says everything (beacons have no device type); keep what we know.
                    kind = reading.kind.takeIf { it != ComponentKind.UNKNOWN }?.name,
                    modelId = reading.modelId?.toLong(),
                    deviceType = reading.classByte?.toLong(),
                    firmware = reading.firmware,
                    actuations = null,
                    id = row.id,
                )
                if (reading.address.isNotEmpty() && row.address != reading.address) {
                    db.componentQueries.setAddress(reading.address, row.id)
                }
                db.bikeQueries.markSeen(at, bike.id)
                reading.battery?.let {
                    insertSample(row.id, at, it.millivolts, it.percent, it.reportedStatus, ReadMethod.Advert)
                }
            }
            lastWrite[row.id] = at
        }
        val component = db.componentQueries.selectByKey(row.key).executeAsOne().toDomain()
        val freshBike = db.bikeQueries.selectById(bike.id).executeAsOne().toDomain()
        return Heard(reading, component, freshBike)
    }

    /**
     * By serial (or a Quarq's name). Frames without a serial are keyed by address, so they resolve to
     * the component last heard at that address.
     */
    private fun findComponent(reading: AxsReading) =
        db.componentQueries.selectByKey(reading.key).executeAsOneOrNull()
            ?: reading.address.takeIf { reading.serial == null && it.isNotEmpty() }?.let {
                db.componentQueries.selectByAddress(it).executeAsList().firstOrNull()
            }

    /**
     * Applies the hub's tables (decision 0010): every listed component is seen, new ones join the bike,
     * and components the hub hasn't listed for 30 days are marked removed. The hub's own row skips
     * battery, since its adverts carry voltage, percent and status.
     */
    suspend fun applyHubTable(
        bike: Bike,
        table: SystemTable<SystemComponent>,
        versions: SystemTable<ComponentVersions>?,
        at: Instant,
    ) =
        withContext(dispatchers.io) {
            lock.withLock {
                val now = at.toEpochMilliseconds()
                val firmware = versions?.rows?.associate { it.serial to it.firmware }.orEmpty()
                db.transaction {
                    for (row in table.rows) {
                        val key = row.serial.toString()
                        val existing = db.componentQueries.selectByKey(key).executeAsOneOrNull()
                        val id =
                            if (existing == null) {
                                db.componentQueries.insert(
                                    bike.id.value,
                                    key,
                                    row.serial,
                                    row.kind.name,
                                    row.modelId.toLong(),
                                    row.deviceType.toLong(),
                                    firmware[row.serial],
                                    row.actuations,
                                    now,
                                    now,
                                )
                                db.componentQueries.lastInsertedId().executeAsOne()
                            } else if (existing.bike_id != bike.id.value) {
                                // Listed by another bike's hub; leave it where the user put it.
                                continue
                            } else {
                                db.componentQueries.markSeen(
                                    at = now,
                                    kind = row.kind.takeIf { it != ComponentKind.UNKNOWN }?.name,
                                    modelId = row.modelId.toLong(),
                                    deviceType = row.deviceType.toLong(),
                                    firmware = firmware[row.serial],
                                    actuations = row.actuations,
                                    id = existing.id,
                                )
                                existing.id
                            }
                        if (row.serial != table.masterSerial) {
                            insertSample(id, now, row.millivolts, null, row.reportedStatus, ReadMethod.Hub)
                        }
                    }
                    db.bikeQueries.markHubRead(now, bike.id.value)
                    db.bikeQueries.markSeen(now, bike.id.value)
                    db.componentQueries.markMissingRemoved(
                        at = now,
                        bikeId = bike.id.value,
                        cutoff = (at - REMOVED_AFTER).toEpochMilliseconds(),
                    )
                }
            }
        }

    /** A power meter's standard Battery Level. */
    suspend fun applyBatteryLevel(component: Component, percent: Int, at: Instant) =
        withContext(dispatchers.io) {
            lock.withLock {
                insertSample(component.id.value, at.toEpochMilliseconds(), null, percent, null, ReadMethod.Gatt)
            }
        }

    /**
     * History keeps a reading per component every 10 minutes, plus any status change, so the open app
     * scanning every second doesn't flood it.
     */
    private fun insertSample(
        componentId: Long,
        at: Long,
        millivolts: Int?,
        percent: Int?,
        status: BatteryStatus?,
        method: ReadMethod,
    ) {
        val last = db.batterySampleQueries.latestForComponent(componentId).executeAsOneOrNull()
        val due =
            last == null ||
                at - last.at >= SAMPLE_INTERVAL.inWholeMilliseconds ||
                last.reported_status != status?.name ||
                last.method != method.name
        if (!due || (last != null && at < last.at)) return
        db.batterySampleQueries.insert(
            componentId,
            at,
            millivolts?.toLong(),
            percent?.toLong(),
            status?.name,
            method.name,
        )
    }

    private companion object {
        val WRITE_INTERVAL = 5.seconds
        val SAMPLE_INTERVAL = 10.minutes
        val REMOVED_AFTER = 30.days
    }
}
