package com.r0adkll.cassette.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.r0adkll.cassette.common.compose.AppInfo
import com.r0adkll.cassette.common.compose.AppPermission
import com.r0adkll.cassette.common.compose.AppPlatform
import com.r0adkll.cassette.common.compose.AppUpdates
import com.r0adkll.cassette.common.compose.AvailableBuild
import com.r0adkll.cassette.common.compose.FixedPermissionController
import com.r0adkll.cassette.common.compose.LocalAppInfo
import com.r0adkll.cassette.common.compose.LocalAppPlatform
import com.r0adkll.cassette.common.compose.LocalAppUpdates
import com.r0adkll.cassette.common.compose.LocalPermissionController
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.PermissionController
import com.r0adkll.cassette.common.compose.PermissionState
import com.r0adkll.cassette.common.compose.ReinstallReason
import com.r0adkll.cassette.common.compose.UpdateStatus
import com.r0adkll.cassette.common.compose.currentScreenLayout
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.common.screens.SettingsCategory
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.settings.api.NotificationSettings
import com.r0adkll.cassette.settings.api.ThemeMode
import java.io.File
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders Settings to `build/screens/` on a phone, an open fold and a tablet, light and dark, for
 * review without a device (decisions 0014; iteration 13 for Bikes and App updates).
 */
class ScreenRenderTest {
  private val out = File("build/screens").apply { mkdirs() }

  private val state =
    SettingsUiState(
      garage =
        listOf(
          GarageBike(
            1,
            "Road bike",
            "Road",
            "seen 2 min ago",
            StatusTone.Low,
            listOf(
              "RD" to StatusTone.Good,
              "FD" to StatusTone.Low,
              "L" to StatusTone.Good,
              "R" to StatusTone.Good,
              "PM" to StatusTone.Good,
            ),
            opensFirst = false,
          ),
          GarageBike(
            2,
            "Gravel bike",
            "Gravel",
            "seen 3 days ago",
            StatusTone.Good,
            listOf(
              "RD" to StatusTone.Good,
              "L" to StatusTone.Good,
              "R" to StatusTone.Good,
              "DP" to StatusTone.Good,
            ),
            opensFirst = true,
          ),
        ),
      pinned = BikeId(2),
      backgroundCapture = true,
      playfulTitles = true,
      notifications = NotificationSettings(notSeenReminders = false),
    ) {}

  private val build =
    AvailableBuild(
      number = 41,
      headline = "Group settings by category on every size",
      sizeBytes = 9_800_000,
      uploadedAt = Clock.System.now() - 2.hours,
      whatsNew =
        "- Settings are grouped by category.\n" +
          "- Diagnostics opens in the settings pane on wide screens.\n" +
          "- Tablets list the bikes on the left.",
      pullRequest = 47,
      channel = "internal",
    )

  @Test
  fun settings() {
    val ready = UpdateStatus.Available(build)
    val screens =
      listOf(
        Shot("phone", RenderSize.Phone, state, ready),
        Shot("phone-notifications", RenderSize.Phone, state.copy(category = Notifications), ready),
        Shot("phone-bikes", RenderSize.Phone, state.copy(category = Bikes), ready),
        Shot("fold-notifications", RenderSize.Fold, state.copy(selected = Notifications), ready),
        Shot("fold-bikes", RenderSize.Fold, state.copy(selected = Bikes), ready),
        Shot("tablet-bikes", RenderSize.Tablet, state.copy(selected = Bikes), ready),
        Shot("phone-appearance", RenderSize.Phone, state.copy(category = Appearance), ready),
        Shot(
          "fold-appearance",
          RenderSize.Fold,
          state.copy(selected = Appearance, themeMode = ThemeMode.Dark),
          ready,
        ),
        Shot("phone-updates-ready", RenderSize.Phone, state.copy(category = Updates), ready),
        Shot("fold-updates-ready", RenderSize.Fold, state.copy(selected = Updates), ready),
        Shot(
          "phone-updates-up-to-date",
          RenderSize.Phone,
          state.copy(category = Updates),
          UpdateStatus.UpToDate,
        ),
        Shot(
          "phone-updates-reinstall",
          RenderSize.Phone,
          state.copy(category = Updates),
          UpdateStatus.Available(build.copy(number = 42, reinstall = ReinstallReason.DifferentKey)),
        ),
        Shot(
          "phone-updates-failed",
          RenderSize.Phone,
          state.copy(category = Updates),
          UpdateStatus.Failed("Sign in to Drop to check"),
        ),
        Shot("phone-about", RenderSize.Phone, state.copy(category = About), ready),
        Shot("fold-about", RenderSize.Fold, state.copy(selected = About), ready),
        // A phone on its side keeps the phone's one category at a time (decision 0014).
        Shot("landscape", RenderSize.PhoneLandscape, state, ready),
        Shot("landscape-bikes", RenderSize.PhoneLandscape, state.copy(category = Bikes), ready),
        Shot(
          "landscape-appearance",
          RenderSize.PhoneLandscape,
          state.copy(category = Appearance),
          ready,
        ),
        Shot("landscape-updates", RenderSize.PhoneLandscape, state.copy(category = Updates), ready),
      )
    for (dark in listOf(true, false)) {
      for (shot in screens) {
        render("settings-${shot.name}-${if (dark) "dark" else "light"}", dark, shot) {
          SettingsUi(shot.state)
        }
      }
    }
  }

