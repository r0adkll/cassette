package com.r0adkll.cassette.surfaces.widget

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.r0adkll.cassette.bikes.api.BikeRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Re-renders widgets whenever bike data changes in this process: captures (including background
 * wakes, which start the process), added or removed bikes, a new pinned bike. Debounced, since a
 * hub read writes several rows.
 */
@SingleIn(AppScope::class)
@Inject
class WidgetUpdater(private val application: Application, private val repository: BikeRepository) {
  @OptIn(FlowPreview::class)
  fun start(scope: CoroutineScope) {
    scope.launch {
      merge(repository.observeBikes(), repository.observeOverview(null))
        .drop(1)
        .debounce(1_000)
        .collect {
          BikeWidget().updateAll(application)
          AllBikesWidget().updateAll(application)
        }
    }
  }
}
