package com.r0adkll.cassette.common.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.r0adkll.cassette.common.compose.resources.Res
import com.r0adkll.cassette.common.compose.resources.barlow_condensed_bold
import com.r0adkll.cassette.common.compose.resources.barlow_condensed_medium
import com.r0adkll.cassette.common.compose.resources.barlow_condensed_semibold
import com.r0adkll.cassette.common.compose.resources.barlow_medium
import com.r0adkll.cassette.common.compose.resources.barlow_regular
import com.r0adkll.cassette.common.compose.resources.barlow_semibold
import com.r0adkll.cassette.common.compose.resources.jetbrains_mono_bold
import com.r0adkll.cassette.common.compose.resources.jetbrains_mono_medium
import com.r0adkll.cassette.common.compose.resources.jetbrains_mono_regular
import org.jetbrains.compose.resources.Font

/**
 * Barlow Condensed for display, numerals and labels; Barlow for body text; JetBrains Mono for raw
 * data in diagnostics (decision 0012).
 */
@Immutable
data class CassetteTypography(
  /** Bike name and the status block's headline. */
  val display: TextStyle,
  /** Screen titles that aren't the bike name ("Settings.", "Rear derailleur"). */
  val title: TextStyle,
  /** A component's value in a row ("78%", "2.91 V"). */
  val numeral: TextStyle,
  /** The big value in a component's status block. */
  val numeralLarge: TextStyle,
  /** Small uppercase labels ("ROAD BIKE", "VOLTAGE"). Pass text already uppercased. */
  val label: TextStyle,
  /** Button text, uppercased by the caller. */
  val button: TextStyle,
  /** Component and setting names. */
  val itemTitle: TextStyle,
  val body: TextStyle,
  val bodySmall: TextStyle,
  /** Status words beside icons ("GOOD", "CHARGE NOW"). */
  val status: TextStyle,
  val mono: TextStyle,
)

@Composable
fun cassetteTypography(): CassetteTypography {
  val condensed =
    FontFamily(
      Font(Res.font.barlow_condensed_medium, FontWeight.Medium),
      Font(Res.font.barlow_condensed_semibold, FontWeight.SemiBold),
      Font(Res.font.barlow_condensed_bold, FontWeight.Bold),
    )
  val barlow =
    FontFamily(
      Font(Res.font.barlow_regular, FontWeight.Normal),
      Font(Res.font.barlow_medium, FontWeight.Medium),
      Font(Res.font.barlow_semibold, FontWeight.SemiBold),
    )
  val mono =
    FontFamily(
      Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
      Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
      Font(Res.font.jetbrains_mono_bold, FontWeight.Bold),
    )
  return CassetteTypography(
    display =
      TextStyle(
        fontFamily = condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        lineHeight = 0.92.em,
        letterSpacing = (-0.02).em,
      ),
    title =
      TextStyle(
        fontFamily = condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 46.sp,
        lineHeight = 0.92.em,
        letterSpacing = (-0.02).em,
      ),
    numeral =
      TextStyle(
        fontFamily = condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 1.em,
      ),
    numeralLarge =
      TextStyle(
        fontFamily = condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 88.sp,
        lineHeight = 0.85.em,
        letterSpacing = (-0.01).em,
      ),
    label =
      TextStyle(
        fontFamily = condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.2.em,
      ),
    button =
      TextStyle(
        fontFamily = condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        letterSpacing = 0.1.em,
      ),
    itemTitle = TextStyle(fontFamily = barlow, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    body =
      TextStyle(
        fontFamily = barlow,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 1.35.em,
      ),
    bodySmall = TextStyle(fontFamily = barlow, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    status =
      TextStyle(
        fontFamily = barlow,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 0.12.em,
      ),
    mono =
      TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 1.45.em,
      ),
  )
}
