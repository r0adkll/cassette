package com.r0adkll.cassette.settings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.bikes.summary.ago
import com.r0adkll.cassette.common.compose.AvailableBuild
import com.r0adkll.cassette.common.compose.LocalAppUpdates
import com.r0adkll.cassette.common.compose.ReinstallReason
import com.r0adkll.cassette.common.compose.UpdateStatus
import com.r0adkll.cassette.common.compose.components.BikeCard
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.RailAddButton
import com.r0adkll.cassette.common.compose.components.SettingsRow
import com.r0adkll.cassette.common.compose.components.SettingsSection
import com.r0adkll.cassette.common.compose.components.SettingsSwitchRow
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteColors
import com.r0adkll.cassette.common.compose.theme.CassetteRed
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.DarkCassetteColors
import com.r0adkll.cassette.common.compose.theme.LightCassetteColors
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.settings.api.ThemeMode
import kotlin.time.Clock

/**
 * Bikes (iteration 13, B2): whether the last bike seen opens first and playful status titles
 * (decision 0015), then a card per bike, then Add bike. Cards open the bike's own page.
 */
@Composable
internal fun ColumnScope.Garage(state: SettingsUiState, columns: Int) {
  val sink = state.eventSink
  Panel {
    SettingsSwitchRow(
      "Open the last bike seen",
      checked = state.pinned == null,
      onCheckedChange = { sink(SettingsEvent.SetFollowLastSeen(it)) },
      subtitle = "Off: the bike marked Opens first, always",
      icon = LucideIcons.History,
    )
    SettingsSwitchRow(
      "Playful titles",
      checked = state.playfulTitles,
      onCheckedChange = { sink(SettingsEvent.SetPlayfulTitles(it)) },
      subtitle =
        if (state.playfulTitles) "On: a new one each time the app opens"
        else "Off: plain status titles",
      icon = LucideIcons.Smile,
      divider = false,
    )
  }
  state.garage.chunked(columns).forEach { row ->
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      row.forEach { bike ->
        BikeCard(
          bike.name,
          details = "${bike.type} · ${bike.seen}",
          tone = bike.tone,
          components = bike.components,
          opensFirst = bike.opensFirst,
          onClick = { sink(SettingsEvent.OpenBike(bike.id)) },
          modifier = Modifier.weight(1f).fillMaxHeight(),
          narrow = columns > 1,
        )
      }
      // Keeps a lone card in a two-across row at half width.
      repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
  }
  RailAddButton("Add bike", onClick = { sink(SettingsEvent.AddBike) }, Modifier.height(56.dp))
}

/**
 * App updates (iteration 13): a hero saying where things stand, then the detail, then the one
 * action. [wide] puts the action inside the hero, as on the fold's pane; [gutter] is how far the
 * hero bleeds.
 */
