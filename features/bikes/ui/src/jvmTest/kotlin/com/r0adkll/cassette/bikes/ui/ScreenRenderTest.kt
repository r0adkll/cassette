package com.r0adkll.cassette.bikes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.bikes.summary.BikeStatus
import com.r0adkll.cassette.bikes.summary.ComponentRowModel
import com.r0adkll.cassette.bikes.ui.add.AddBikeStep
import com.r0adkll.cassette.bikes.ui.add.AddBikeUi
import com.r0adkll.cassette.bikes.ui.add.AddBikeUiState
import com.r0adkll.cassette.bikes.ui.add.FoundRow
import com.r0adkll.cassette.bikes.ui.add.MeterRow
import com.r0adkll.cassette.bikes.ui.add.WakeAndFound
import com.r0adkll.cassette.bikes.ui.component.ComponentDetailModel
import com.r0adkll.cassette.bikes.ui.component.ComponentUi
import com.r0adkll.cassette.bikes.ui.component.ComponentUiState
import com.r0adkll.cassette.bikes.ui.component.HistoryRange
import com.r0adkll.cassette.bikes.ui.picker.BikeOption
import com.r0adkll.cassette.bikes.ui.picker.BikePicker
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.currentScreenLayout
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.settings.api.Tip
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
      // Unfolded, waking and found share a screen split at the hinge (decision 0014).
      val fold = RenderSize.Fold
      val hinge = Rect(fold.width.toFloat(), 0f, fold.width.toFloat(), fold.height * 2f)
      val wide =
        listOf(
          "1-wake" to AddBikeStep.Wake(38, bluetoothReady = true, showTips = false),
          "1-wake-tips" to AddBikeStep.Wake(38, bluetoothReady = true, showTips = true),
          "2-reading" to AddBikeStep.Found(1405188718, true, rows.take(2), failed = false),
          "2-found" to AddBikeStep.Found(1405188718, false, rows, failed = false),
        )
      for ((name, step) in wide) {
        render("add-bike-fold-$name-${if (dark) "dark" else "light"}", dark, fold) {
          WakeAndFound(AddBikeUiState(step) {}, hinge = hinge)
        }
      }
    }
  }

  @Test
  fun componentScreen() {
    for (dark in listOf(true, false)) {
      render("component-${if (dark) "dark" else "light"}", dark) {
        ComponentUi(ComponentUiState(sampleComponent) {})
      }
    }
  }

  /** The bike and component screens on an open fold and a tablet (decision 0014). */
  @Test
  fun largeScreens() {
    val bike =
      BikeUiState.Loaded(
        bikeId = 1,
        name = "Road bike",
        position = "1 OF 2",
        status =
          BikeStatus(
            StatusTone.Good,
            "All good.",
            "Next to charge: rear derailleur, in about 3 weeks (est.)",
            "Seen 2 min ago",
          ),
        components = sampleRows,
        bikes =
          listOf(
            BikeChoice(1, "Road bike", StatusTone.Good, "2 min ago"),
            BikeChoice(2, "Gravel bike", StatusTone.Good, "3 days ago"),
          ),
        trends = sampleRows.associate { it.id to trendTo(it.level) },
      ) {}
    // Charge soon: the front derailleur is low, so it takes the wide tile.
    val low =
      bike.copy(
        status =
          BikeStatus(
            StatusTone.Low,
            "Charge soon.",
            "Front derailleur at 18%, about 5 days left (est.)",
            "Seen 2 min ago",
          ),
        components =
          sampleRows.map {
            if (it.code == "FD") it.copy(value = "18%", level = 0.18f, tone = StatusTone.Low)
            else it
          },
        trends =
          sampleRows.associate { it.id to trendTo(if (it.code == "FD") 0.18f else it.level) },
      )
    for (size in listOf(RenderSize.Fold, RenderSize.Tablet)) {
      for (dark in listOf(true, false)) {
        val suffix = "${size.name.lowercase()}-${if (dark) "dark" else "light"}"
        render("bike-$suffix", dark, size) { BikeUi(bike) }
        render("bike-low-$suffix", dark, size) { BikeUi(low) }
        // The sheet open on the rear derailleur, drawn with the component screen's own UI.
        render("bike-sheet-$suffix", dark, size) {
          BikeDashboard(
            bike.copy(sheet = 1),
            rail = size == RenderSize.Tablet,
            sheetContent = {
              ComponentUi(ComponentUiState(sampleComponent, inSheet = true) {})
            },
          )
        }
        render("component-$suffix", dark, size) {
          ComponentUi(ComponentUiState(sampleComponent) {})
        }
      }
    }
  }

  /** No bike yet: the phone's column, then split at the fold's hinge and on a tablet. */
  @Test
  fun emptyBike() {
    val fold = RenderSize.Fold
    for (dark in listOf(true, false)) {
      val theme = if (dark) "dark" else "light"
      render("bike-empty-$theme", dark) { BikeUi(BikeUiState.Empty {}) }
      render("bike-empty-fold-$theme", dark, fold) {
        WideEmptyBike(
          {},
          hinge = Rect(fold.width.toFloat(), 0f, fold.width.toFloat(), fold.height * 2f),
        )
      }
      render("bike-empty-tablet-$theme", dark, RenderSize.Tablet) { BikeUi(BikeUiState.Empty {}) }
    }
  }

  /** The tabletop refresh (iteration 10d): each state, with the fold across the middle. */
  @Test
  fun tabletop() {
    val bike =
      BikeUiState.Loaded(
        bikeId = 1,
        name = "Road bike",
        position = null,
        status =
          BikeStatus(
            StatusTone.Good,
            "All good.",
            "Next to charge: rear derailleur, in about 3 weeks (est.)",
            "Seen 2 h ago",
          ),
        components = sampleRows,
        lastRead = "2 h ago",
      ) {}
    val all = sampleRows.map { it.id }.toSet()
    val states =
      listOf(
        "1-ready" to bike,
        "2-listening" to bike.copy(refresh = RefreshButton.Scanning(52), heard = emptySet()),
        "3-reading" to bike.copy(refresh = RefreshButton.Scanning(41), heard = all.take(3).toSet()),
        "4-done" to
          bike.copy(
            refresh = RefreshButton.Done(19),
            heard = all,
            status = bike.status.copy(meta = "Seen just now"),
            lastRead = "just now",
          ),
        "5-done-partly" to bike.copy(refresh = RefreshButton.Done(60), heard = all.take(4).toSet()),
        "6-bluetooth-off" to bike.copy(refresh = RefreshButton.Unavailable),
      )
    val size = RenderSize.Tabletop
    for ((name, state) in states) {
      for (dark in listOf(true, false)) {
        // Late enough that Done has landed.
        render("tabletop-$name-${if (dark) "dark" else "light"}", dark, size, atMillis = 1_500) {
          BikeTabletop(state, foldTop = (size.height / 2).dp, foldBottom = (size.height / 2).dp)
        }
      }
    }
  }

  /**
   * Starting a tabletop refresh as a filmstrip: ready, then frames after Refresh as the circle
   * turns into the ring and the pulses start, so the motion can be checked without a device.
   */
  @Test
  fun tabletopStartFilmstrip() {
    val size = RenderSize.Tabletop
    val ready =
      BikeUiState.Loaded(
        bikeId = 1,
        name = "Road bike",
        position = null,
        status =
          BikeStatus(
            StatusTone.Good,
            "All good.",
            "Next to charge: rear derailleur, in about 3 weeks (est.)",
            "Seen 2 h ago",
          ),
        components = sampleRows,
        lastRead = "2 h ago",
      ) {}
    var state by mutableStateOf(ready)
    val scene =
      ImageComposeScene(width = size.width * 2, height = size.height * 2, density = Density(2f)) {
        CassetteTheme(darkTheme = true) {
          Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) {
            BikeTabletop(state, foldTop = (size.height / 2).dp, foldBottom = (size.height / 2).dp)
          }
        }
      }
    var at = 0L
    fun advanceTo(millis: Long) {
      while (at < millis) {
        at = minOf(millis, at + 16)
        scene.render(at * 1_000_000L)
      }
    }
    advanceTo(1_000)
    state = ready.copy(refresh = RefreshButton.Scanning(60), heard = emptySet())
    val frames = listOf(0L, 150, 300, 450, 700, 1_000, 1_800, 3_000)
    val start = at
    for (frame in frames) {
      advanceTo(start + frame)
      // The countdown ticks once a second, as capture reports it.
      state = state.copy(refresh = RefreshButton.Scanning(60 - (frame / 1_000).toInt()))
      File(out, "tabletop-start-${frame.toString().padStart(4, '0')}ms.png")
        .writeBytes(scene.render(at * 1_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
    scene.close()
  }

  /**
   * The add-bike wake pulse as a filmstrip: rings leaving the Bluetooth circle as it beats, then
   * the hub answering, when the last rings finish and the circle settles green.
   */
  @Test
  fun wakePulseFilmstrip() {
    var step by
      mutableStateOf<AddBikeStep>(AddBikeStep.Wake(38, bluetoothReady = true, showTips = false))
    val scene =
      // Unfolded, where the pulse stays on screen once the hub answers.
      ImageComposeScene(width = 852 * 2, height = 883 * 2, density = Density(2f)) {
        CassetteTheme(darkTheme = true) {
          Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) {
            WakeAndFound(AddBikeUiState(step) {}, hinge = Rect(852f, 0f, 852f, 883f * 2))
          }
        }
      }
    var at = 0L
    fun shoot(millis: Long, name: String) {
      while (at < millis) {
        at = minOf(millis, at + 16)
        scene.render(at * 1_000_000L)
      }
      File(out, "add-bike-pulse-$name.png")
        .writeBytes(scene.render(at * 1_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
    for (frame in listOf(60L, 150, 450, 900, 1_800, 3_000)) {
      shoot(frame, "${frame.toString().padStart(4, '0')}ms")
    }
    step = AddBikeStep.Found(1405188718, reading = true, components = emptyList(), failed = false)
    shoot(3_600, "found-0600ms")
    shoot(6_500, "found-3500ms")
    scene.close()
  }

  /** Thirty days falling to [level], with a charge on day 8 (sample data). */
  private fun trendTo(level: Float): List<Float> =
    listOf(0.62f, 0.58f, 0.55f, 0.51f, 0.48f, 0.44f, 0.41f, 0.37f) +
      (0..12).map { 0.99f + (level - 0.99f) * it / 12 }

  @Test
  fun bikeWithTip() {
    val rows =
      listOf(
        ComponentRowModel(
          1,
          "RD",
          "Rear derailleur",
          "Reported · fw 2.55.8",
          "78%",
          0.78f,
          StatusTone.Good,
        ),
        ComponentRowModel(
          2,
          "FD",
          "Front derailleur",
          "Estimated from 7.40 V",
          "≈11%",
          0.11f,
          StatusTone.Low,
        ),
      )
    for (tip in Tip.entries) {
      for (dark in listOf(true, false)) {
        render("bike-tip-${tip.name.lowercase()}-${if (dark) "dark" else "light"}", dark) {
          BikeUi(
            BikeUiState.Loaded(
              bikeId = 1,
              name = "Road bike",
              position = null,
              status =
                BikeStatus(
                  StatusTone.Low,
                  "Charge soon.",
                  "Front derailleur at ≈11%.",
                  "Seen 2 min ago",
                ),
              components = rows,
              tip = tip,
            ) {}
          )
        }
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

  private fun render(
    name: String,
    dark: Boolean,
    size: RenderSize = RenderSize.Phone,
    atMillis: Long = 100,
    content: @Composable () -> Unit,
  ) {
    val scene =
      ImageComposeScene(width = size.width * 2, height = size.height * 2, density = Density(2f)) {
        CassetteTheme(darkTheme = dark) {
          // The scene is the window, so its size class picks the layout as on a device.
          CompositionLocalProvider(LocalScreenLayout provides currentScreenLayout()) {
            Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) { content() }
          }
        }
      }
    repeat(4) { scene.render(it * 16_000_000L) }
    // Steps up to the frame, so animations that start on the first frames get there.
    (100L..atMillis step 100).forEach { scene.render(it * 1_000_000L) }
    File(out, "$name.png")
      .writeBytes(scene.render(atMillis * 1_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
  }

  /**
   * Window sizes in dp. The fold's is its panel at the density it seems to use (the first build put
   * it over 840 dp); not yet read off the device.
   */
  private enum class RenderSize(val width: Int, val height: Int) {
    Phone(412, 892),
    Fold(852, 883),
    Tablet(1280, 800),
    /** The open fold turned so its hinge runs across, standing half open. */
    Tabletop(883, 852),
  }

  private val sampleRows =
    listOf(
      ComponentRowModel(
        1,
        "RD",
        "Rear derailleur",
        "Reported · fw 2.55.8",
        "78%",
        0.78f,
        StatusTone.Good,
      ),
      ComponentRowModel(
        2,
        "FD",
        "Front derailleur",
        "Estimated from 8.09 V",
        "≈96%",
        0.96f,
        StatusTone.Good,
      ),
      ComponentRowModel(
        3,
        "L",
        "Left shifter",
        "CR2032 coin cell",
        "2.91 V",
        0.89f,
        StatusTone.Good,
      ),
      ComponentRowModel(
        4,
        "R",
        "Right shifter",
        "CR2032 coin cell",
        "2.89 V",
        0.86f,
        StatusTone.Good,
      ),
      ComponentRowModel(5, "PM", "Power meter", "Quarq DZero", "85%", 0.85f, StatusTone.Good),
    )

  private val sampleComponent =
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
      chart =
        listOf(62, 58, 55, 51, 48, 44, 41, 37, 98, 97, 95, 93, 91, 89, 88, 86, 84, 83, 81, 80, 78)
          .map { it / 100f },
      chartCaption = "Charged 12 days ago (sample)",
    )
}
