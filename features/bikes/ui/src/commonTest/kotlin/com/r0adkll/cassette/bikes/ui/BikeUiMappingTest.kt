package com.r0adkll.cassette.bikes.ui

import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.ComponentWithReading
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.core.BatteryReading
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.Component
import com.r0adkll.cassette.core.ComponentId
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.ComponentKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class BikeUiMappingTest {
    private val now = Instant.fromEpochMilliseconds(1_792_200_000_000)

    @Test
    fun goodBikeShowsLowestComponentAndValues() {
        val state =
            overview(
                    component(
                        1,
                        ComponentKind.REAR_DERAILLEUR,
                        mV = 7_870,
                        percent = 78,
                        status = BatteryStatus.OK,
                        firmware = "2.55.8",
                    ),
                    component(
                        2,
                        ComponentKind.FRONT_DERAILLEUR,
                        mV = 8_090,
                        status = BatteryStatus.OK,
                    ),
                    component(3, ComponentKind.LEFT_SHIFTER, mV = 2_910, status = BatteryStatus.OK),
                    component(4, ComponentKind.POWER_METER, percent = 85, method = ReadMethod.Gatt),
                )
                .toLoaded(now) {}

        assertEquals(StatusTone.Good, state.status.tone)
        assertEquals("All good.", state.status.headline)
        assertEquals("Lowest: rear derailleur, 78%.", state.status.summary)
        assertEquals("Seen 2 min ago", state.status.meta)
        assertEquals(listOf("78%", "≈97%", "2.91 V", "85%"), state.components.map { it.value })
        assertEquals(
            listOf(
                "Reported · fw 2.55.8",
                "Estimated from 8.09 V",
                "Coin cell",
                "Reported by the power meter",
            ),
            state.components.map { it.source },
        )
        assertEquals(listOf("RD", "FD", "L", "PM"), state.components.map { it.code })
    }

    @Test
    fun worstStatusWins() {
        val state =
            overview(
                    component(
                        1,
                        ComponentKind.REAR_DERAILLEUR,
                        mV = 7_200,
                        percent = 4,
                        status = BatteryStatus.CRITICAL,
                    ),
                    component(
                        2,
                        ComponentKind.FRONT_DERAILLEUR,
                        mV = 7_460,
                        status = BatteryStatus.LOW,
                    ),
                )
                .toLoaded(now) {}

        assertEquals(StatusTone.Critical, state.status.tone)
        assertEquals("Charge now.", state.status.headline)
        assertEquals("Rear derailleur is critical, 4%.", state.status.summary)
        assertEquals(listOf(StatusTone.Critical, StatusTone.Low), state.components.map { it.tone })
    }

    @Test
    fun staleBikeGreysEverything() {
        val state =
            overview(
                    component(
                        1,
                        ComponentKind.REAR_DERAILLEUR,
                        mV = 7_870,
                        percent = 78,
                        status = BatteryStatus.OK,
                    ),
                    seen = now - 16.days,
                )
                .toLoaded(now) {}

        assertEquals(StatusTone.Stale, state.status.tone)
        assertEquals("Not seen in 16 days.", state.status.headline)
        assertEquals(StatusTone.Stale, state.components.single().tone)
        assertEquals("78%", state.components.single().value)
    }

    @Test
    fun componentsWithoutReadingsAreUnknown() {
        val state = overview(component(1, ComponentKind.RIGHT_SHIFTER)).toLoaded(now) {}

        assertEquals(StatusTone.Unknown, state.status.tone)
        assertEquals("—", state.components.single().value)
        assertEquals("No reading yet", state.components.single().source)
    }

    @Test
    fun relativeTimes() {
        assertEquals("just now", ago(30.minutes / 60))
        assertEquals("5 min ago", ago(5.minutes))
        assertEquals("3 h ago", ago(180.minutes))
        assertEquals("yesterday", ago(1.days))
        assertEquals("4 days ago", ago(4.days))
    }

    private fun overview(vararg components: ComponentWithReading, seen: Instant = now - 2.minutes) =
        BikeOverview(
            bike =
                Bike(BikeId(1), "Road bike", BikeType.Road, null, 1405188718, now - 10.days, seen),
            bikeCount = 1,
            components = components.toList(),
        )

    private fun component(
        id: Long,
        kind: ComponentKind,
        mV: Int? = null,
        percent: Int? = null,
        status: BatteryStatus? = null,
        method: ReadMethod = ReadMethod.Hub,
        firmware: String? = null,
    ): ComponentWithReading {
        val component =
            Component(
                ComponentId(id),
                BikeId(1),
                "$id",
                id,
                kind,
                null,
                null,
                firmware,
                null,
                now,
                now,
                null,
            )
        val reading =
            if (mV == null && percent == null) null
            else BatteryReading(ComponentId(id), now - 2.minutes, mV, percent, status, method)
        return ComponentWithReading(component, reading)
    }
}
