package com.r0adkll.cassette.surfaces.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.r0adkll.cassette.bikes.ui.BikeUiState
import com.r0adkll.cassette.bikes.ui.ComponentRowModel
import com.r0adkll.cassette.bikes.ui.toLoaded
import com.r0adkll.cassette.common.compose.components.word
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The default bike, one widget that adapts to its size (decision 0010): small shows the status
 * block, medium a bar per component, large a thick bar per component with the value inside.
 */
class BikeWidget : GlanceAppWidget() {
  // Exact, not Responsive: the bars are bitmaps drawn at the widget's real size, and Responsive
  // only
  // reports the size bucket.
  override val sizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val graph = context.widgets
    val flow = graph.bikeRepository.observeOverview(null).map { it?.toLoaded(graph.clock.now()) }
    val initial = flow.first()
    provideContent {
      val state by remember { flow }.collectAsState(initial)
      Content(state)
    }
  }

  @Composable
  private fun Content(state: BikeUiState.Loaded?) {
    val size = LocalSize.current
    val open = launchApp(LocalContext.current)
    val modifier =
      GlanceModifier.fillMaxSize()
        .cornerRadius(26.dp)
        .background(WidgetColors.surface)
        .clickable(open)
    when {
      state == null -> Empty(modifier)
      size.height < MEDIUM.height || size.width < MEDIUM.width ->
        Small(state, GlanceModifier.fillMaxSize().clickable(open))
      size.height < LARGE.height -> Medium(state, modifier)
      else -> Large(state, modifier)
    }
  }

  @Composable
  private fun Empty(modifier: GlanceModifier) {
    Column(modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(
        "No bike yet.",
        style =
          TextStyle(
            fontFamily = Condensed,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = WidgetColors.text,
          ),
      )
      Text(
        "Open Cassette to add one.",
        style = TextStyle(fontSize = 12.sp, color = WidgetColors.muted),
      )
    }
  }

  /** The status block: bike, headline, and the weakest component or the count. */
  @Composable
  private fun Small(state: BikeUiState.Loaded, modifier: GlanceModifier) {
    val tone = state.status.tone
    val content = WidgetColors.blockContent(tone)
    Column(
      modifier.cornerRadius(26.dp).background(WidgetColors.blockFill(tone)).padding(16.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Text(
        state.name.uppercase(),
        style =
          TextStyle(
            fontFamily = Condensed,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = content,
          ),
        maxLines = 1,
      )
      Spacer(GlanceModifier.defaultWeight())
      Text(
        state.status.headline,
        style =
          TextStyle(
            fontFamily = Condensed,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            color = content,
          ),
        maxLines = 2,
      )
      Spacer(GlanceModifier.defaultWeight())
      val weakest = state.components.weakest()
      Text(
        weakest?.let { "${it.code} ${it.value} · ${it.tone.word}" } ?: state.status.meta,
        style =
          TextStyle(
            fontFamily = Condensed,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = content,
          ),
        maxLines = 1,
      )
    }
  }

  /** One vertical bar per component. */
  @Composable
  private fun Medium(state: BikeUiState.Loaded, modifier: GlanceModifier) {
    val context = LocalContext.current
    val night = context.isNight()
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val count = state.components.size.coerceAtLeast(1)
    // The layout shares out the space (weights); the bitmap is drawn near that size and stretched
    // to fit, since hosts add their own padding we can't see.
    val barWidth = ((size.width.value - 32) / count - GAP).coerceAtLeast(16f).dp
    val barHeight = (size.height.value - 90).coerceAtLeast(24f).dp
    Column(modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
      Header(state)
      Row(GlanceModifier.fillMaxWidth().defaultWeight().padding(top = 10.dp)) {
        state.components.forEachIndexed { i, row ->
          Column(
            GlanceModifier.defaultWeight()
              .fillMaxHeight()
              .padding(start = if (i > 0) GAP.dp else 0.dp)
          ) {
            Image(
              ImageProvider(
                barBitmap(
                  barWidth,
                  barHeight,
                  density,
                  row.level,
                  WidgetColors.thickTrack(night),
                  status(row, night),
                  vertical = true,
                )
              ),
              contentDescription = "${row.name} ${row.value}",
              contentScale = ContentScale.FillBounds,
              modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            )
            Row(GlanceModifier.fillMaxWidth().padding(top = 3.dp)) {
              Text(
                row.code,
                style =
                  TextStyle(
                    fontFamily = Condensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = WidgetColors.muted,
                  ),
                modifier = GlanceModifier.defaultWeight(),
              )
              // Compact, but an estimate keeps its ≈ (decision 0003).
              Text(
                row.value.removeSuffix("%").removeSuffix(" V"),
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

  /** Thick bars with the name and value inside (after Pixel's battery widget). */
  @Composable
  private fun Large(state: BikeUiState.Loaded, modifier: GlanceModifier) {
    val context = LocalContext.current
    val night = context.isNight()
    val width = (LocalSize.current.width.value - 32).dp
    Column(modifier.padding(16.dp)) {
      Header(state)
      // Glance caps a Column at 10 children: rows go in their own column and space themselves.
      Column(GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
        state.components.take(MAX_ROWS).forEach { row ->
          Box(
            GlanceModifier.fillMaxWidth().height(52.dp).padding(bottom = 6.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            Image(
              ImageProvider(
                barBitmap(
                  width,
                  46.dp,
                  context.resources.displayMetrics.density,
                  row.level,
                  WidgetColors.thickTrack(night),
                  WidgetColors.thickFill(row.tone, night),
                )
              ),
              contentDescription = null,
              modifier = GlanceModifier.fillMaxWidth().height(46.dp),
            )
            Row(
              GlanceModifier.fillMaxWidth().padding(horizontal = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                row.code,
                style =
                  TextStyle(
                    fontFamily = Condensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = WidgetColors.text,
                  ),
                modifier = GlanceModifier.width(32.dp),
              )
              Text(
                row.name,
                style =
                  TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = WidgetColors.text,
                  ),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
              )
              Text(
                row.value,
                style =
                  TextStyle(
                    fontFamily = Condensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = WidgetColors.text,
                  ),
              )
            }
          }
        }
      }
    }
  }

  @Composable
  private fun Header(state: BikeUiState.Loaded) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
        state.name,
        style =
          TextStyle(
            fontFamily = Condensed,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = WidgetColors.text,
          ),
        modifier = GlanceModifier.defaultWeight(),
        maxLines = 1,
      )
      Text(
        "${state.status.tone.word} · ${state.status.meta.removePrefix("Seen ").uppercase()}",
        style =
          TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = WidgetColors.status(state.status.tone),
          ),
        maxLines = 1,
      )
    }
  }

  private fun status(row: ComponentRowModel, night: Boolean) =
    if (night) com.r0adkll.cassette.common.compose.theme.DarkCassetteColors.status(row.tone)
    else com.r0adkll.cassette.common.compose.theme.LightCassetteColors.status(row.tone)

  companion object {
    val SMALL = DpSize(110.dp, 110.dp)
    val MEDIUM = DpSize(250.dp, 110.dp)
    val LARGE = DpSize(250.dp, 280.dp)
    const val GAP = 8
    /** Glance's 10-child cap per container. */
    const val MAX_ROWS = 10
  }
}

class BikeWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget = BikeWidget()
}

/** Worst first; null when nothing has a reading. */
internal fun List<ComponentRowModel>.weakest(): ComponentRowModel? = filter {
  it.value != "—"
}
  .minByOrNull { it.level }

internal fun Context.isNight() =
  resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
    Configuration.UI_MODE_NIGHT_YES

internal fun launchApp(context: Context) =
  actionStartActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!)
