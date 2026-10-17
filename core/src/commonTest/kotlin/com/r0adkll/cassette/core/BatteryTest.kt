package com.r0adkll.cassette.core

import com.r0adkll.cassette.protocol.BatteryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class BatteryTest {
    private fun reading(mV: Int?, percent: Int? = null, reported: BatteryStatus? = null) =
        BatteryReading(
            ComponentId(1),
            Instant.fromEpochMilliseconds(0),
            mV,
            percent,
            reported,
            ReadMethod.Hub,
        )

    @Test
    fun reportedStatusWinsOverOurs() {
        assertEquals(BatteryStatus.LOW, reading(mV = 8_100, reported = BatteryStatus.LOW).status)
        assertEquals(BatteryStatus.OK, reading(mV = 8_100).status)
    }

    @Test
    fun packsGetAnEstimateAndCoinCellsDoNot() {
        val pack = reading(mV = 8_118)
        assertTrue(pack.isEstimate)
        assertEquals(100, pack.displayPercent)

        val coin = reading(mV = 2_910)
        assertFalse(coin.isEstimate)
        assertNull(coin.displayPercent)
        assertEquals(0.91f, coin.level, 0.001f)

        val reported = reading(mV = 7_870, percent = 78)
        assertFalse(reported.isEstimate)
        assertEquals(0.78f, reported.level, 0.001f)
    }

    @Test
    fun staleAfter14Days() {
        val now = Instant.fromEpochMilliseconds(0) + 30.days
        assertTrue(isStale(null, now))
        assertTrue(isStale(now - 15.days, now))
        assertFalse(isStale(now - 13.days, now))
    }
}
