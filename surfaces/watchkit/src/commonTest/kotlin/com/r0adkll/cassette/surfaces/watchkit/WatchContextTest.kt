package com.r0adkll.cassette.surfaces.watchkit

import com.r0adkll.cassette.surfaces.wear.sync.WearCodec
import com.r0adkll.cassette.surfaces.wear.sync.WearRefresh
import com.r0adkll.cassette.surfaces.widgetkit.WidgetBike
import com.r0adkll.cassette.surfaces.widgetkit.WidgetCodec
import com.r0adkll.cassette.surfaces.widgetkit.WidgetComponent
import com.r0adkll.cassette.surfaces.widgetkit.WidgetSnapshot
import com.r0adkll.cassette.surfaces.widgetkit.WidgetStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WatchContextTest {
  private fun snapshot(value: String, level: Float, tone: String = "Good", pm: String = "92%") =
    WidgetSnapshot(
      writtenAt = 1,
      defaultBikeId = 1,
      bikes =
        listOf(
          WidgetBike(
            id = 1,
            name = "Road bike",
            lastSeenAt = 1,
            current =
              WidgetStatus(
                tone = tone,
                word = "GOOD",
                headline = "All good.",
                summary = "",
                weakestId = 2,
                components =
                  listOf(
                    WidgetComponent(2, "RD", "Rear derailleur", value, level, tone, "GOOD"),
                    WidgetComponent(3, "PM", "Power meter", pm, 0.92f, "Good", "GOOD"),
                  ),
              ),
          )
        ),
    )

  @Test
  fun carriesTheSnapshotAndRefreshAsJson() {
    val snapshot = snapshot("78%", 0.78f)
    val refresh = WearRefresh(WearRefresh.State.Scanning, until = 60_000, at = 0)
    val context = WatchContext.build(snapshot, refresh)

    assertEquals(snapshot, WidgetCodec.decode(context.getValue(WatchContext.SNAPSHOT)))
    assertEquals(
      refresh,
      WearCodec.decodeRefresh(context.getValue(WatchContext.REFRESH).encodeToByteArray()),
    )
    assertEquals(setOf(WatchContext.SNAPSHOT), WatchContext.build(snapshot, null).keys)
  }

  @Test
  fun complicationsWakeOnlyForWhatTheyShow() {
    val before = WatchContext.complicationKey(snapshot("78%", 0.78f))
    // A new reading at the same value is no reason to spend the day's budget.
    assertEquals(before, WatchContext.complicationKey(snapshot("78%", 0.781f)))
    assertNotEquals(before, WatchContext.complicationKey(snapshot("77%", 0.77f)))
    assertNotEquals(before, WatchContext.complicationKey(snapshot("78%", 0.78f, tone = "Low")))
    // A complication can pick a component that isn't the weakest.
    assertNotEquals(before, WatchContext.complicationKey(snapshot("78%", 0.78f, pm = "91%")))
  }
}
