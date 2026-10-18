package com.r0adkll.cassette.bikes.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.ui.toLoaded
import com.r0adkll.cassette.common.compose.components.FloatingTopBar
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.StatusLabel
import com.r0adkll.cassette.common.compose.components.TopBarButton
import com.r0adkll.cassette.common.compose.components.TopBarButtonSize
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone
import kotlin.time.Instant

/** One choice in [BikePicker]. A null [bikeId] is "Default bike". */
data class BikeOption(
  val bikeId: Long?,
  val name: String,
  val detail: String,
  val tone: StatusTone?,
)

/**
 * The options for a widget: the default bike (following Settings, currently [default]) and then
 * every bike.
 */
fun bikeOptions(default: BikeOverview?, bikes: List<BikeOverview>, now: Instant): List<BikeOption> =
  listOf(
    BikeOption(
      null,
      "Default bike",
      default?.let { "Follows Settings · now ${it.bike.name}" } ?: "Follows Settings",
      null,
    )
  ) +
    bikes.map { overview ->
      val loaded = overview.toLoaded(now)
      BikeOption(
        overview.bike.id.value,
        overview.bike.name,
        "${loaded.status.headline} ${loaded.status.meta.replaceFirstChar { it.uppercase() }}",
        loaded.status.tone,
      )
    }

/** "Which bike?" for a widget: a tap picks and closes. */
@Composable
fun BikePicker(
  options: List<BikeOption>,
  selected: Long?,
  onPick: (Long?) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = CassetteTheme.colors
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      Modifier.widthIn(max = 480.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .safeDrawingPadding()
        .padding(horizontal = 20.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Close floats over this row (iteration 14).
      Row(Modifier.height(TopBarButtonSize), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        Kicker("Widget")
      }
      Text("Which bike?", style = CassetteTheme.type.display, color = colors.text)
      Text(
        "This widget shows one bike. The default follows the one you pick in Settings, or the one " +
          "you rode last.",
        style = CassetteTheme.type.body,
        color = colors.muted,
      )
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
          Panel(onClick = { onPick(option.bikeId) }) {
            Row(
              Modifier.padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(option.name, style = CassetteTheme.type.itemTitle, color = colors.text)
                Text(option.detail, style = CassetteTheme.type.bodySmall, color = colors.muted)
                option.tone?.let { StatusLabel(it, Modifier.padding(top = 4.dp)) }
              }
              if (option.bikeId == selected)
                Icon(
                  LucideIcons.Check,
                  contentDescription = "Selected",
                  tint = colors.text,
                  modifier = Modifier.size(20.dp),
                )
            }
          }
        }
      }
    }
    FloatingTopBar(
      Modifier.widthIn(max = 480.dp)
        .safeDrawingPadding()
        .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
      TopBarButton(LucideIcons.X, "Close", onClick = onClose)
    }
  }
}
