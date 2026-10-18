package com.r0adkll.cassette.app.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.common.compose.AppUpdates
import com.r0adkll.cassette.common.compose.LocalAppUpdates
import com.r0adkll.cassette.common.compose.LocalPermissionController
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.PermissionController
import com.r0adkll.cassette.common.compose.currentScreenLayout
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.common.screens.OnboardingScreen
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.r0adkll.cassette.settings.api.ThemeMode
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.runtime.screen.Screen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * The app: theme, first screens and the back stack, hosted by each platform's entry point.
 *
 * @param launchLink the screens the app was opened with (a notification or widget), if any.
 * @param newLinks links that arrive while the app is running.
 * @param systemBars applies the theme (and the Appearance setting it came from) to the platform's
 *   status and navigation bars.
 * @param overlay drawn over the screens (Android: Drop's update sheet).
 */
@Composable
fun CassetteRoot(
  circuit: Circuit,
  settings: CassetteSettings,
  bikes: BikeRepository,
  permissions: PermissionController,
  updates: AppUpdates,
  launchLink: List<Screen>?,
  newLinks: Flow<List<Screen>>,
  systemBars: @Composable (dark: Boolean, mode: ThemeMode) -> Unit = { _, _ -> },
  overlay: @Composable () -> Unit = {},
) {
  // Settings → Appearance; the phone's theme until the setting has loaded.
  val mode by remember { settings.themeMode }.collectAsState(initial = ThemeMode.System)
  val dark =
    when (mode) {
      ThemeMode.System -> isSystemInDarkTheme()
      ThemeMode.Light -> false
      ThemeMode.Dark -> true
    }
  systemBars(dark, mode)
  CassetteTheme(darkTheme = dark) {
    // The background runs under the system bars; content stays inside them.
    Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) {
      val initial by
        produceState<List<Screen>?>(null) { value = firstScreens(settings, bikes, launchLink) }
      initial?.let { start ->
        CompositionLocalProvider(
          LocalPermissionController provides permissions,
          LocalAppUpdates provides updates,
          LocalScreenLayout provides currentScreenLayout(),
        ) {
          CircuitCompositionLocals(circuit) {
            val backStack = rememberSaveableBackStack(start)
            val navigator = rememberAppNavigator(backStack)
            LaunchedEffect(navigator) {
              newLinks.collect { screens ->
                // Mid-onboarding, a link waits for the user to finish rather than skipping it.
                if (!settings.onboarded.first()) return@collect
                navigator.resetRoot(screens.first())
                screens.drop(1).forEach(navigator::goTo)
              }
            }
            val background = CassetteTheme.colors.background
            NavigableCircuitContent(
              navigator,
              backStack,
              decoration =
                remember(background) { OpaqueScreens(circuit.defaultNavDecoration, background) },
            )
            overlay()
          }
        }
      }
    }
  }
}

/**
 * Onboarding on first run (decision 0011); anyone who already has a bike skips it. Otherwise the
 * launching link's screens, or the default bike.
 */
private suspend fun firstScreens(
  settings: CassetteSettings,
  bikes: BikeRepository,
  link: List<Screen>?,
): List<Screen> {
  if (!settings.onboarded.first()) {
    if (bikes.observeBikes().first().isEmpty()) return listOf(OnboardingScreen)
    settings.setOnboarded()
  }
  return link ?: listOf(BikeScreen())
}
