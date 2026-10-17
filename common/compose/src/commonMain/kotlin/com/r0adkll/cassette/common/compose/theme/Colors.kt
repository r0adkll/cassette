package com.r0adkll.cassette.common.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** SRAM-ish red: the brand accent, and the colour of "charge now". */
val CassetteRed = Color(0xFFE4002B)

/** A component's or a bike's battery status, as the UI shows it. */
enum class StatusTone {
    Good,
    Low,
    Critical,
    /** Not seen for long enough that the reading may be out of date. */
    Stale,
    /** No reading yet, or the component reports "unknown". */
    Unknown,
}

/**
 * Colours from decision 0012. The status block's colour is the status; [CassetteRed] fills a large
 * area only for [StatusTone.Critical].
 */
@Immutable
data class CassetteColors(
    val isDark: Boolean,
    val background: Color,
    val panel: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val track: Color,
    /** Primary buttons and selected controls. */
    val ink: Color,
    val onInk: Color,
    val brand: Color,
    /** Bars, labels and icons on the background. */
    val good: Color,
    val low: Color,
    val critical: Color,
    val stale: Color,
) {
    fun status(tone: StatusTone): Color =
        when (tone) {
            StatusTone.Good -> good
            StatusTone.Low -> low
            StatusTone.Critical -> critical
            StatusTone.Stale,
            StatusTone.Unknown -> stale
        }

    /** Fill and content colours for the full-width status block. */
    fun block(tone: StatusTone): BlockColors =
        when (tone) {
            StatusTone.Good -> BlockColors(Color(0xFF2DB36A), Color(0xFF03200F))
            StatusTone.Low -> BlockColors(Color(0xFFFFB020), Color(0xFF1A1200))
            StatusTone.Critical -> BlockColors(brand, Color.White)
            StatusTone.Stale,
            StatusTone.Unknown -> BlockColors(panel, muted, outline = line)
        }
}

@Immutable
data class BlockColors(val container: Color, val content: Color, val outline: Color? = null)

val DarkCassetteColors =
    CassetteColors(
        isDark = true,
        background = Color(0xFF0A0A0B),
        panel = Color(0xFF141416),
        line = Color(0xFF2A2A2F),
        text = Color(0xFFF4F4F2),
        muted = Color(0xFFA2A2A8),
        track = Color(0xFF1E1E22),
        ink = Color(0xFFF4F4F2),
        onInk = Color(0xFF0A0A0B),
        brand = CassetteRed,
        good = Color(0xFF4ADE80),
        low = Color(0xFFFFB020),
        critical = CassetteRed,
        stale = Color(0xFF6A6A70),
    )

val LightCassetteColors =
    CassetteColors(
        isDark = false,
        background = Color(0xFFEDEDEB),
        panel = Color(0xFFFFFFFF),
        line = Color(0xFFD6D6D2),
        text = Color(0xFF111113),
        muted = Color(0xFF55555B),
        track = Color(0xFFE2E2DE),
        ink = Color(0xFF111113),
        onInk = Color(0xFFFFFFFF),
        brand = CassetteRed,
        good = Color(0xFF1E8E3E),
        low = Color(0xFFB86E00),
        critical = CassetteRed,
        stale = Color(0xFF9A9A96),
    )
