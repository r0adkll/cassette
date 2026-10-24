package com.r0adkll.cassette.surfaces.tile

import com.r0adkll.cassette.bikes.summary.BikeStatus
import com.r0adkll.cassette.bikes.summary.ComponentRowModel
import com.r0adkll.cassette.bikes.ui.BikeUiState
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.common.compose.theme.StatusTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TileViewTest {
  private fun bike(
    tone: StatusTone,
    headline: String,
    meta: String = "Seen 2 min ago",
    vararg rows: ComponentRowModel,
  ) =
    BikeUiState.Loaded(
      1,
      "Road bike",
      BikeStatus(tone, headline, "summary", meta),
      rows.toList(),
    ) {}

  private fun row(code: String, value: String, level: Float, tone: StatusTone) =
    ComponentRowModel(0, code, code, "", value, level, tone)

  @Test
  fun allGood() {
    val view =
      tileView(
        bike(
          StatusTone.Good,
          "All good.",
          rows = arrayOf(row("RD", "78%", 0.78f, StatusTone.Good)),
        ),
        null,
      )
    assertEquals("Road bike", view.label)
    assertEquals("All good · 2 min", view.subtitle)
    assertTrue(view.active)
    assertEquals(R.drawable.lucide_circle_check, view.icon)
  }

  @Test
  fun lowNamesTheComponent() {
    val view =
      tileView(
        bike(
          StatusTone.Low,
          "Charge soon.",
          rows =
            arrayOf(
              row("RD", "78%", 0.78f, StatusTone.Good),
              row("FD", "18%", 0.18f, StatusTone.Low),
            ),
        ),
        null,
      )
    assertEquals("FD 18% · 2 min", view.subtitle)
    assertEquals(R.drawable.lucide_battery_low, view.icon)
  }

  @Test
  fun refreshingCountsDown() {
    val view =
      tileView(
        bike(StatusTone.Good, "All good."),
        RefreshProgress.Scanning(secondsLeft = 42, componentsHeard = 0),
      )
    assertEquals("Wake the bike · 0:42", view.subtitle)
  }

  @Test
  fun staleIsInactive() {
    val view =
      tileView(bike(StatusTone.Stale, "Not seen in 16 days.", meta = "Seen 16 days ago"), null)
    assertEquals("Seen 16 days ago", view.subtitle)
    assertFalse(view.active)
  }

  @Test
  fun noBike() {
    val view = tileView(null, null)
    assertEquals("No bike yet", view.subtitle)
    assertFalse(view.active)
  }
}
