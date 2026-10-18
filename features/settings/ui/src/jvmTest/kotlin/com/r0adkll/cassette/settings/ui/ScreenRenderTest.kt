package com.r0adkll.cassette.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.r0adkll.cassette.common.compose.AppUpdates
import com.r0adkll.cassette.common.compose.LocalAppUpdates
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.UpdateStatus
import com.r0adkll.cassette.common.compose.currentScreenLayout
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.SettingsCategory
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.settings.api.NotificationSettings
import java.io.File
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders Settings to `build/screens/` on a phone, an open fold and a tablet, light and dark, for
 * review without a device (decision 0014).
 */
class ScreenRenderTest {
  private val out = File("build/screens").apply { mkdirs() }
  private val now = Instant.fromEpochMilliseconds(1_792_000_000_000)

  private val state =
    SettingsUiState(
      bikes =
        listOf(
          Bike(BikeId(1), "Road bike", BikeType.Road, null, 1, now, now),
          Bike(BikeId(2), "Gravel bike", BikeType.Gravel, null, 1, now, now),
        ),
      pinned = null,
      choosingBike = false,
      backgroundCapture = true,
      notifications = NotificationSettings(notSeenReminders = false),
    ) {}

  @Test
  fun settings() {
    val screens =
      listOf(
        Triple("phone", RenderSize.Phone, state),
        Triple("phone-notifications", RenderSize.Phone, state.copy(category = Notifications)),
        Triple("fold-notifications", RenderSize.Fold, state.copy(selected = Notifications)),
        Triple(
          "tablet-bikes",
          RenderSize.Tablet,
          state.copy(selected = SettingsCategory.Bikes, choosingBike = true),
        ),
      )
    for (dark in listOf(true, false)) {
      for ((name, size, screen) in screens) {
        render("settings-$name-${if (dark) "dark" else "light"}", dark, size) {
          SettingsUi(screen)
        }
      }
    }
  }

  private fun render(
    name: String,
    dark: Boolean,
    size: RenderSize,
    content: @Composable () -> Unit,
  ) {
    val scene =
      ImageComposeScene(width = size.width * 2, height = size.height * 2, density = Density(2f)) {
        CassetteTheme(darkTheme = dark) {
          // The scene is the window, so its size class picks the layout as on a device.
          CompositionLocalProvider(
            LocalScreenLayout provides currentScreenLayout(),
            LocalAppUpdates provides WaitingUpdate,
          ) {
            Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) { content() }
          }
        }
      }
    repeat(4) { scene.render(it * 16_000_000L) }
    File(out, "$name.png")
      .writeBytes(scene.render(100_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
  }

  /** A build waiting, so App updates shows with its dot. */
  private object WaitingUpdate : AppUpdates {
    override val status = MutableStateFlow<UpdateStatus>(UpdateStatus.Available("Build 41"))

    override fun check() = Unit

    override fun open() = Unit
  }

  /** Window sizes in dp, as in the bike screen's renders. */
  private enum class RenderSize(val width: Int, val height: Int) {
    Phone(412, 892),
    Fold(852, 883),
    Tablet(1280, 800),
  }

  private companion object {
    val Notifications = SettingsCategory.Notifications
  }
}
