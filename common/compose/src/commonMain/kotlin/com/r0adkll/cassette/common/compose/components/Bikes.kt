package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
 * A bike in the open foldable's top bar (decision 0014): a status dot and its name. The selected
 * bike is filled in the ink colour.
 */
@Composable
fun BikeChip(
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
        .height(36.dp)
        .clip(shape)
        .then(
          if (selected) Modifier.background(colors.ink)
          else Modifier.border(1.dp, colors.line, shape)
        )
        .clickable(role = Role.Tab, onClick = onClick)
        .semantics { this.selected = selected }
        .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(colors.status(tone)))
    Text(
      name.uppercase(),
      style = CassetteTheme.type.button.copy(fontSize = CassetteTheme.type.button.fontSize * 0.85f),
      color = if (selected) colors.onInk else colors.muted,
      maxLines = 1,
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
 * A plain row at the foot of the tablet rail (Settings). [badge] adds a red dot for something
 * waiting behind it; [selected] outlines it while its screen is open.
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
  val shape = RoundedCornerShape(14.dp)
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(44.dp)
        .clip(shape)
        .then(
          if (selected) Modifier.background(colors.panel).border(2.dp, colors.text, shape)
          else Modifier
        )
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(icon, null, tint = colors.text, modifier = Modifier.size(18.dp))
    Text(
      text,
      style = CassetteTheme.type.itemTitle,
      color = colors.text,
      modifier = Modifier.weight(1f),
    )
    if (badge) Box(Modifier.size(10.dp).clip(CircleShape).background(CassetteRed))
  }
}
