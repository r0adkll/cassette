package com.r0adkll.cassette.wear.ui

import android.os.Parcelable
import com.slack.circuit.runtime.screen.Screen
import kotlinx.parcelize.Parcelize

/** Every bike with its weakest status (iteration 5). */
@Parcelize data object BikesScreen : Screen, Parcelable

/** One bike: the status block, then a bar per component. */
@Parcelize data class BikeScreen(val bikeId: Long) : Screen, Parcelable
