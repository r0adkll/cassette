package com.r0adkll.cassette.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.components.IconCircleButton
import com.r0adkll.cassette.common.compose.components.SettingsRow
import com.r0adkll.cassette.common.compose.components.SettingsSection
import com.r0adkll.cassette.common.compose.components.SettingsSwitchRow
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.LicencesScreen
import com.r0adkll.cassette.common.screens.SettingsScreen
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject

/** A screen with a back button and a big title, content capped at phone width. */
@Composable
internal fun TitledScreen(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      Modifier.widthIn(max = 480.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      IconCircleButton(LucideIcons.ArrowLeft, "Back", onClick = onBack)
      Text(title, style = CassetteTheme.type.title, color = CassetteTheme.colors.text)
      content()
    }
  }
}

@CircuitInject(SettingsScreen::class, AppScope::class)
@Composable
fun SettingsUi(state: SettingsUiState, modifier: Modifier = Modifier) {
  val sink = state.eventSink
  val colors = CassetteTheme.colors
  TitledScreen("Settings.", onBack = { sink(SettingsEvent.Back) }, modifier) {
    SettingsSection("Bikes") {
      SettingsRow(
        "Default bike",
        subtitle = state.defaultBikeLabel,
        icon = LucideIcons.Pin,
        onClick = { sink(SettingsEvent.ChooseBike) },
        divider = state.choosingBike,
      )
      if (state.choosingBike) {
        SettingsRow(
          "Most recently seen",
          onClick = { sink(SettingsEvent.PinBike(null)) },
          divider = state.bikes.isNotEmpty(),
          trailing = { if (state.pinned == null) Check() },
        )
        state.bikes.forEachIndexed { i, bike ->
          SettingsRow(
            bike.name,
            onClick = { sink(SettingsEvent.PinBike(bike.id)) },
            divider = i < state.bikes.lastIndex,
            trailing = { if (state.pinned == bike.id) Check() },
          )
        }
      }
    }
    SettingsSection("Capture") {
      SettingsSwitchRow(
        "Background capture",
        subtitle =
          if (state.backgroundCapture) "On · reads the bike when it wakes"
          else "Off · only Refresh reads the bike",
        icon = LucideIcons.Bluetooth,
        checked = state.backgroundCapture,
        onCheckedChange = { sink(SettingsEvent.SetBackgroundCapture(it)) },
      )
      SettingsRow(
        "Refresh scan length",
        subtitle = "60 seconds",
        icon = LucideIcons.RefreshCw,
        divider = false,
        trailing = {},
      )
    }
    SettingsSection("Notifications") {
      val n = state.notifications
      SettingsSwitchRow(
        "Battery alerts",
        subtitle = "When a component gets low or needs charging",
        icon = LucideIcons.Bell,
        checked = n.batteryAlerts,
        onCheckedChange = { sink(SettingsEvent.SetNotifications(n.copy(batteryAlerts = it))) },
      )
      SettingsSwitchRow(
        "Pre-ride nudge",
        subtitle = "At most every 12 hours",
        icon = LucideIcons.BellRing,
        checked = n.preRide,
        onCheckedChange = { sink(SettingsEvent.SetNotifications(n.copy(preRide = it))) },
      )
      SettingsSwitchRow(
        "Not-seen reminders",
        subtitle = "14 and 30 days",
        icon = LucideIcons.History,
        divider = false,
        checked = n.notSeenReminders,
        onCheckedChange = { sink(SettingsEvent.SetNotifications(n.copy(notSeenReminders = it))) },
      )
    }
    SettingsSection("More") {
      SettingsRow(
        "Diagnostics",
        subtitle = "Raw adverts, hub tables, scan profile",
        icon = LucideIcons.Activity,
        onClick = { sink(SettingsEvent.OpenDiagnostics) },
      )
      SettingsRow(
        "Licences",
        subtitle = "Barlow, JetBrains Mono, Lucide",
        icon = LucideIcons.FileText,
        onClick = { sink(SettingsEvent.OpenLicences) },
        divider = false,
      )
    }
    Spacer(Modifier.padding(4.dp))
    Text(
      "Cassette · Unofficial. Not affiliated with SRAM.",
      style = CassetteTheme.type.bodySmall,
      color = colors.muted,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun Check() {
  Icon(LucideIcons.Check, null, tint = CassetteTheme.colors.text, modifier = Modifier.size(18.dp))
}

data class LicencesUiState(val onBack: () -> Unit) : CircuitUiState

@CircuitInject(LicencesScreen::class, AppScope::class)
@Inject
class LicencesPresenter(private val navigator: Navigator) : Presenter<LicencesUiState> {
  @Composable override fun present() = LicencesUiState(onBack = { navigator.pop() })
}

/** Third-party notices (decision 0012: Lucide ISC; fonts OFL). */
@CircuitInject(LicencesScreen::class, AppScope::class)
@Composable
fun LicencesUi(state: LicencesUiState, modifier: Modifier = Modifier) {
  TitledScreen("Licences.", onBack = state.onBack, modifier) {
    LICENCES.forEach { (name, licence, copyright) ->
      SettingsSection(name) {
        Text(licence, style = CassetteTheme.type.itemTitle, color = CassetteTheme.colors.text)
        Text(copyright, style = CassetteTheme.type.bodySmall, color = CassetteTheme.colors.muted)
      }
    }
  }
}

private val LICENCES =
  listOf(
    Triple(
      "Barlow, Barlow Condensed",
      "SIL Open Font License 1.1",
      "Copyright 2017 The Barlow Project Authors (github.com/jpt/barlow)",
    ),
    Triple(
      "JetBrains Mono",
      "SIL Open Font License 1.1",
      "Copyright 2020 The JetBrains Mono Project Authors",
    ),
    Triple(
      "Lucide",
      "ISC License",
      "Copyright Lucide Contributors 2022; portions Copyright Cole Bemis 2013–2022 (Feather, MIT)",
    ),
  )
