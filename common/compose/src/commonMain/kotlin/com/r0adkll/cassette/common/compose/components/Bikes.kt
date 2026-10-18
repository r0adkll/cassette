package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
