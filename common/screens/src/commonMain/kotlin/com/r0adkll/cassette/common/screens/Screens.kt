package com.r0adkll.cassette.common.screens

import com.r0adkll.cassette.core.parcelize.Parcelize
import com.slack.circuit.runtime.screen.ParcelableScreen
import com.slack.circuit.runtime.screen.StaticScreen

/** A bike's overview. [bikeId] null means the default (most recently seen) bike (decision 0010). */
@Parcelize data class BikeScreen(val bikeId: Long? = null) : ParcelableScreen

/**
 * A component's detail. [inSheet] shows it inside the bike dashboard's sheet (decision 0014), which
 * draws its own header, so the screen leaves out its back button.
 */
@Parcelize
data class ComponentScreen(val componentId: Long, val inSheet: Boolean = false) : ParcelableScreen

@Parcelize data object AddBikeScreen : ParcelableScreen

/** First run (decision 0011): the welcome, then [OnboardingPermissionsScreen], then add-bike. */
@Parcelize data object OnboardingScreen : ParcelableScreen

/** First run: both permissions explained and asked for on one screen. */
@Parcelize data object OnboardingPermissionsScreen : ParcelableScreen

/** Settings' categories, in list order (decision 0014). */
enum class SettingsCategory {
  Bikes,
  Capture,
  Notifications,
  Appearance,
  Diagnostics,
  Updates,
  /** Version, who made it, help, the legal pages and licences. */
  About,
}

/**
 * Settings (decision 0014). On a phone, no [category] is the list of categories and a [category] is
 * that category's own screen; on wide windows the list sits beside the open category, [category] or
 * the first.
 */
@Parcelize data class SettingsScreen(val category: SettingsCategory? = null) : ParcelableScreen

/** A bike's own settings page (iteration 13): name and type, opens first, components, remove. */
@Parcelize data class BikeSettingsScreen(val bikeId: Long) : ParcelableScreen

/**
 * Diagnostics. [inPane] shows it inside Settings' pane on a wide window, where it has no back
 * button; its tools still open within the pane.
 */
@Parcelize data class DiagnosticsScreen(val inPane: Boolean = false) : ParcelableScreen

/** Third-party notices, from Settings → About. */
@Parcelize data object LicencesScreen : ParcelableScreen

@Parcelize data object LiveAdvertsScreen : ParcelableScreen

/** Ride tests: the one recording, the profiles compared and past tests. */
@Parcelize data object RideTestsScreen : ParcelableScreen

/** A new ride test's setup: step 1 (what to test, the name) and 2 (the checks), then Start. */
@Parcelize data object RideTestSetupScreen : ParcelableScreen

/** The test that's recording; leaving it keeps the test running. */
@Parcelize data object RideTestRecordingScreen : ParcelableScreen

/** How the test that started at [startedAt] (epoch milliseconds) went. */
@Parcelize data class RideTestResultScreen(val startedAt: Long) : ParcelableScreen

/** The hub tables of [bikeId]'s hub, read on open. */
@Parcelize data class HubTablesScreen(val bikeId: Long) : ParcelableScreen

/** The design-system gallery, kept as a developer screen. No presenter. */
@Parcelize data object DesignGalleryScreen : StaticScreen, ParcelableScreen
