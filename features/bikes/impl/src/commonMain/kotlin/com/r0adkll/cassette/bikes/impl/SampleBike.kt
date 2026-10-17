package com.r0adkll.cassette.bikes.impl

import com.r0adkll.cassette.bikes.api.DeveloperTools
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.ComponentKind
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
@Inject
class DatabaseDeveloperTools(
    private val db: CassetteDatabase,
    private val dispatchers: CassetteDispatchers,
    private val clock: Clock,
) : DeveloperTools {

    override suspend fun loadSampleBike() =
        withContext(dispatchers.io) {
            val seen = (clock.now() - 2.minutes).toEpochMilliseconds()
            db.transaction {
                db.bikeQueries.selectByHubSerial(SAMPLE_HUB).executeAsOneOrNull()?.let {
                    db.bikeQueries.delete(it.id)
                }
                db.bikeQueries.insert("Road bike", BikeType.Road.name, null, SAMPLE_HUB, seen, seen)
                val bike = db.bikeQueries.lastInsertedId().executeAsOne()
                SAMPLE_COMPONENTS.forEach { c ->
                    db.componentQueries.insert(
                        bike,
                        c.key,
                        c.key.toLongOrNull(),
                        c.kind.name,
                        c.modelId,
                        c.deviceType,
                        c.firmware,
                        null,
                        seen,
                        seen,
                    )
                    val component = db.componentQueries.lastInsertedId().executeAsOne()
                    db.batterySampleQueries.insert(
                        component,
                        seen,
                        c.millivolts,
                        c.percent,
                        c.status?.name,
                        c.method.name,
                    )
                }
            }
        }

    private class Sample(
        val key: String,
        val kind: ComponentKind,
        val modelId: Long?,
        val deviceType: Long?,
        val firmware: String?,
        val millivolts: Long?,
        val percent: Long?,
        val status: BatteryStatus?,
        val method: ReadMethod,
    )

    private companion object {
        const val SAMPLE_HUB = 1405188718L

        /** The validation bike (Red AXS) as read on 2026-10-16. */
        val SAMPLE_COMPONENTS =
            listOf(
                Sample(
                    "1405188718",
                    ComponentKind.REAR_DERAILLEUR,
                    1066,
                    0x81,
                    "2.55.8",
                    7_870,
                    78,
                    BatteryStatus.OK,
                    ReadMethod.Hub,
                ),
                Sample(
                    "1315137227",
                    ComponentKind.FRONT_DERAILLEUR,
                    1002,
                    0x80,
                    "2.7.3",
                    8_090,
                    null,
                    BatteryStatus.OK,
                    ReadMethod.Hub,
                ),
                Sample(
                    "1132146225",
                    ComponentKind.LEFT_SHIFTER,
                    1005,
                    0x00,
                    "2.7.3",
                    2_910,
                    null,
                    BatteryStatus.OK,
                    ReadMethod.Hub,
                ),
                Sample(
                    "1132134130",
                    ComponentKind.RIGHT_SHIFTER,
                    1004,
                    0x01,
                    "2.7.3",
                    2_890,
                    null,
                    BatteryStatus.OK,
                    ReadMethod.Hub,
                ),
                Sample(
                    "Quarq AGN12387",
                    ComponentKind.POWER_METER,
                    7,
                    null,
                    null,
                    null,
                    85,
                    null,
                    ReadMethod.Gatt,
                ),
            )
    }
}
