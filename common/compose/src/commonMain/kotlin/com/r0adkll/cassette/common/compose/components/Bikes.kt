package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteRed
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone

/**
 * A bike in the tablet rail (decision 0014): its name, then status icon and word with when it was
 * seen. The selected bike is outlined in the text colour.
 */
@Composable
fun BikeRailItem(
  name: String,
  tone: StatusTone,
  seen: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = CassetteTheme.colors
  val shape = RoundedCornerShape(14.dp)
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(shape)
        .then(
          if (selected) Modifier.background(colors.panel).border(2.dp, colors.text, shape)
          else Modifier
        )
        .clickable(role = Role.Tab, onClick = onClick)
        .semantics { this.selected = selected }
        .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      name,
      style =
        CassetteTheme.type.numeral.copy(fontSize = CassetteTheme.type.numeral.fontSize * 0.92f),
      color = colors.text,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      StatusLabel(tone)
      Text(
        " · ${seen.uppercase()}",
        style = CassetteTheme.type.status,
        color = colors.muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * A bike in the open foldable's top bar (decision 0014): a status dot and its name. The chips float
 * over the content with the bar's buttons, so they stay when it scrolls. On iOS they're Liquid
 * Glass, the selected bike's tinted with the ink colour (decision 0017); elsewhere they're solid,
 * the selected bike's filled in the ink colour.
 */
@Composable
expect fun BikeChip(
  name: String,
  tone: StatusTone,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
)

/** [BikeChip] off iOS: filled, so it reads over the content scrolling under it. */
@Composable
internal fun SolidBikeChip(
  name: String,
  tone: StatusTone,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = CassetteTheme.colors
  val shape = CircleShape
  Row(
    modifier =
      modifier
        .height(ChipHeight)
        .clip(shape)
        .then(
          if (selected) Modifier.background(colors.ink)
          else Modifier.background(colors.panel).border(1.dp, colors.line, shape)
        )
        .clickable(role = Role.Tab, onClick = onClick)
        .semantics { this.selected = selected }
        .padding(horizontal = ChipPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ChipGap),
  ) {
    Box(Modifier.size(ChipDot).clip(CircleShape).background(colors.status(tone)))
    Text(
      name.uppercase(),
      style = chipLabelStyle,
      color = if (selected) colors.onInk else colors.muted,
      maxLines = 1,
    )
  }
}

internal val ChipHeight = 36.dp
internal val ChipPadding = 14.dp
internal val ChipDot = 8.dp
internal val ChipGap = 8.dp

/**
 * How a phone switches bikes (decision 0026), first in its floating top bar: how many bikes there
 * are, then, when others need charging, how many at their worst [attention] tone, with its icon and
 * word ("1 LOW"). Narrower than that (a small phone, large text), the word goes and the icon and
 * number stay. It opens the bikes sheet. Glass on iOS, filled elsewhere, as [BikeChip];
 * [description] is what screen readers say.
 */
@Composable
fun BikesPill(
  count: Int,
  attention: StatusTone?,
  attentionCount: Int,
  description: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val measurer = rememberTextMeasurer()
  val style = chipLabelStyle
  val density = LocalDensity.current
  BoxWithConstraints(modifier) {
    val full = attention?.let { pillAttention(it, attentionCount) }
    val fits =
      remember(count, full, style, density, maxWidth) {
        full == null ||
          with(density) {
            val row =
              pillRowWidth(
                measurer.measure(pillCount(count), style, maxLines = 1).size.width.toFloat(),
                measurer.measure(full, style, maxLines = 1).size.width.toFloat(),
              )
            (row.toDp() + PillPaddingStart + PillPaddingEnd) <= maxWidth
          }
      }
    PlatformBikesPill(
      count,
      attention,
      attentionLabel = if (fits) full else attention?.let { "$attentionCount" },
      description,
      onClick,
    )
  }
}

/** [BikesPill] as each platform draws it, with its attention label as it fits. */
@Composable
internal expect fun PlatformBikesPill(
  count: Int,
  attention: StatusTone?,
  attentionLabel: String?,
  description: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
)

/** [BikesPill] off iOS: filled, so it reads over the content scrolling under it. */
@Composable
internal fun SolidBikesPill(
  count: Int,
  attention: StatusTone?,
  attentionLabel: String?,
  description: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = CassetteTheme.colors
  Row(
    modifier
      .height(TopBarButtonSize)
      .clip(CircleShape)
      .background(colors.panel)
      .border(1.dp, colors.line, CircleShape)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { contentDescription = description }
      .padding(start = PillPaddingStart, end = PillPaddingEnd),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      Modifier.clearAndSetSemantics {},
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PillGap),
    ) {
      Icon(LucideIcons.Bike, null, tint = colors.text, modifier = Modifier.size(PillIcon))
      Text(pillCount(count), style = chipLabelStyle, color = colors.text, maxLines = 1)
      if (attention != null && attentionLabel != null) {
        val color = colors.status(attention)
        Box(Modifier.size(1.dp, PillDivider).background(colors.line))
        Icon(attention.icon, null, tint = color, modifier = Modifier.size(PillIcon))
        Text(attentionLabel, style = chipLabelStyle, color = color, maxLines = 1)
      }
      Icon(
        LucideIcons.ChevronDown,
        null,
        tint = colors.muted,
        modifier = Modifier.size(PillChevron),
      )
    }
  }
}

