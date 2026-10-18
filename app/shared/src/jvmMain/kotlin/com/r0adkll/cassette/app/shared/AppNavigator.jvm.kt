package com.r0adkll.cassette.app.shared

import androidx.compose.runtime.Composable
import com.slack.circuit.backstack.SaveableBackStack
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.runtime.Navigator

@Composable
internal actual fun rememberAppNavigator(backStack: SaveableBackStack): Navigator =
  rememberCircuitNavigator(backStack, onRootPop = {})
