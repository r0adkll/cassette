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
