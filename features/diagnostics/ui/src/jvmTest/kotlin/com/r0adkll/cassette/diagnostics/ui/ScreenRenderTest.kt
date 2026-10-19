package com.r0adkll.cassette.diagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.WearAction
import com.r0adkll.cassette.common.compose.AppPlatform
import com.r0adkll.cassette.common.compose.LocalAppPlatform
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.data.ble.ScanProfile
import com.r0adkll.cassette.settings.api.CaptureHealth
import com.r0adkll.cassette.surfaces.wear.sync.ConnectedWatch
import java.io.File
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.jetbrains.skia.EncodedImageFormat

/** Renders Diagnostics to `build/screens/`, light and dark, for review without a device. */
class ScreenRenderTest {
  private val out = File("build/screens").apply { mkdirs() }
  private val now = Instant.fromEpochMilliseconds(1_792_000_000_000)

  @Test
  fun diagnostics() {
    val wear =
      listOf(
        "wear" to
          WearDiagnostics(
            WatchCheck.Found(listOf(ConnectedWatch("Pixel Watch 3", hasCassette = true))),
            lastPublish = wearEvent(WearAction.Published, "2 bikes", 2.minutes.ago),
            lastRequest = wearEvent(WearAction.RefreshRequested, "Pixel Watch 3", 1.hours.ago),
          ),
        "no-wear" to WearDiagnostics(WatchCheck.Unavailable, null, null),
        // The Apple Watch panel and no scan profiles on iOS (decision 0017).
        "ios" to
          WearDiagnostics(
            WatchCheck.Found(listOf(ConnectedWatch("Apple Watch", hasCassette = true))),
            lastPublish = wearEvent(WearAction.Published, "2 bikes", 2.minutes.ago),
            lastRequest = wearEvent(WearAction.RefreshRequested, "Apple Watch", 1.hours.ago),
          ),
      )
    for (dark in listOf(true, false)) {
      for ((name, panel) in wear) {
        val platform = if (name == "ios") AppPlatform.Ios else AppPlatform.Android
        render("diagnostics-$name-${if (dark) "dark" else "light"}", dark, platform) {
          DiagnosticsUi(
            DiagnosticsUiState(
              health =
                CaptureHealth("Armed (AllLowPower) · app start", 3.hours.ago, 5.minutes.ago, 42),
              bluetoothReady = true,
              profile = ScanProfile.AllLowPower,
              bike = null,
              now = now,
              confirmRemove = false,
              logEvents = 120,
              sharing = false,
              wear = panel,
            ) {}
          )
        }
      }
    }
  }

  private val kotlin.time.Duration.ago: Instant
    get() = now - this

  private fun wearEvent(action: WearAction, detail: String, at: Instant) =
    CaptureEvent.Wear(at.toEpochMilliseconds(), action, detail)

  private fun render(
    name: String,
    dark: Boolean,
    platform: AppPlatform,
    content: @Composable () -> Unit,
  ) {
    val scene =
      ImageComposeScene(width = 412 * 2, height = 892 * 2, density = Density(2f)) {
        CassetteTheme(darkTheme = dark) {
          CompositionLocalProvider(LocalAppPlatform provides platform) {
            Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) { content() }
          }
        }
      }
    repeat(4) { scene.render(it * 16_000_000L) }
    File(out, "$name.png")
      .writeBytes(scene.render(100_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
  }
}
