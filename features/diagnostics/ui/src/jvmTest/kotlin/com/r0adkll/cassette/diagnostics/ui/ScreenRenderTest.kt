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
import com.r0adkll.cassette.common.compose.components.LocalInDetailPane
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
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
          DiagnosticsUi(state(panel))
        }
      }
      // In Settings' pane on a tablet (the window less the 320 dp list and its divider), laid out
      // as the pane's categories are.
      render(
        "diagnostics-tablet-pane-${if (dark) "dark" else "light"}",
        dark,
        AppPlatform.Android,
        width = 959,
        height = 800,
      ) {
        CompositionLocalProvider(LocalInDetailPane provides true) {
          DiagnosticsUi(state(wear.first().second, inPane = true))
        }
      }
    }
  }

  /** AXS frames beside Shimano adverts, one read and one not (decision 0029). */
  @Test
  fun liveAdverts() {
    val state =
      LiveAdvertsUiState(
        adverts =
          listOf(
            AdvertRow(
              3,
              "+12.4 s",
              "Di2",
              AdvertTag.Shimano,
              -58,
              listOf("name RDR9250", "address C4:7F:51:2A:90:11", "services 18ef, 18ff"),
              probeAddress = "C4:7F:51:2A:90:11",
            ),
            AdvertRow(
              2,
              "+9.1 s",
              "RD",
              AdvertTag.Status,
              -61,
              listOf("serial 1016200351", "mfg 00 00 01 02 00 04 05 b6 1f 03 80 64"),
            ),
            AdvertRow(
              1,
              "+4.0 s",
              "Di2",
              AdvertTag.Shimano,
              -74,
              listOf("address D0:2E:61:7B:44:02", "mfg 01 02 03"),
              probeAddress = "D0:2E:61:7B:44:02",
            ),
          ),
        filter = AdvertFilter.All,
        paused = false,
        failed = false,
        probes =
          mapOf(
            "D0:2E:61:7B:44:02" to
              ProbeState.Done(
                listOf("battery 87 %", "model RD-R7150", "services 1800, 180a, 180f, 18ef, 18ff")
              )
          ),
      ) {}
    for (dark in listOf(true, false)) {
      val theme = if (dark) "dark" else "light"
      render("live-adverts-$theme", dark, AppPlatform.Android) { LiveAdvertsUi(state) }
      render("live-adverts-tablet-$theme", dark, AppPlatform.Android, width = 1280, height = 800) {
        LiveAdvertsUi(state)
      }
      render(
        "live-adverts-landscape-$theme",
        dark,
        AppPlatform.Android,
        width = 892,
        height = 412,
      ) {
        LiveAdvertsUi(state)
      }
    }
  }

  private fun state(wear: WearDiagnostics, inPane: Boolean = false) =
    DiagnosticsUiState(
      health = CaptureHealth("Armed (AllLowPower) · app start", 3.hours.ago, 5.minutes.ago, 42),
      bluetoothReady = true,
      bike = null,
      now = now,
      confirmRemove = false,
      logEvents = 120,
      sharing = false,
      wear = wear,
      inPane = inPane,
    ) {}

  private val kotlin.time.Duration.ago: Instant
    get() = now - this

  private fun wearEvent(action: WearAction, detail: String, at: Instant) =
    CaptureEvent.Wear(at.toEpochMilliseconds(), action, detail)

  private fun render(
    name: String,
    dark: Boolean,
    platform: AppPlatform,
    width: Int = 412,
    height: Int = 892,
    content: @Composable () -> Unit,
  ) {
    val scene =
      ImageComposeScene(width = width * 2, height = height * 2, density = Density(2f)) {
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
