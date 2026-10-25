package com.r0adkll.cassette.wear.complication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import com.r0adkll.cassette.wear.WearApplication
import com.r0adkll.cassette.wear.ui.ComplicationPicked
import com.r0adkll.cassette.wear.ui.PickBikeScreen
import com.r0adkll.cassette.wear.ui.SwipeDismissDecoration
import com.r0adkll.cassette.wear.ui.WearTheme
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator

/**
 * Which bike, then which component, for a complication going on a face (decision 0027). The face
 * opens it with the complication's id; a pick saves and returns OK, and backing out cancels, which
 * leaves the face without it (or as it was, when it was already there).
 */
class ComplicationConfigActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setResult(RESULT_CANCELED)
    if (!intent.hasExtra(ComplicationDataSourceService.EXTRA_CONFIG_COMPLICATION_ID))
      return finish()
    val complicationId =
      intent.getIntExtra(ComplicationDataSourceService.EXTRA_CONFIG_COMPLICATION_ID, 0)
    val circuit = (application as WearApplication).graph.circuit
    setContent {
      WearTheme {
        AppScaffold {
          CircuitCompositionLocals(circuit) {
            val backStack = rememberSaveableBackStack(PickBikeScreen(complicationId))
            val navigator =
              rememberCircuitNavigator(
                backStack,
                onRootPop = { result ->
                  if (result is ComplicationPicked) {
                    setResult(RESULT_OK)
                    BikeComplicationService.update(this@ComplicationConfigActivity, complicationId)
                  }
                  finish()
                },
              )
            NavigableCircuitContent(navigator, backStack, decoration = SwipeDismissDecoration)
          }
        }
      }
    }
  }
}
