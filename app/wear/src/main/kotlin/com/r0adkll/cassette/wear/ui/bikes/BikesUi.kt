package com.r0adkll.cassette.wear.ui.bikes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.wear.ui.BikesScreen
import com.r0adkll.cassette.wear.ui.WearTheme
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope

/** Every bike with its weakest status; tap for its components (iteration 5). */
@CircuitInject(BikesScreen::class, AppScope::class)
@Composable
fun BikesUi(state: BikesUiState, modifier: Modifier = Modifier) {
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
          Text("CASSETTE", style = type.kicker, color = colors.muted)
        }
      }
      when (state) {
        BikesUiState.Loading -> Unit
        is BikesUiState.Empty ->
          item {
            Text(
              if (state.synced) "No bikes yet. Add one in Cassette on your phone."
              else "Open Cassette on your phone to see your bikes here.",
              style = type.body,
              color = colors.muted,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 12.dp),
            )
          }
        is BikesUiState.Loaded ->
          items(state.bikes, key = { it.id }) { bike ->
            val tone = colors.status(bike.tone)
            Button(
              onClick = { state.eventSink(BikesEvent.Open(bike.id)) },
              modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
              transformation = SurfaceTransformation(spec),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = colors.track,
                  contentColor = colors.text,
                  secondaryContentColor = tone,
                  iconColor = tone,
                ),
              icon = { Icon(bike.tone.icon, contentDescription = null) },
              secondaryLabel = { Text(bike.label, style = type.label) },
            ) {
              Text(bike.name, style = type.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
      }
    }
  }
}
