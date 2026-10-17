package com.r0adkll.cassette.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.r0adkll.cassette.common.compose.AppPermission
import com.r0adkll.cassette.common.compose.FixedPermissionController
import com.r0adkll.cassette.common.compose.LocalPermissionController
import com.r0adkll.cassette.common.compose.PermissionState
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
    }
  }

  private fun render(
    file: File,
    dark: Boolean,
    permissions: FixedPermissionController,
    content: @Composable () -> Unit,
  ) {
    val scene =
      ImageComposeScene(width = 412 * 2, height = 892 * 2, density = Density(2f)) {
        CassetteTheme(darkTheme = dark) {
          CompositionLocalProvider(LocalPermissionController provides permissions) {
            Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) { content() }
          }
        }
      }
    repeat(4) { scene.render(it * 16_000_000L) }
    file.writeBytes(scene.render(100_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
  }
}
