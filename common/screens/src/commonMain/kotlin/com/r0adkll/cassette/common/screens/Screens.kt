package com.r0adkll.cassette.common.screens

import com.r0adkll.cassette.core.parcelize.Parcelize
import com.slack.circuit.runtime.screen.ParcelableScreen
import com.slack.circuit.runtime.screen.StaticScreen

/** A bike's overview. [bikeId] null means the default (most recently seen) bike (decision 0010). */
@Parcelize data class BikeScreen(val bikeId: Long? = null) : ParcelableScreen

@Parcelize data class ComponentScreen(val componentId: Long) : ParcelableScreen

@Parcelize data object AddBikeScreen : ParcelableScreen

/** First run (decision 0011): the welcome, then [OnboardingPermissionsScreen], then add-bike. */
@Parcelize data object OnboardingScreen : ParcelableScreen

/** First run: both permissions explained and asked for on one screen. */
@Parcelize data object OnboardingPermissionsScreen : ParcelableScreen

@Parcelize data object SettingsScreen : ParcelableScreen

@Parcelize data object DiagnosticsScreen : ParcelableScreen

@Parcelize data object LicencesScreen : ParcelableScreen

@Parcelize data object LiveAdvertsScreen : ParcelableScreen

@Parcelize data object RideTestsScreen : ParcelableScreen

/** The hub tables of [bikeId]'s hub, read on open. */
@Parcelize data class HubTablesScreen(val bikeId: Long) : ParcelableScreen

/** The design-system gallery, kept as a developer screen. No presenter. */
@Parcelize data object DesignGalleryScreen : StaticScreen, ParcelableScreen
