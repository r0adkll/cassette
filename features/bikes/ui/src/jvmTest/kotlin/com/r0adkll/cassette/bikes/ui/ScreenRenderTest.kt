package com.r0adkll.cassette.bikes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.r0adkll.cassette.bikes.ui.add.AddBikeStep
import com.r0adkll.cassette.bikes.ui.add.AddBikeUi
import com.r0adkll.cassette.bikes.ui.add.AddBikeUiState
import com.r0adkll.cassette.bikes.ui.add.FoundRow
import com.r0adkll.cassette.bikes.ui.add.MeterRow
import com.r0adkll.cassette.bikes.ui.component.ComponentDetailModel
import com.r0adkll.cassette.bikes.ui.component.ComponentUi
import com.r0adkll.cassette.bikes.ui.component.ComponentUiState
import com.r0adkll.cassette.bikes.ui.component.HistoryRange
import com.r0adkll.cassette.bikes.ui.picker.BikeOption
import com.r0adkll.cassette.bikes.ui.picker.BikePicker
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.core.BikeType
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/** Renders the add-bike steps to `build/screens/`, light and dark, for review without a device. */
class ScreenRenderTest {
  private val out = File("build/screens").apply { mkdirs() }

  @Test
  fun addBikeSteps() {
    val rows =
      listOf(
        FoundRow("RD", "Rear derailleur", "Hub · reported", "78%", StatusTone.Good),
        FoundRow("FD", "Front derailleur", "Reported by the hub", "≈97%", StatusTone.Good),
        FoundRow("R", "Right shifter", "Reported by the hub", "2.94 V", StatusTone.Good),
        FoundRow("L", "Left shifter", "Reported by the hub", "2.95 V", StatusTone.Good),
      )
    val steps =
      listOf(
        "1-wake" to AddBikeStep.Wake(12, bluetoothReady = true, showTips = false),
        "2-found" to
          AddBikeStep.Found(
            1405188718,
            reading = false,
            components = rows,
            failed = false,
          ),
        "3-name" to AddBikeStep.Name("Road bike", BikeType.Road),
        "4-power" to
          AddBikeStep.PowerMeter(
            listOf(
              MeterRow("Quarq AGN12387", "Strong signal", 4),
              MeterRow("Quarq AGN20911", "Weak signal", 1),
            ),
            selected = "Quarq AGN12387",
          ),
        "5-fresh" to AddBikeStep.KeepFresh(bluetoothReady = true, saving = false),
      )
    for (dark in listOf(true, false)) {
      for ((name, step) in steps) {
        render("add-bike-$name-${if (dark) "dark" else "light"}", dark) {
          AddBikeUi(AddBikeUiState(step) {})
        }
      }
    }
  }

  @Test
  fun componentScreen() {
    val levels =
      listOf(62, 58, 55, 51, 48, 44, 41, 37, 98, 97, 95, 93, 91, 89, 88, 86, 84, 83, 81, 80, 78)
    val model =
      ComponentDetailModel(
        bikeName = "Road bike",
        name = "Rear derailleur",
        value = "78%",
        headline = "Good.",
        source = "Reported by the hub, 2 min ago",
        level = 0.78f,
        tone = StatusTone.Good,
        stats =
          listOf(
            "Voltage" to "7.87 V",
            "Shifts" to "12,324",
            "Firmware" to "2.55.8",
            "Model" to "1066",
            "Serial" to "1405188718",
            "Last read" to "2 min",
          ),
        range = HistoryRange.Month,
        chart = levels.map { it / 100f },
        chartCaption = "Charged 12 days ago (sample)",
      )
    for (dark in listOf(true, false)) {
      render("component-${if (dark) "dark" else "light"}", dark) {
        ComponentUi(ComponentUiState(model) {})
      }
    }
  }

  @Test
  fun widgetBikePicker() {
    val options =
      listOf(
        BikeOption(null, "Default bike", "Follows Settings · now Road bike", null),
        BikeOption(1, "Road bike", "Charge soon. Seen 2 min ago", StatusTone.Low),
        BikeOption(2, "Gravel bike", "All good. Seen 3 days ago", StatusTone.Good),
      )
    for (dark in listOf(true, false)) {
      render("widget-bike-picker-${if (dark) "dark" else "light"}", dark) {
        BikePicker(options, selected = 2, onPick = {}, onClose = {})
      }
    }
  }

  private fun render(name: String, dark: Boolean, content: @Composable () -> Unit) {
    val scene =
      ImageComposeScene(width = 412 * 2, height = 892 * 2, density = Density(2f)) {
        CassetteTheme(darkTheme = dark) {
          Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) {
            content()
          }
        }
      }
    repeat(4) { scene.render(it * 16_000_000L) }
    File(out, "$name.png")
      .writeBytes(scene.render(100_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
  }
}
