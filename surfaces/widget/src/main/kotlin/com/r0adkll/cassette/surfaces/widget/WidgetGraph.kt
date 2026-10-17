package com.r0adkll.cassette.surfaces.widget

import android.content.Context
import com.r0adkll.cassette.bikes.api.BikeRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlin.time.Clock

/** What widgets need from the app graph; the graph implements it through Metro. */
@ContributesTo(AppScope::class)
interface WidgetComponent {
  val bikeRepository: BikeRepository
  val clock: Clock
  val widgetUpdater: WidgetUpdater
}

/**
 * Implemented by the Application so widgets, which Android creates, reach the graph (decision
 * 0007).
 */
interface WidgetHost {
  val widgetComponent: WidgetComponent
}

internal val Context.widgets: WidgetComponent
  get() = (applicationContext as WidgetHost).widgetComponent
