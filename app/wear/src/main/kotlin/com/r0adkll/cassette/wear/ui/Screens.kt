package com.r0adkll.cassette.wear.ui

import android.os.Parcelable
import com.slack.circuit.runtime.screen.PopResult
import com.slack.circuit.runtime.screen.Screen
import kotlinx.parcelize.Parcelize

/** Every bike with its weakest status (iteration 5). */
@Parcelize data object BikesScreen : Screen, Parcelable

/** One bike: the status block, then a bar per component. */
@Parcelize data class BikeScreen(val bikeId: Long) : Screen, Parcelable

/** The watch app's update from Drop (iteration 9, direction A). */
@Parcelize data object UpdateScreen : Screen, Parcelable

/** Once: allow the app to install its own updates. */
@Parcelize data object AllowUpdatesScreen : Screen, Parcelable

/** A complication going on a face (decision 0027), first: which bike. */
@Parcelize data class PickBikeScreen(val complicationId: Int) : Screen, Parcelable

/** Then which of [bikeId]'s components it shows (null: the default bike's). */
@Parcelize
data class PickComponentScreen(val complicationId: Int, val bikeId: Long?) : Screen, Parcelable

/** What [PickComponentScreen] pops with once the pick is saved; the bike step passes it on. */
@Parcelize data object ComplicationPicked : PopResult, Parcelable
