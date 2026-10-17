package com.r0adkll.cassette.common.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.r0adkll.cassette.common.compose.gallery.DesignGallery
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders the design gallery to `build/gallery/` in light and dark, so the design system can be
 * checked without a device. Not an assertion test; it fails only if rendering throws.
 */
class GalleryRenderTest {
  @Test
  fun renderGallery() {
    val out = File("build/gallery").apply { mkdirs() }
    for (dark in listOf(true, false)) for (state in StatusTone.entries) {
      val scene =
        ImageComposeScene(width = 412 * 2, height = 3300 * 2, density = Density(2f)) {
          CassetteTheme(darkTheme = dark) {
            DesignGallery(onToggleTheme = {}, initialState = state)
          }
        }
      // A few frames so fonts and layout settle.
      repeat(4) { scene.render(it * 16_000_000L) }
      val image = scene.render(100_000_000L)
      val name = "gallery-${if (dark) "dark" else "light"}-${state.name.lowercase()}.png"
      File(out, name).writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
      scene.close()
    }
  }
}
