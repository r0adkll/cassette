package com.r0adkll.cassette.surfaces.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.text.FontFamily
import com.r0adkll.cassette.common.compose.theme.DarkCassetteColors
import com.r0adkll.cassette.common.compose.theme.LightCassetteColors
import com.r0adkll.cassette.common.compose.theme.StatusTone

/** Our palette as day/night pairs (decision 0012). */
internal object WidgetColors {
  private val light = LightCassetteColors
  private val dark = DarkCassetteColors

  val surface = ColorProvider(day = Color(0xFFF7F7F5), night = Color(0xFF0E0E10))
  val text = ColorProvider(day = light.text, night = dark.text)
  val muted = ColorProvider(day = light.muted, night = dark.muted)
  val track = ColorProvider(day = Color(0xFFE6E6E2), night = Color(0xFF1E1E22))

  fun status(tone: StatusTone) = ColorProvider(day = light.status(tone), night = dark.status(tone))

  fun blockFill(tone: StatusTone) =
    ColorProvider(day = light.block(tone).container, night = dark.block(tone).container)

  fun blockContent(tone: StatusTone) =
    ColorProvider(day = light.block(tone).content, night = dark.block(tone).content)

  /** Thick-bar fills: a tint of the status colour, readable under text in both themes. */
  fun thickFill(tone: StatusTone, night: Boolean): Color =
    if (night) {
      when (tone) {
        StatusTone.Good -> Color(0xFF1D5C38)
        StatusTone.Low -> Color(0xFF6B4800)
        StatusTone.Critical -> Color(0xFF8A0019)
        StatusTone.Stale,
        StatusTone.Unknown -> Color(0xFF2A2A2F)
      }
    } else {
      when (tone) {
        StatusTone.Good -> Color(0xFFBFE8CC)
        StatusTone.Low -> Color(0xFFFFE0A3)
        StatusTone.Critical -> Color(0xFFFFC7CE)
        StatusTone.Stale,
        StatusTone.Unknown -> Color(0xFFE2E2DE)
      }
    }

  fun thickTrack(night: Boolean): Color = if (night) Color(0xFF1E1E22) else Color(0xFFE6E6E2)
}

/** RemoteViews can't load our Barlow fonts; the system condensed face is the nearest. */
internal val Condensed = FontFamily("sans-serif-condensed")
