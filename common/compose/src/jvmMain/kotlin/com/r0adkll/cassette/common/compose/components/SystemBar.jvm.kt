package com.r0adkll.cassette.common.compose.components

import androidx.compose.runtime.Composable

/** Renders have no system bar for a screen's actions: screens float their own. */
@Composable actual fun hasSystemActionBar(): Boolean = false

@Composable actual fun SystemBarActions(actions: List<BarAction>, placement: BarPlacement) {}
