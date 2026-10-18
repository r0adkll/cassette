package com.r0adkll.cassette.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import com.r0adkll.cassette.common.compose.AppPermission
import com.r0adkll.cassette.common.compose.FixedPermissionController
import com.r0adkll.cassette.common.compose.LocalPermissionController
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.PermissionState
import com.r0adkll.cassette.common.compose.currentScreenLayout
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders the onboarding screens to `build/screens/`, light and dark, for review without a device.
 */
class OnboardingRenderTest {
  private val askable =
    FixedPermissionController(AppPermission.entries.associateWith { PermissionState.Askable })
  private val answered =
    FixedPermissionController(
      mapOf(
        AppPermission.Nearby to PermissionState.Granted,
        AppPermission.Notifications to PermissionState.Blocked,
      )
    )

  @Test
  fun screens() {
    val out = File("build/screens").apply { mkdirs() }
    for (dark in listOf(true, false)) {
      val theme = if (dark) "dark" else "light"
      render(File(out, "onboarding-1-welcome-$theme.png"), dark, askable) {
        WelcomeUi(WelcomeUiState {})
      }
      render(File(out, "onboarding-2-permissions-$theme.png"), dark, askable) {
        PermissionsUi(PermissionsUiState {})
      }
      render(File(out, "onboarding-3-permissions-answered-$theme.png"), dark, answered) {
        PermissionsUi(PermissionsUiState {})
      }
      // Unfolded (decision 0014): the open fold splits at its hinge, a tablet down the middle.
      render(File(out, "onboarding-fold-welcome-$theme.png"), dark, askable, 852, 883) {
        WideWelcome(WelcomeUiState {}, hinge = Rect(852f, 0f, 852f, 883f * 2))
      }
      render(File(out, "onboarding-tablet-welcome-$theme.png"), dark, askable, 1280, 800) {
        WelcomeUi(WelcomeUiState {})
      }
    }
  }

  private fun render(
    file: File,
    dark: Boolean,
    permissions: FixedPermissionController,
    width: Int = 412,
    height: Int = 892,
    content: @Composable () -> Unit,
  ) {
    val scene =
      ImageComposeScene(width = width * 2, height = height * 2, density = Density(2f)) {
        CassetteTheme(darkTheme = dark) {
          CompositionLocalProvider(
            LocalPermissionController provides permissions,
            LocalScreenLayout provides currentScreenLayout(),
          ) {
            Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) { content() }
          }
        }
      }
    repeat(4) { scene.render(it * 16_000_000L) }
    file.writeBytes(scene.render(100_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
  }
}
