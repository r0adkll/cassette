package com.r0adkll.cassette.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.r0adkll.cassette.common.compose.LocalPermissionController
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.common.screens.DeepLinks
import com.r0adkll.cassette.common.screens.OnboardingScreen
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.runtime.screen.Screen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val app
    get() = application as CassetteApplication

  private val permissions = AndroidPermissionController(this)

  /** Links from notifications and widgets that arrive while the activity is running. */
  private val newLinks = Channel<List<Screen>>(Channel.CONFLATED)

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    lifecycleScope.launch { app.graph.captureController.ensureBackgroundCapture("app opened") }

    val circuit = app.graph.circuit
    // A restored activity keeps its back stack; only a fresh start follows the launching link.
    val launchLink = if (savedInstanceState == null) intent.link() else null
    setContent {
      CassetteTheme {
        // The background runs under the system bars; content stays inside them.
        Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) {
          val initial by produceState<List<Screen>?>(null) { value = firstScreens(launchLink) }
          initial?.let { start ->
            CompositionLocalProvider(LocalPermissionController provides permissions) {
              CircuitCompositionLocals(circuit) {
                val backStack = rememberSaveableBackStack(start)
                val navigator = rememberCircuitNavigator(backStack)
                LaunchedEffect(navigator) {
                  newLinks.receiveAsFlow().collect { screens ->
                    // Mid-onboarding, a link waits for the user to finish rather than skipping it.
                    if (!app.graph.settings.onboarded.first()) return@collect
                    navigator.resetRoot(screens.first())
                    screens.drop(1).forEach(navigator::goTo)
                  }
                }
                NavigableCircuitContent(navigator, backStack, Modifier.safeDrawingPadding())
              }
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intent.link()?.let(newLinks::trySend)
  }

  /**
   * Onboarding on first run (decision 0011); anyone who already has a bike skips it. Otherwise the
   * launching link's screens, or the default bike.
   */
  private suspend fun firstScreens(link: List<Screen>?): List<Screen> {
    val settings = app.graph.settings
    if (!settings.onboarded.first()) {
      if (app.graph.bikeRepository.observeBikes().first().isEmpty()) return listOf(OnboardingScreen)
      settings.setOnboarded()
    }
    return link ?: listOf(BikeScreen())
  }

  private fun Intent.link(): List<Screen>? = data?.toString()?.let(DeepLinks::parse)
}
