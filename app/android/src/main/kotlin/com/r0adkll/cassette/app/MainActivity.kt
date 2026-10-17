package com.r0adkll.cassette.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.r0adkll.cassette.common.compose.LocalPermissionController
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.common.screens.OnboardingScreen
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.runtime.screen.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val app
    get() = application as CassetteApplication

  private val permissions = AndroidPermissionController(this)

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    lifecycleScope.launch { app.graph.captureController.ensureBackgroundCapture("app opened") }

    val circuit = app.graph.circuit
    setContent {
      CassetteTheme {
        // The background runs under the system bars; content stays inside them.
        Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) {
          val root by produceState<Screen?>(null) { value = firstScreen() }
          root?.let { start ->
            CompositionLocalProvider(LocalPermissionController provides permissions) {
              CircuitCompositionLocals(circuit) {
                val backStack = rememberSaveableBackStack(root = start)
                val navigator = rememberCircuitNavigator(backStack)
                NavigableCircuitContent(navigator, backStack, Modifier.safeDrawingPadding())
              }
            }
          }
        }
      }
    }
  }

  /** Onboarding on first run (decision 0011); anyone who already has a bike skips it. */
  private suspend fun firstScreen(): Screen {
    val settings = app.graph.settings
    if (settings.onboarded.first()) return BikeScreen()
    if (app.graph.bikeRepository.observeBikes().first().isNotEmpty()) {
      settings.setOnboarded()
      return BikeScreen()
    }
    return OnboardingScreen
  }
}
