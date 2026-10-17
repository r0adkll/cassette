package com.r0adkll.cassette.common.screens

import com.r0adkll.cassette.core.parcelize.Parcelize
import com.slack.circuit.runtime.screen.ParcelableScreen
import com.slack.circuit.runtime.screen.StaticScreen

/** A bike's overview. [bikeId] null means the default (most recently seen) bike (decision 0010). */
@Parcelize data class BikeScreen(val bikeId: Long? = null) : ParcelableScreen

@Parcelize data class ComponentScreen(val componentId: Long) : ParcelableScreen

@Parcelize data object AddBikeScreen : ParcelableScreen

@Parcelize data object SettingsScreen : ParcelableScreen

@Parcelize data object DiagnosticsScreen : ParcelableScreen

/** The design-system gallery, kept as a developer screen. No presenter. */
@Parcelize data object DesignGalleryScreen : StaticScreen, ParcelableScreen
