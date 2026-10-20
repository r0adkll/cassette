package com.r0adkll.cassette.bikes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.r0adkll.cassette.bikes.summary.BikeStatus
import com.r0adkll.cassette.bikes.summary.ComponentRowModel
import com.r0adkll.cassette.bikes.ui.add.AddBikeStep
import com.r0adkll.cassette.bikes.ui.add.AddBikeUi
import com.r0adkll.cassette.bikes.ui.add.AddBikeUiState
import com.r0adkll.cassette.bikes.ui.component.ComponentDetailModel
import com.r0adkll.cassette.bikes.ui.component.ComponentUi
import com.r0adkll.cassette.bikes.ui.component.ComponentUiState
import com.r0adkll.cassette.bikes.ui.component.HistoryRange
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.currentScreenLayout
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders the Play listing's Android screens to `build/store/`, dark, at the sizes
 * `scripts/store-assets` lays out: the phone ones get a caption there, the tablet ones go up as
 * they are. No SRAM names in the sample data (CLAUDE.md).
 */
class StoreRenderTest {
  private val out = File("build/store").apply { mkdirs() }

  private val rows =
    listOf(
      ComponentRowModel(1, "RD", "Rear derailleur", "Reported · fw 2.55.8", "78%", 0.78f, Good),
      ComponentRowModel(2, "FD", "Front derailleur", "Estimated from 8.09 V", "≈96%", 0.96f, Good),
      ComponentRowModel(3, "L", "Left shifter", "CR2032 coin cell", "2.91 V", 0.89f, Good),
      ComponentRowModel(4, "R", "Right shifter", "CR2032 coin cell", "2.89 V", 0.86f, Good),
      ComponentRowModel(5, "PM", "Power meter", "Reported by the hub", "85%", 0.85f, Good),
    )

  private val bike =
    BikeUiState.Loaded(
      bikeId = 1,
      name = "Road bike",
      position = "1 OF 2",
      status =
        BikeStatus(
          Good,
          "All good.",
          "Next to charge: rear derailleur, in about 3 weeks (est.)",
          "Seen 2 min ago",
        ),
      title = "Full tape. Press play.",
      components = rows,
      bikes =
        listOf(
          BikeChoice(1, "Road bike", Good, "2 min ago"),
          BikeChoice(2, "Gravel bike", StatusTone.Low, "3 days ago"),
        ),
      trends = rows.associate { it.id to trendTo(it.level) },
    ) {}

  // The front derailleur is low, so it takes the wide tile.
  private val low =
    bike.copy(
      status =
        BikeStatus(
          StatusTone.Low,
          "Charge soon.",
          "Front derailleur at 18%, about 5 days left (est.)",
          "Seen 2 min ago",
        ),
      title = "Charge soon.",
      components =
        rows.map {
          if (it.code == "FD") {
            it.copy(value = "18%", level = 0.18f, tone = StatusTone.Low)
          } else {
            it
          }
        },
      trends = rows.associate { it.id to trendTo(if (it.code == "FD") 0.18f else it.level) },
    )

  private val component =
    ComponentDetailModel(
      bikeName = "Road bike",
      name = "Rear derailleur",
      value = "78%",
      headline = "Good.",
      source = "Reported by the hub, 2 min ago",
      level = 0.78f,
      tone = Good,
      stats =
        listOf(
          "Voltage" to "7.87 V",
          "Shifts" to "12,324",
          "Firmware" to "2.55.8",
          "Last read" to "2 min",
        ),
      range = HistoryRange.Month,
      chart =
        listOf(62, 58, 55, 51, 48, 44, 41, 37, 98, 97, 95, 93, 91, 89, 88, 86, 84, 83, 81, 80, 78)
          .map { it / 100f },
      chartCaption = "Charged 12 days ago",
    )

  // Waking, not Found: the found step shows the hub's maker and serial.
  private val wake = AddBikeStep.Wake(38, bluetoothReady = true, showTips = false)

  @Test
  fun phone() {
    render("phone-1-bike", Phone) { BikeUi(bike) }
    render("phone-2-low", Phone) { BikeUi(low) }
    render("phone-3-component", Phone) { ComponentUi(ComponentUiState(component) {}) }
    render("phone-4-add-bike", Phone, atMillis = 1_200) { AddBikeUi(AddBikeUiState(wake) {}) }
  }

  /**
   * 7": portrait, where the bike screen fits (its add-bike step splits, half empty, so it's left
   * out). 10": landscape with the bike rail, then portrait.
   */
  @Test
  fun tablets() {
    render("seven-1-bike", SevenInch) { BikeUi(bike) }
    render("seven-2-low", SevenInch) { BikeUi(low) }
    render("seven-3-component", SevenInch) { ComponentUi(ComponentUiState(component) {}) }
    render("seven-4-sheet", SevenInch) { sheet(rail = false) }
    render("ten-1-bike", TenInch) { BikeUi(bike) }
    render("ten-2-low", TenInch) { BikeUi(low) }
    render("ten-3-sheet", TenInch) { sheet(rail = true) }
    render("ten-4-bike-portrait", TenInchPortrait) { BikeUi(bike) }
  }

  /** The component sheet open on the rear derailleur, over the dashboard. */
  @Composable
  private fun sheet(rail: Boolean) {
    BikeDashboard(
      bike.copy(sheet = 1),
      rail = rail,
      sheetContent = { ComponentUi(ComponentUiState(component, inSheet = true) {}) },
    )
  }

  private fun render(
    name: String,
    size: StoreSize,
    atMillis: Long = 100,
    content: @Composable () -> Unit,
  ) {
    val scene =
      ImageComposeScene(size.width, size.height, density = Density(size.density)) {
        CassetteTheme(darkTheme = true) {
          CompositionLocalProvider(LocalScreenLayout provides currentScreenLayout()) {
            Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) { content() }
          }
        }
      }
    repeat(4) { scene.render(it * 16_000_000L) }
    (100L..atMillis step 100).forEach { scene.render(it * 1_000_000L) }
    File(out, "$name.png")
      .writeBytes(scene.render(atMillis * 1_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
  }

  /** Thirty days falling to [level], with a charge on day 8. */
  private fun trendTo(level: Float): List<Float> =
    listOf(0.62f, 0.58f, 0.55f, 0.51f, 0.48f, 0.44f, 0.41f, 0.37f) +
      (0..12).map { 0.99f + (level - 0.99f) * it / 12 }

  /** A window in pixels and its density. */
  private class StoreSize(val width: Int, val height: Int, val density: Float)

  private companion object {
    val Good = StatusTone.Good

    /** The phone screen the captioned shots crop from: 412 × 892 dp. */
    val Phone = StoreSize(1236, 2676, 3f)

    /** Play's 9:16 and 16:9 tablet shots: 600 × 1067 dp, 1280 × 720 dp and 800 × 1422 dp. */
    val SevenInch = StoreSize(1080, 1920, 1.8f)
    val TenInch = StoreSize(2560, 1440, 2f)
    val TenInchPortrait = StoreSize(1440, 2560, 1.8f)
  }
}
