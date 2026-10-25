package com.r0adkll.cassette.wear.ui.pick

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.r0adkll.cassette.common.compose.components.icon
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.wear.ui.PickBikeScreen
import com.r0adkll.cassette.wear.ui.PickComponentScreen
import com.r0adkll.cassette.wear.ui.WearTheme
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope

@CircuitInject(PickBikeScreen::class, AppScope::class)
@Composable
fun PickBikeUi(state: PickUiState, modifier: Modifier = Modifier) = PickList(state, modifier)

@CircuitInject(PickComponentScreen::class, AppScope::class)
@Composable
fun PickComponentUi(state: PickUiState, modifier: Modifier = Modifier) = PickList(state, modifier)

/** The question over its choices, a tap each, in the bike list's buttons (iteration 18). */
@Composable
private fun PickList(state: PickUiState, modifier: Modifier = Modifier) {
  val colors = CassetteTheme.colors
  val type = WearTheme.type
  val listState = rememberTransformingLazyColumnState()
  val spec = rememberTransformationSpec()
  ScreenScaffold(scrollState = listState, modifier = modifier) { padding ->
    TransformingLazyColumn(state = listState, contentPadding = padding) {
      item {
        ListHeader(
          Modifier.transformedHeight(this, spec),
          transformation = SurfaceTransformation(spec),
        ) {
          Text(state.title, style = type.kicker, color = colors.muted)
        }
      }
      items(state.choices, key = { it.event.toString() }) { choice ->
        val tone = choice.tone?.let(colors::status) ?: colors.muted
        Button(
          onClick = { state.eventSink(choice.event) },
          modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
          transformation = SurfaceTransformation(spec),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = colors.track,
              contentColor = colors.text,
              secondaryContentColor = tone,
              iconColor = if (choice.leading == Leading.DefaultBike) colors.text else tone,
            ),
          // The current pick is outlined, as the phone's selected panels are.
          border = if (choice.selected) BorderStroke(2.dp, colors.text) else null,
          icon = {
            when (val leading = choice.leading) {
              Leading.DefaultBike -> Icon(LucideIcons.Bike, contentDescription = null)
              Leading.Status ->
                Icon((choice.tone ?: StatusTone.Unknown).icon, contentDescription = null)
              Leading.Weakest -> Icon(LucideIcons.ArrowDownWideNarrow, contentDescription = null)
              is Leading.Code ->
                Box(
                  Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(colors.line),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(leading.code, style = type.code, color = tone)
                }
            }
          },
          secondaryLabel = { Text(choice.label, style = type.label) },
        ) {
          Text(choice.name, style = type.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
