package com.r0adkll.cassette.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.BikeScreen
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val circuit = (application as CassetteApplication).graph.circuit
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
}