@Composable
internal fun ColumnScope.UpdatesBody(wide: Boolean, gutter: Dp) {
  val updates = LocalAppUpdates.current
  val status by updates.status.collectAsState()
  val checked by updates.lastChecked.collectAsState()
  val now = Clock.System.now()
  val checkedLine = checked?.let { "Checked ${ago(now - it)}" } ?: "Not checked yet"
  val installed = updates.installedBuild?.let { "Build $it" } ?: "This build"
  when (val s = status) {
    UpdateStatus.Off -> Unit
    is UpdateStatus.Available -> {
      val build = s.build
      val reinstall = build.reinstall
      val action = if (reinstall != null) "Reinstall" else "Install build ${build.number}"
      UpdateHero(
        filled = true,
        kicker = "New build · ${build.channel}",
        title =
          if (reinstall != null) "Build ${build.number} needs a reinstall."
          else "Build ${build.number} is ready.",
        sub = reinstall?.sentence() ?: build.headline,
        meta =
          listOfNotNull(
              megabytes(build.sizeBytes),
              "uploaded ${ago(now - build.uploadedAt)}",
              build.pullRequest?.let { "PR #$it" },
            )
            .joinToString(" · "),
        gutter = gutter,
        action = if (wide) ({ HeroAction(action, onClick = updates::open) }) else null,
      )
      if (reinstall == null)
        build.notes().takeIf { it.isNotEmpty() }?.let { notes -> WhatsNew(notes) }
      Panel {
        if (reinstall != null)
          SettingsRow(
            "This clears Cassette on this phone",
            subtitle = "Bikes and their history go; add the bikes again after",
            icon = LucideIcons.Info,
          )
        SettingsRow(
          "Installed",
          subtitle = "$installed · ${build.channel} channel",
          icon = LucideIcons.Cpu,
        )
        SettingsRow(
          "Check again",
          subtitle = checkedLine,
          icon = LucideIcons.RefreshCw,
          onClick = updates::check,
          divider = false,
        )
      }
      if (!wide)
        CassetteButton(
          action,
          onClick = updates::open,
          trailing = megabytes(build.sizeBytes),
          modifier = Modifier.padding(top = 6.dp),
        )
    }
    UpdateStatus.Checking,
    UpdateStatus.UpToDate -> {
      val checking = status == UpdateStatus.Checking
      UpdateHero(
        filled = false,
        kicker = if (checking) "Checking…" else checkedLine,
        title = "Up to date.",
        sub = "$installed is the newest on Drop.",
        meta = null,
        gutter = gutter,
      )
      Panel { InstalledRow(installed) }
      CassetteButton(
        if (checking) "Checking…" else "Check now",
        onClick = updates::check,
        primary = false,
        icon = LucideIcons.RefreshCw,
        modifier = Modifier.padding(top = 6.dp),
      )
    }
    is UpdateStatus.Failed -> {
      UpdateHero(
        filled = false,
        kicker = checkedLine,
        title = "Couldn't check.",
        sub = s.message,
        meta = null,
        gutter = gutter,
      )
      Panel { InstalledRow(installed) }
      CassetteButton(
        "Try again",
        onClick = updates::check,
        icon = LucideIcons.RefreshCw,
        modifier = Modifier.padding(top = 6.dp),
      )
    }
  }
}

/**
 * The page's status block: filled in the ink colour when a build is waiting (with the red dot), or
 * an outlined panel when not. It bleeds by [gutter] to the screen's (or pane's) edges.
 */
@Composable
private fun UpdateHero(
  filled: Boolean,
  kicker: String,
  title: String,
  sub: String,
  meta: String?,
  gutter: Dp,
  action: (@Composable () -> Unit)? = null,
) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  val content = if (filled) colors.onInk else colors.text
  Row(
    Modifier.bleed(gutter)
      .background(if (filled) colors.ink else colors.panel)
      .then(if (filled) Modifier else Modifier.border(1.dp, colors.line))
      .padding(horizontal = gutter, vertical = 18.dp),
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        if (filled) Box(Modifier.size(8.dp).clip(CircleShape).background(CassetteRed))
        Text(kicker.uppercase(), style = type.label, color = content.copy(alpha = 0.9f))
      }
      Text(title, style = type.title, color = content)
      Text(sub, style = type.body, color = content)
      if (meta != null)
        Text(meta.uppercase(), style = type.status, color = content.copy(alpha = 0.8f))
    }
    action?.invoke()
  }
}

/** The hero's own button on wide windows: the hero's colours, reversed. */
@Composable
private fun HeroAction(text: String, onClick: () -> Unit) {
  val colors = CassetteTheme.colors
  Box(
    Modifier.width(170.dp)
      .height(48.dp)
      .clip(CircleShape)
      .background(colors.onInk)
      .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(text.uppercase(), style = CassetteTheme.type.button, color = colors.ink)
  }
}

@Composable
private fun WhatsNew(notes: List<String>) {
  val colors = CassetteTheme.colors
  SettingsSection("What's new") {
    notes.forEach { note ->
      Row(
        Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("—", style = CassetteTheme.type.body, color = colors.muted)
        Text(note, style = CassetteTheme.type.body, color = colors.text)
      }
    }
  }
}