internal fun pillCount(count: Int) = "$count BIKES"

internal fun pillAttention(tone: StatusTone, count: Int) = "$count ${tone.word}"

/**
 * The pill's row in pixels without its padding: bike icon, count, then the divider, tone icon and
 * attention label if there is one, then the chevron, [PillGap] apart.
 */
internal fun Density.pillRowWidth(count: Float, attention: Float?): Float {
  val gap = PillGap.toPx()
  var width = PillIcon.toPx() + gap + count
  if (attention != null) width += gap + 1.dp.toPx() + gap + PillIcon.toPx() + gap + attention
  return width + gap + PillChevron.toPx()
}

internal val PillIcon = 18.dp
internal val PillChevron = 16.dp
internal val PillGap = 8.dp
internal val PillDivider = 18.dp
internal val PillPaddingStart = 14.dp
internal val PillPaddingEnd = 12.dp

internal val chipLabelStyle: TextStyle
  @Composable
  @ReadOnlyComposable
  get() = CassetteTheme.type.button.copy(fontSize = CassetteTheme.type.button.fontSize * 0.85f)

/**
 * A bike's card in Settings' garage (iteration 13, B2): its name, [details] ("Road · seen 2 min
 * ago"), a chip per component with its status dot, the bike's status, and OPENS FIRST on the bike
 * that always opens first. A [narrow] card (two across on a fold) moves the status and OPENS FIRST
 * to a line of their own, so the name keeps the full width.
 */
@Composable
fun BikeCard(
  name: String,
  details: String,
  tone: StatusTone,
  components: List<Pair<String, StatusTone>>,
  opensFirst: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  narrow: Boolean = false,
) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  val shape = RoundedCornerShape(20.dp)
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(shape)
        .background(colors.panel)
        .border(1.dp, colors.line, shape)
        .clickable(role = Role.Button, onClick = onClick)
  ) {
    Box(Modifier.padding(16.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              name,
              style = type.numeral.copy(fontSize = type.numeral.fontSize * 1.15f),
              color = colors.text,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(details, style = type.bodySmall, color = colors.muted)
          }
          if (opensFirst && !narrow) OpensFirstPill()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            components.forEach { (code, componentTone) -> ComponentChip(code, componentTone) }
          }
          if (!narrow) StatusLabel(tone)
        }
        if (narrow)
          Row(verticalAlignment = Alignment.CenterVertically) {
            StatusLabel(tone, Modifier.weight(1f))
            if (opensFirst) OpensFirstPill()
          }
      }
    }
  }
}

@Composable
private fun OpensFirstPill() {
  val colors = CassetteTheme.colors
  Row(
    Modifier.height(26.dp).clip(CircleShape).background(colors.ink).padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Icon(LucideIcons.Pin, null, tint = colors.onInk, modifier = Modifier.size(12.dp))
    Text(
      "OPENS FIRST",
      style = CassetteTheme.type.button.copy(fontSize = CassetteTheme.type.button.fontSize * 0.65f),
      color = colors.onInk,
    )
  }
}

/** A component's code with a dot in its status colour. */
@Composable
private fun ComponentChip(code: String, tone: StatusTone) {
  val colors = CassetteTheme.colors
  Row(
    Modifier.height(28.dp)
      .clip(RoundedCornerShape(9.dp))
      .background(colors.track)
      .padding(horizontal = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(colors.status(tone)))
    Text(
      code,
      style =
        CassetteTheme.type.numeral.copy(fontSize = CassetteTheme.type.numeral.fontSize * 0.5f),
      color = colors.text,
    )
  }
}

/** The tablet rail's dashed Add bike button. */
@Composable
fun RailAddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = CassetteTheme.colors
  val shape = RoundedCornerShape(22.dp)
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(44.dp)
        .clip(shape)
        .border(1.dp, colors.line, shape)
        .clickable(role = Role.Button, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
  ) {
    Icon(LucideIcons.BikePlus, null, tint = colors.muted, modifier = Modifier.size(16.dp))
    Text(text.uppercase(), style = CassetteTheme.type.button, color = colors.muted)
  }
}

/**
 * An outlined button at the foot of the tablet rail (Settings), the solid-lined partner of
 * [RailAddButton]. [badge] adds a red dot for something waiting behind it; [selected] fills it and
 * thickens the outline while its screen is open.
 */
@Composable
fun RailLink(
  icon: ImageVector,
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  badge: Boolean = false,
  selected: Boolean = false,
) {
  val colors = CassetteTheme.colors
  val shape = RoundedCornerShape(22.dp)
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(44.dp)
        .clip(shape)
        .then(
          if (selected) Modifier.background(colors.panel).border(2.dp, colors.text, shape)
          else Modifier.border(1.dp, colors.line, shape)
        )
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center,
  ) {
    // Centred like Add bike; the dot keeps to the end so it doesn't push them off centre.
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(icon, null, tint = colors.text, modifier = Modifier.size(16.dp))
      Text(text.uppercase(), style = CassetteTheme.type.button, color = colors.text)
    }
    if (badge)
      Box(Modifier.align(Alignment.CenterEnd).size(10.dp).clip(CircleShape).background(CassetteRed))
  }
}
