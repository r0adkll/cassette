package com.r0adkll.cassette.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.AppScaffold
import com.r0adkll.cassette.wear.ui.BikeScreen
import com.r0adkll.cassette.wear.ui.BikesScreen
import com.r0adkll.cassette.wear.ui.SwipeDismissDecoration
import com.r0adkll.cassette.wear.ui.WearTheme
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator

class WearActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val circuit = (application as WearApplication).graph.circuit
    // The tile opens its bike; a restored activity keeps its own back stack.
    val bike = intent.getLongExtra(EXTRA_BIKE, -1).takeIf { it >= 0 && savedInstanceState == null }
    setContent {
      WearTheme {
        AppScaffold {
          CircuitCompositionLocals(circuit) {
            val backStack =
              rememberSaveableBackStack(BikesScreen) { bike?.let { push(BikeScreen(it)) } }
            // The system's back (swipe on the root, or the button) closes the app.
            val navigator = rememberCircuitNavigator(backStack, onRootPop = { finish() })
            NavigableCircuitContent(navigator, backStack, decoration = SwipeDismissDecoration)
          }
        }
      }
    }
  }

  companion object {
    const val EXTRA_BIKE = "bike_id"
  }
}
