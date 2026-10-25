package com.r0adkll.cassette.bikes.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.summary.weakest
import com.r0adkll.cassette.bikes.ui.toLoaded
import com.r0adkll.cassette.common.compose.components.CodeBadge
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.StatusLabel
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone
import kotlin.time.Instant

/**
 * One choice in [ComponentPicker]: a component by its kind's name (what the widget keeps, so the
 * pick outlives a replaced part), or "Weakest" when [kind] is null.
 */
data class ComponentOption(
  val kind: String?,
  val code: String?,
  val name: String,
  val detail: String,
  val value: String?,
  val tone: StatusTone?,
)

/**
 * The options for a widget's small size (decision 0027): the weakest, then each component of [bike]
 * (the bike picked, or the default bike), one per kind.
 */
fun componentOptions(bike: BikeOverview?, now: Instant): List<ComponentOption> {
  val components = bike?.toLoaded(now)?.components.orEmpty()
  val weakest = components.weakest()
  return listOf(
    ComponentOption(
      kind = null,
      code = null,
      name = "Weakest",
      detail =
        weakest?.let { "Whichever is lowest · now ${it.code} ${it.value}" }
          ?: "Whichever is lowest",
      value = null,
      tone = null,
    )
  ) +
    components
      .distinctBy { it.kind }
      .map { ComponentOption(it.kind.name, it.code, it.name, it.source, it.value, it.tone) }
}

/**
 * "Which component?" for a widget, after [BikePicker]: the one its small size names while
 * everything is fine (decision 0027). A tap picks; back returns to the bikes.
 */
@Composable
fun ComponentPicker(
  bikeName: String?,
  options: List<ComponentOption>,
  selected: String?,
  onPick: (String?) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  PickerLayout(
    kicker = listOfNotNull("Widget", bikeName).joinToString(" · "),
    title = "Which component?",
    body =
      "When the widget is small, it shows this one while everything’s fine. If something needs " +
        "charging, it shows that instead.",
    navigation = LucideIcons.ArrowLeft to "Back",
    onNavigation = onBack,
    modifier = modifier,
  ) {
    options.forEach { option ->
      Panel(onClick = { onPick(option.kind) }) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (option.code != null) CodeBadge(option.code) else WeakestBadge()
          Column(Modifier.weight(1f)) {
            Text(
              option.name,
              style = type.itemTitle,
              color = colors.text,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(option.detail, style = type.bodySmall, color = colors.muted)
          }
          if (option.value != null && option.tone != null) {
            Column(horizontalAlignment = Alignment.End) {
              Text(option.value, style = type.numeral, color = colors.text)
              StatusLabel(option.tone)
            }
          }
          if (option.kind == selected) Selected()
        }
      }
    }
  }
}

/** [CodeBadge]'s tile with the "lowest first" icon, for "Weakest". */
@Composable
private fun WeakestBadge() {
  Box(
    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CassetteTheme.colors.track),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      LucideIcons.ArrowDownWideNarrow,
      contentDescription = null,
      tint = CassetteTheme.colors.text,
      modifier = Modifier.size(20.dp),
    )
  }
}
