package com.r0adkll.cassette.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class HistoryTest {
  private val start = Instant.fromEpochMilliseconds(0)

  private fun pack(day: Int, percent: Int) =
    BatteryReading(ComponentId(1), start + day.days, 7_800, percent, null, ReadMethod.Advert)

  private fun coin(day: Int, mV: Int) =
    BatteryReading(ComponentId(1), start + day.days, mV, null, null, ReadMethod.Hub)

  @Test
  fun aPackChargeIsAJumpInLevel() {
    val history = listOf(pack(0, 62), pack(5, 41), pack(6, 37), pack(7, 98), pack(12, 90))
    assertEquals(listOf(start + 7.days), charges(history).map { it.at })
  }

  @Test
  fun aCoinCellSwapIsAJumpInVoltage() {
    val history = listOf(coin(0, 2_600), coin(30, 2_380), coin(31, 2_950), coin(40, 2_930))
    assertEquals(listOf(start + 31.days), charges(history).map { it.at })
  }

  @Test
  fun noiseIsNotACharge() {
    val history = listOf(pack(0, 78), pack(1, 81), pack(2, 77), coin(3, 2_900), coin(4, 2_950))
    assertEquals(emptyList(), charges(history.take(3)))
    assertEquals(emptyList(), charges(history.drop(3)))
  }
}
