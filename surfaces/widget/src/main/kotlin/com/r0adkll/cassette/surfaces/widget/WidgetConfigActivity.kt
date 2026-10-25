package com.r0adkll.cassette.surfaces.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.r0adkll.cassette.bikes.ui.picker.BikeOption
import com.r0adkll.cassette.bikes.ui.picker.BikePicker
import com.r0adkll.cassette.bikes.ui.picker.ComponentOption
import com.r0adkll.cassette.bikes.ui.picker.ComponentPicker
import com.r0adkll.cassette.bikes.ui.picker.bikeOptions
import com.r0adkll.cassette.bikes.ui.picker.componentOptions
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.currentScreenLayout
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.core.BikeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "Which bike?", then "Which component?" for one [BikeWidget] (decision 0027): shown by the
 * launcher from the widget's settings (the widget is reconfigurable, and configuration is optional,
 * so a new widget starts on the default bike and its weakest component).
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
        CompositionLocalProvider(LocalScreenLayout provides currentScreenLayout()) {
          Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) {
            val saved by
              produceState<Preferences?>(null) {
                value =
                  getAppWidgetState(
                    this@WidgetConfigActivity,
                    BikeWidget().stateDefinition,
                    glanceId,
                  )
              }
            val bikes by
              produceState<List<BikeOption>?>(null) {
                val repository = graph.bikeRepository
                val overviews =
                  repository.observeBikes().first().mapNotNull {
                    repository.observeOverview(it.id).first()
                  }
                value =
                  bikeOptions(
                    repository.observeOverview(null).first(),
                    overviews,
                    graph.clock.now(),
                  )
              }
            // The first step's answer, then the second step asks for its component.
            var choosing by rememberSaveable { mutableStateOf(false) }
            var bikeId by rememberSaveable { mutableStateOf<Long?>(null) }
            val state = saved
            val options = bikes
            if (state != null && options != null) {
              if (!choosing) {
                BikePicker(
                  options,
                  state[BikeWidget.BIKE_ID],
                  onPick = {
                    bikeId = it
                    choosing = true
                  },
                  onClose = ::finish,
                )
              } else {
                BackHandler { choosing = false }
                val components by
                  produceState<List<ComponentOption>?>(null, bikeId) {
                    val overview =
                      graph.bikeRepository.observeOverview(bikeId?.let(::BikeId)).first()
                    value = componentOptions(overview, graph.clock.now())
                  }
                components?.let {
                  ComponentPicker(
                    bikeId?.let { id ->
                      options.firstOrNull { option -> option.bikeId == id }?.name
                    },
                    it,
                    state[BikeWidget.COMPONENT_KIND],
                    onPick = { kind -> save(glanceId, widgetId, bikeId, kind) },
                    onBack = { choosing = false },
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  private fun save(glanceId: GlanceId, widgetId: Int, bikeId: Long?, kind: String?) {
    lifecycleScope.launch {
      updateAppWidgetState(this@WidgetConfigActivity, glanceId) {
        if (bikeId == null) it.remove(BikeWidget.BIKE_ID) else it[BikeWidget.BIKE_ID] = bikeId
        if (kind == null) it.remove(BikeWidget.COMPONENT_KIND)
        else it[BikeWidget.COMPONENT_KIND] = kind
      }
      BikeWidget().update(this@WidgetConfigActivity, glanceId)
      setResult(RESULT_OK, result(widgetId))
      finish()
    }
  }

  private fun result(widgetId: Int) =
    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}
