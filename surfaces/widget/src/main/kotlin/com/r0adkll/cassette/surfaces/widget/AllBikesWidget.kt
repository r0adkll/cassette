package com.r0adkll.cassette.surfaces.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.r0adkll.cassette.bikes.summary.weakest
import com.r0adkll.cassette.bikes.ui.BikeUiState
import com.r0adkll.cassette.bikes.ui.toLoaded
import com.r0adkll.cassette.common.compose.components.word
import com.r0adkll.cassette.common.screens.DeepLinks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Every bike as a row tinted by its weakest status (decision 0010). */
class AllBikesWidget : GlanceAppWidget() {
  override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val graph = context.widgets
    val flow: Flow<List<BikeUiState.Loaded>> =
      graph.bikeRepository.observeBikes().flatMapLatest { bikes ->
        if (bikes.isEmpty()) flowOf(emptyList())
        else
          combine(bikes.map { graph.bikeRepository.observeOverview(it.id) }) { overviews ->
            overviews.filterNotNull().map { it.toLoaded(graph.clock.now()) }
          }
      }
    val initial = flow.first()
    provideContent {
      val bikes by remember { flow }.collectAsState(initial)
      Content(bikes)
    }
  }

  @Composable
  private fun Content(bikes: List<BikeUiState.Loaded>) {
    Column(
      GlanceModifier.fillMaxSize()
        .cornerRadius(26.dp)
        .background(WidgetColors.surface)
        .padding(horizontal = 16.dp, vertical = 14.dp)
        .clickable(launchApp(LocalContext.current))
    ) {
      Text(
        "ALL BIKES",
        style =
          TextStyle(
            fontFamily = Condensed,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = WidgetColors.muted,
          ),
      )
      Spacer(GlanceModifier.height(8.dp))
      if (bikes.isEmpty()) {
        Text("No bikes yet.", style = TextStyle(fontSize = 14.sp, color = WidgetColors.muted))
      }
      // Glance caps a Column at 10 children.
      Column(GlanceModifier.fillMaxWidth()) {
        bikes.take(10).forEach { bike ->
          val tone = bike.status.tone
          Row(
            GlanceModifier.fillMaxWidth()
              .padding(bottom = 6.dp)
              .height(54.dp)
              .clickable(openLink(LocalContext.current, DeepLinks.bike(bike.bikeId)))
              .cornerRadius(18.dp)
              .background(
                ColorProvider(
                  day = WidgetColors.thickFill(tone, night = false),
                  night = WidgetColors.thickFill(tone, night = true),
                )
              )
              .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            DisplayText(bike.name, 20f, textColor(), LocalSize.current.width - 150.dp)
            Spacer(GlanceModifier.defaultWeight())
            val weakest = bike.components.weakest()
            Text(
              if (
                tone == com.r0adkll.cassette.common.compose.theme.StatusTone.Good || weakest == null
              )
                tone.word
              else "${weakest.code} ${weakest.tone.word}",
              style =
                TextStyle(
                  fontFamily = Condensed,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  color = WidgetColors.text,
                ),
            )
          }
        }
      }
    }
  }
}

class AllBikesWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget = AllBikesWidget()
}
