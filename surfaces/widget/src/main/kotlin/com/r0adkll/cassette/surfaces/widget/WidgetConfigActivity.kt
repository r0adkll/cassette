package com.r0adkll.cassette.surfaces.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.r0adkll.cassette.bikes.ui.picker.BikeOption
import com.r0adkll.cassette.bikes.ui.picker.BikePicker
import com.r0adkll.cassette.bikes.ui.picker.bikeOptions
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "Which bike?" for one [BikeWidget]: shown by the launcher from the widget's settings (the widget
 * is reconfigurable, and configuration is optional, so a new widget starts on the default bike).
 */
class WidgetConfigActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val widgetId =
      intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    // Backing out leaves the widget as it was.
    setResult(RESULT_CANCELED, result(widgetId))
    if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
    val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(widgetId)
    val graph = widgets

    setContent {
      CassetteTheme {
        Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) {
          val loaded by
            produceState<Pair<List<BikeOption>, Long?>?>(null) {
              val repository = graph.bikeRepository
              val bikes =
                repository.observeBikes().first().mapNotNull {
                  repository.observeOverview(it.id).first()
                }
              val options =
                bikeOptions(repository.observeOverview(null).first(), bikes, graph.clock.now())
              val selected =
                getAppWidgetState<Preferences>(
                  this@WidgetConfigActivity,
                  BikeWidget().stateDefinition,
                  glanceId,
                )[BikeWidget.BIKE_ID]
              value = options to selected
            }
          loaded?.let { (options, selected) ->
            BikePicker(
              options,
              selected,
              onPick = { bikeId -> pick(glanceId, widgetId, bikeId) },
              onClose = ::finish,
            )
          }
        }
      }
    }
  }

  private fun pick(glanceId: androidx.glance.GlanceId, widgetId: Int, bikeId: Long?) {
    lifecycleScope.launch {
      updateAppWidgetState(this@WidgetConfigActivity, glanceId) {
        if (bikeId == null) it.remove(BikeWidget.BIKE_ID) else it[BikeWidget.BIKE_ID] = bikeId
      }
      BikeWidget().update(this@WidgetConfigActivity, glanceId)
      setResult(RESULT_OK, result(widgetId))
      finish()
    }
  }

  private fun result(widgetId: Int) =
    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}
