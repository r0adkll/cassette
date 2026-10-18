package com.r0adkll.cassette.app.shared

import androidx.compose.runtime.Composable
import com.slack.circuit.backstack.SaveableBackStack
import com.slack.circuit.runtime.Navigator

/** Android leaves the app when the root screen is popped; iOS apps never close themselves. */
@Composable internal expect fun rememberAppNavigator(backStack: SaveableBackStack): Navigator
