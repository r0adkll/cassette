package com.r0adkll.cassette.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.r0adkll.cassette.common.compose.AppPermission
import com.r0adkll.cassette.common.compose.LocalPermissionController
import com.r0adkll.cassette.common.compose.PermissionController
import com.r0adkll.cassette.common.compose.PermissionState
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders each onboarding step to `build/screens/`, light and dark, for review without a device.
 */
class OnboardingRenderTest {
  private val askable =
    object : PermissionController {
      override fun state(permission: AppPermission) = PermissionState.Askable

      override fun request(permission: AppPermission, onResult: (PermissionState) -> Unit) = Unit

      override fun openSettings() = Unit
    }

  @Test
  fun steps() {
    val out = File("build/screens").apply { mkdirs() }
    for (dark in listOf(true, false)) {
      for (step in OnboardingStep.entries) {
        val scene =
          ImageComposeScene(width = 412 * 2, height = 892 * 2, density = Density(2f)) {
            CassetteTheme(darkTheme = dark) {
              CompositionLocalProvider(LocalPermissionController provides askable) {
                Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) {
                  OnboardingUi(OnboardingUiState(step) {})
                }
              }
            }
          }
        repeat(4) { scene.render(it * 16_000_000L) }
        File(
            out,
            "onboarding-${step.ordinal + 1}-${step.name.lowercase()}-${if (dark) "dark" else "light"}.png",
          )
          .writeBytes(scene.render(100_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
      }
    }
  }
}