@Composable
private fun InstalledRow(installed: String) {
  SettingsRow("Installed", subtitle = installed, icon = LucideIcons.Cpu, divider = false)
}

/** What's new as separate changes: a line each, without list markers. */
internal fun AvailableBuild.notes(): List<String> =
  whatsNew
    ?.lines()
    ?.map { it.trim().removePrefix("- ").removePrefix("* ").trim() }
    ?.filter { it.isNotEmpty() }
    .orEmpty()

private fun ReinstallReason.sentence(): String =
  when (this) {
    ReinstallReason.DifferentKey ->
      "It is signed with a different key, so Android can only install it fresh."
    ReinstallReason.LowerBuild ->
      "Its build number is lower than this one's, so Android can only install it fresh."
    ReinstallReason.Unknown -> "Android can only install it fresh."
  }

/** "9.8 MB", to a tenth. */
internal fun megabytes(bytes: Long): String {
  val tenths = (bytes + 50_000) / 100_000
  return "${tenths / 10}.${tenths % 10} MB"
}

/** Lays this out wider than its slot by [amount] on each side, so it reaches the edges. */
private fun Modifier.bleed(amount: Dp): Modifier = layout { measurable, constraints ->
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
 * Appearance: the theme as three choices, each a small picture of the bike screen in it. System is
 * half light, half dark.
 */
@Composable
internal fun ThemeChoices(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    ThemeMode.entries.forEach { mode ->
      ThemeChoice(
        mode,
        selected = mode == selected,
        onClick = { onSelect(mode) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun ThemeChoice(
  mode: ThemeMode,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = CassetteTheme.colors
  val shape = RoundedCornerShape(16.dp)
  Column(
    modifier
      .clip(RoundedCornerShape(18.dp))
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .padding(4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Canvas(
      Modifier.fillMaxWidth()
        .aspectRatio(0.8f)
        .clip(shape)
        .border(if (selected) 2.dp else 1.dp, if (selected) colors.text else colors.line, shape)
    ) {
      when (mode) {
        ThemeMode.Light -> drawPreview(LightCassetteColors)
        ThemeMode.Dark -> drawPreview(DarkCassetteColors)
        ThemeMode.System -> {
          clipRect(right = size.width / 2) { drawPreview(LightCassetteColors) }
          clipRect(left = size.width / 2) { drawPreview(DarkCassetteColors) }
        }
      }
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (selected)
        Icon(LucideIcons.Check, null, tint = colors.text, modifier = Modifier.size(16.dp))
      Text(
        mode.label.uppercase(),
        style =
          CassetteTheme.type.button.copy(fontSize = CassetteTheme.type.button.fontSize * 0.85f),
        color = if (selected) colors.text else colors.muted,
      )
    }
  }
}

/** A tiny bike screen in [palette]: the green status block, then three component rows. */
private fun DrawScope.drawPreview(palette: CassetteColors) {
  drawRect(palette.background)
  val inset = size.width * 0.1f
  val width = size.width - inset * 2
  val radius = CornerRadius(size.width * 0.05f)
  drawRoundRect(
    palette.block(StatusTone.Good).container,
    topLeft = Offset(inset, inset),
    size = Size(width, size.height * 0.24f),
    cornerRadius = radius,
  )
  val row = size.height * 0.11f
  repeat(3) { i ->
    val top = inset + size.height * 0.24f + inset * 0.8f + i * (row + inset * 0.5f)
    drawRoundRect(
      palette.panel,
      topLeft = Offset(inset, top),
      size = Size(width, row),
      cornerRadius = radius,
    )
    // Each row's battery bar.
    drawRoundRect(
      palette.good,
      topLeft = Offset(inset * 1.6f, top + row * 0.4f),
      size = Size(width * (0.7f - i * 0.12f), row * 0.2f),
      cornerRadius = CornerRadius(row * 0.1f),
    )
  }
}

internal val ThemeMode.label: String
  get() =
    when (this) {
      ThemeMode.System -> "System"
      ThemeMode.Light -> "Light"
      ThemeMode.Dark -> "Dark"
    }
