package com.r0adkll.cassette.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.BikeScreen
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val app
    get() = application as CassetteApplication

  // Until onboarding exists (decision 0011), ask for Nearby devices on launch.
  private val requestBluetooth =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      lifecycleScope.launch {
        app.graph.captureController.ensureBackgroundCapture("permission granted")
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val missing = BLUETOOTH_PERMISSIONS.filter {
      checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
    }
    if (missing.isNotEmpty()) {
      requestBluetooth.launch(missing.toTypedArray())
    } else {
      lifecycleScope.launch { app.graph.captureController.ensureBackgroundCapture("app opened") }
    }

    val circuit = app.graph.circuit
    setContent {
      CassetteTheme {
        // The background runs under the system bars; content stays inside them.
        Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) {
          CircuitCompositionLocals(circuit) {
            val backStack = rememberSaveableBackStack(root = BikeScreen())
            val navigator = rememberCircuitNavigator(backStack)
            NavigableCircuitContent(navigator, backStack, Modifier.safeDrawingPadding())
          }
        }
      }
    }
  }

  private companion object {
    val BLUETOOTH_PERMISSIONS =
      arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
  }
}
