package com.r0adkll.cassette.bikes.ui

import com.r0adkll.cassette.bikes.api.ComponentDetail
import com.r0adkll.cassette.bikes.ui.component.HistoryRange
import com.r0adkll.cassette.bikes.ui.component.toModel
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

class ComponentMappingTest {
  private val now = Instant.fromEpochMilliseconds(1_792_200_000_000)

  @Test
  fun rearDerailleurWithACharge() {
    val history =
      listOf(40, 35, 98, 90, 78).mapIndexed { i, p -> reading(now - (12 - i * 3).days, 7_870, p) }
    val model =
      detail(ComponentKind.REAR_DERAILLEUR, history.last().copy(at = now - 2.minutes), history)
        .toModel(HistoryRange.Month, now)

    assertEquals("78%", model.value)
    assertEquals("Good.", model.headline)
    assertEquals(StatusTone.Good, model.tone)
    assertEquals("Charged 6 days ago", model.chartCaption)
    assertEquals(
      listOf(
        "Voltage" to "7.87 V",
        "Shifts" to "12,324",
        "Firmware" to "2.55.8",
        "Model" to "1066",
        "Serial" to "1405188718",
        "Last read" to "2 min",
      ),
      model.stats,
    )
  }

  @Test
  fun coinCellSwapAndStaleness() {
    val history = listOf(reading(now - 40.days, 2_380), reading(now - 20.days, 2_950))
    val model =
      detail(ComponentKind.LEFT_SHIFTER, history.last(), history).toModel(HistoryRange.Quarter, now)

    assertEquals("2.95 V", model.value)
    assertEquals(StatusTone.Stale, model.tone)
    assertEquals("Battery replaced 20 days ago", model.chartCaption)
  }

  @Test
  fun noReadingYet() {
    val model =
      detail(ComponentKind.FRONT_DERAILLEUR, null, emptyList()).toModel(HistoryRange.Month, now)
    assertEquals(StatusTone.Unknown, model.tone)
    assertEquals("—", model.value)
    assertEquals("History builds as Cassette hears the bike.", model.chartCaption)
  }

  private fun reading(at: Instant, mV: Int, percent: Int? = null) =
    BatteryReading(ComponentId(1), at, mV, percent, BatteryStatus.OK, ReadMethod.Hub)

  private fun detail(kind: ComponentKind, latest: BatteryReading?, history: List<BatteryReading>) =
    ComponentDetail(
      component =
        Component(
          ComponentId(1),
          BikeId(1),
          "1405188718",
          1405188718,
          kind,
          1066,
          0x81,
          "2.55.8",
          12_324,
          now,
          now,
          null,
        ),
      bike = Bike(BikeId(1), "Road bike", BikeType.Road, null, 1405188718, now, now),
      latest = latest,
      history = history,
    )
}
