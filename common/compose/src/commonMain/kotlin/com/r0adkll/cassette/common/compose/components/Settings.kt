package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme

/** A labelled group of rows in one panel (Settings, Diagnostics). */
@Composable
fun SettingsSection(
  label: String,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Kicker(label)
    Panel(content = content)
  }
}

/** A row: optional icon, title and subtitle, and a trailing control (chevron, toggle, text). */
@Composable
fun SettingsRow(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  onClick: (() -> Unit)? = null,
  divider: Boolean = true,
  trailing: @Composable () -> Unit = { if (onClick != null) Chevron() },
) {
  RowLayout(
    title,
    modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    subtitle,
    icon,
    divider,
    trailing,
  )
}

/** A row that is one switch: tapping anywhere on it toggles [checked]. */
@Composable
fun SettingsSwitchRow(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  divider: Boolean = true,
) {
  RowLayout(
    title,
    modifier.toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
    subtitle,
    icon,
    divider,
  ) {
    Toggle(checked, onCheckedChange = null)
  }
}

/**
 * The row itself. Its touch area and press ripple reach the edges of the section's panel (past the
 * panel's padding), and it is at least 52dp tall.
 */
@Composable
private fun RowLayout(
  title: String,
  interaction: Modifier,
  subtitle: String?,
  icon: ImageVector?,
  divider: Boolean,
  trailing: @Composable () -> Unit,
) {
  val colors = CassetteTheme.colors
  Column {
    Row(
      Modifier.bleedHorizontally(PanelPadding)
        .heightIn(min = 52.dp)
        .then(interaction)
        .padding(horizontal = PanelPadding, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (icon != null) Icon(icon, null, tint = colors.text, modifier = Modifier.size(20.dp))
      Column(Modifier.weight(1f)) {
        Text(title, style = CassetteTheme.type.itemTitle, color = colors.text)
        if (subtitle != null)
          Text(subtitle, style = CassetteTheme.type.bodySmall, color = colors.muted)
      }
      trailing()
    }
    if (divider) HorizontalDivider(color = colors.line, thickness = 1.dp)
  }
}

@Composable
fun Chevron() {
  Icon(
    LucideIcons.ChevronRight,
    null,
    tint = CassetteTheme.colors.muted,
    modifier = Modifier.size(18.dp),
  )
}

/** The panel's horizontal padding, which rows bleed through. */
private val PanelPadding = 14.dp

private fun Modifier.bleedHorizontally(amount: Dp) = layout { measurable, constraints ->
  if (!constraints.hasBoundedWidth)
    return@layout measurable.measure(constraints).run {
      layout(width, height) { place(0, 0) }
    }
  val extra = amount.roundToPx() * 2
  val placeable =
    measurable.measure(
      constraints.copy(
        minWidth = constraints.maxWidth + extra,
        maxWidth = constraints.maxWidth + extra,
      )
    )
  layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * An on/off switch in ink and track colours. A null [onCheckedChange] draws it only, for a row that
 * handles the tap ([SettingsSwitchRow]).
 */
@Composable
fun Toggle(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
  val colors = CassetteTheme.colors
  Box(
    modifier
      .width(44.dp)
      .height(26.dp)
      .clip(RoundedCornerShape(13.dp))
      .background(if (checked) colors.ink else colors.track)
      .then(
        if (onCheckedChange != null)
          Modifier.clickable(role = Role.Switch) { onCheckedChange(!checked) }
        else Modifier
      )
      .padding(3.dp),
    contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
  ) {
    Box(
      Modifier.size(20.dp).clip(CircleShape).background(if (checked) colors.onInk else colors.muted)
    )
  }
}

/** A row of choices, one selected (filters, scan profiles). */
@Composable
fun <T> ChoiceChips(
  options: List<T>,
  selected: T,
  label: (T) -> String,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = CassetteTheme.colors
  androidx.compose.foundation.layout.FlowRow(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    options.forEach { option ->
      val on = option == selected
      val shape = RoundedCornerShape(15.dp)
      Text(
        label(option).uppercase(),
        style = CassetteTheme.type.label.copy(fontSize = CassetteTheme.type.label.fontSize * 0.92f),
        color = if (on) colors.onInk else colors.text,
        modifier =
          Modifier.clip(shape)
            .then(
              if (on) Modifier.background(colors.ink) else Modifier.border(1.dp, colors.line, shape)
            )
            .clickable { onSelect(option) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
      )
    }
  }
}