  /** The iPhone app's wording (decision 0017), with notifications not yet allowed. */
  @Test
  fun ios() {
    val askable =
      FixedPermissionController(
        mapOf(
          AppPermission.Nearby to PermissionState.Granted,
          AppPermission.Notifications to PermissionState.Askable,
        )
      )
    val off = UpdateStatus.Off
    val screens =
      listOf(
        Shot("notifications", RenderSize.Phone, state.copy(category = Notifications), off, askable),
        Shot("appearance", RenderSize.Phone, state.copy(category = Appearance), off),
        Shot(
          "capture",
          RenderSize.Phone,
          state.copy(
            category = Capture,
            lastHeard = "Last heard 5 min ago",
            wakes = "42 background wakes",
          ),
          off,
        ),
        Shot("tablet-notifications", RenderSize.Tablet, state.copy(selected = Notifications), off),
        Shot("about", RenderSize.Phone, state.copy(category = About), off),
      )
    for (dark in listOf(true, false)) {
      for (shot in screens) {
        val name = "settings-ios-${shot.name}-${if (dark) "dark" else "light"}"
        render(name, dark, shot, AppPlatform.Ios) { SettingsUi(shot.state) }
      }
    }
  }

  @Test
  fun bikePage() {
    val model =
      BikeSettingsModel(
        name = "Road bike",
        type = BikeType.Road,
        opensFirst = false,
        components =
          listOf(
            ComponentLine("RD", "Rear derailleur", "1405188718"),
            ComponentLine("FD", "Front derailleur", "1405117203"),
            ComponentLine("L", "Left shifter", "1405260941"),
            ComponentLine("R", "Right shifter", "1405260958"),
            ComponentLine("PM", "Power meter", "Quarq AGN12387"),
          ),
      )
    for (dark in listOf(true, false)) {
      for (size in RenderSize.entries) {
        val shot = Shot("bike", size, state, UpdateStatus.UpToDate)
        val prefix = if (size == RenderSize.Phone) "" else "${size.name.lowercase()}-"
        render("settings-${prefix}bike-page-${if (dark) "dark" else "light"}", dark, shot) {
          BikeSettingsUi(BikeSettingsUiState(model, confirmRemove = false) {})
        }
      }
      val armed = Shot("bike", RenderSize.Phone, state, UpdateStatus.UpToDate)
      render("settings-bike-page-remove-${if (dark) "dark" else "light"}", dark, armed) {
        BikeSettingsUi(BikeSettingsUiState(model, confirmRemove = true) {})
      }
    }
  }

  private fun render(
    name: String,
    dark: Boolean,
    shot: Shot,
    platform: AppPlatform = AppPlatform.Android,
    content: @Composable () -> Unit,
  ) {
    val size = shot.size
    val updates = FakeUpdates(shot.update)
    val scene =
      ImageComposeScene(width = size.width * 2, height = size.height * 2, density = Density(2f)) {
        CassetteTheme(darkTheme = dark) {
          // The scene is the window, so its size class picks the layout as on a device.
          CompositionLocalProvider(
            LocalScreenLayout provides currentScreenLayout(),
            LocalAppUpdates provides updates,
            LocalAppPlatform provides platform,
            LocalAppInfo provides AppInfo(version = "0.1.0", build = "40"),
            LocalPermissionController provides shot.permissions,
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

  private class Shot(
    val name: String,
    val size: RenderSize,
    val state: SettingsUiState,
    val update: UpdateStatus,
    val permissions: PermissionController = FixedPermissionController(),
  )

  /** Updates in a fixed state, installed as build 40 and checked 5 minutes ago. */
  private class FakeUpdates(status: UpdateStatus) : AppUpdates {
    override val status = MutableStateFlow(status)

    override val installedBuild: Long = 40

    override val lastChecked = MutableStateFlow<Instant?>(Clock.System.now() - 5.minutes)

    override fun check() = Unit

    override fun open() = Unit
  }

  /** Window sizes in dp, as in the bike screen's renders. */
  private enum class RenderSize(val width: Int, val height: Int) {
    Phone(412, 892),
    Fold(852, 883),
    Tablet(1280, 800),
    PhoneLandscape(892, 412),
  }

  private companion object {
    val Bikes = SettingsCategory.Bikes
    val Notifications = SettingsCategory.Notifications
    val Updates = SettingsCategory.Updates
    val Appearance = SettingsCategory.Appearance
    val Capture = SettingsCategory.Capture
    val About = SettingsCategory.About
  }
}
