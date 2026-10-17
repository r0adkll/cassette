package com.r0adkll.cassette.common.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalCassetteColors = staticCompositionLocalOf { DarkCassetteColors }
private val LocalCassetteTypography =
    staticCompositionLocalOf<CassetteTypography> { error("CassetteTheme not applied") }

/**
 * Cassette's look. Material 3 is only the fallback component layer, so its colour scheme is mapped
 * from ours; no dynamic colour (decision 0010).
 */
@Composable
fun CassetteTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkCassetteColors else LightCassetteColors
    val scheme =
        if (darkTheme) {
            darkColorScheme(
                primary = colors.ink,
                onPrimary = colors.onInk,
                secondary = colors.brand,
                background = colors.background,
                onBackground = colors.text,
                surface = colors.background,
                onSurface = colors.text,
                surfaceContainer = colors.panel,
                onSurfaceVariant = colors.muted,
                outline = colors.line,
                error = colors.critical,
            )
        } else {
            lightColorScheme(
                primary = colors.ink,
                onPrimary = colors.onInk,
                secondary = colors.brand,
                background = colors.background,
                onBackground = colors.text,
                surface = colors.background,
                onSurface = colors.text,
                surfaceContainer = colors.panel,
                onSurfaceVariant = colors.muted,
                outline = colors.line,
                error = colors.critical,
            )
        }
    CompositionLocalProvider(
        LocalCassetteColors provides colors,
        LocalCassetteTypography provides cassetteTypography(),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object CassetteTheme {
    val colors: CassetteColors
        @Composable @ReadOnlyComposable get() = LocalCassetteColors.current

    val type: CassetteTypography
        @Composable @ReadOnlyComposable get() = LocalCassetteTypography.current
}
