package com.r0adkll.cassette.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.LocalAppUpdates
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.ScreenLayout
import com.r0adkll.cassette.common.compose.UpdateStatus
import com.r0adkll.cassette.common.compose.components.CategoryItem
import com.r0adkll.cassette.common.compose.components.Chevron
import com.r0adkll.cassette.common.compose.components.IconCircleButton
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.SettingsRow
import com.r0adkll.cassette.common.compose.components.SettingsSection
import com.r0adkll.cassette.common.compose.components.SettingsSwitchRow
import com.r0adkll.cassette.common.compose.components.WaitingDot
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.DiagnosticsScreen
import com.r0adkll.cassette.common.screens.LicencesScreen
import com.r0adkll.cassette.common.screens.SettingsCategory
import com.r0adkll.cassette.common.screens.SettingsScreen
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
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

/**
 * Settings by category (decision 0014). On a phone the list of categories opens each as its own
 * screen; wide windows show the list beside the open category.
 */
@CircuitInject(SettingsScreen::class, AppScope::class)
@Composable
fun SettingsUi(state: SettingsUiState, modifier: Modifier = Modifier) {
  if (LocalScreenLayout.current != ScreenLayout.Column) {
    WideSettings(state, modifier)
    return
  }
  val sink = state.eventSink
  when (val category = state.category) {
    // Diagnostics and Licences open their own screens, so only a wide window lands on them here.
    null,
    SettingsCategory.Diagnostics,
    SettingsCategory.Licences -> CategoryList(state, modifier)
    else ->
      TitledScreen(category.title(), onBack = { sink(SettingsEvent.Back) }, modifier) {
        CategoryBody(category, state)
      }
  }
}

/** The phone's first screen: one panel with a row per category. */
@Composable
private fun CategoryList(state: SettingsUiState, modifier: Modifier) {
  val update by LocalAppUpdates.current.status.collectAsState()
  val categories = visibleCategories(update)
  TitledScreen("Settings.", onBack = { state.eventSink(SettingsEvent.Back) }, modifier) {
    Panel {
      categories.forEachIndexed { i, category ->
        val info = state.info(category, update)
        SettingsRow(
          info.label,
          subtitle = info.summary,
          icon = info.icon,
          onClick = { state.eventSink(SettingsEvent.OpenCategory(category)) },
          divider = i < categories.lastIndex,
          trailing = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (info.badge) WaitingDot()
              Chevron()
            }
          },
        )
      }
    }
    Spacer(Modifier.padding(4.dp))
    Footnote(Modifier.fillMaxWidth())
  }
}

/**
 * The list beside the open category. Diagnostics opens in the pane with its own back stack, so its
 * tools open there too; [diagnostics] draws it (renders pass their own, since the app's runs the
 * screens' presenters).
 */
@Composable
internal fun WideSettings(
  state: SettingsUiState,
  modifier: Modifier = Modifier,
  diagnostics: @Composable () -> Unit = { DiagnosticsPane() },
) {
  val sink = state.eventSink
  val colors = CassetteTheme.colors
  val update by LocalAppUpdates.current.status.collectAsState()
  val categories = visibleCategories(update)
  // App updates can disappear (a build that doesn't update itself); fall back to the first.
  val selected = state.selected.takeIf { it in categories } ?: categories.first()
  Row(modifier.fillMaxSize()) {
    Column(
      Modifier.width(320.dp)
        .fillMaxHeight()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      IconCircleButton(
        LucideIcons.ArrowLeft,
        "Back",
        onClick = { sink(SettingsEvent.Back) },
        modifier = Modifier.padding(start = 4.dp),
      )
      Text(
        "Settings.",
        style = CassetteTheme.type.title,
        color = colors.text,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 10.dp),
      )
      categories.forEach { category ->
        val info = state.info(category, update)
        CategoryItem(
          info.icon,
          info.label,
          info.summary,
          selected = category == selected,
          onClick = { sink(SettingsEvent.SelectCategory(category)) },
          badge = info.badge,
        )
      }
      Footnote(Modifier.padding(start = 12.dp, top = 16.dp))
    }
    Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
    Box(Modifier.weight(1f).fillMaxHeight()) {
      if (selected == SettingsCategory.Diagnostics) {
        diagnostics()
      } else {
        // Capped so rows don't stretch across a tablet; the pane's spare width stays on the right.
        Column(
          Modifier.widthIn(max = 640.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          // Lines the title up with the list's, which sits under its back button.
          Spacer(Modifier.height(44.dp))
          Text(selected.title(), style = CassetteTheme.type.title, color = colors.text)
          CategoryBody(selected, state, wide = true, gutter = 32.dp)
        }
      }
    }
  }
}

/** Diagnostics on its own back stack inside the pane; its root has no back button there. */
@Composable
private fun DiagnosticsPane() {
  val backStack = rememberSaveableBackStack(DiagnosticsScreen(inPane = true))
  val navigator = rememberCircuitNavigator(backStack, onRootPop = {})
  NavigableCircuitContent(navigator, backStack)
}

/**
 * A category's sentence on what it covers, then its rows. Shared by the phone and the pane; [wide]
 * lays the garage out two across and puts Install inside the update hero.
 */
@Composable
private fun ColumnScope.CategoryBody(
  category: SettingsCategory,
  state: SettingsUiState,
  wide: Boolean = false,
  gutter: Dp = 20.dp,
) {
  val sink = state.eventSink
  val colors = CassetteTheme.colors
  category.blurb()?.let { Text(it, style = CassetteTheme.type.body, color = colors.muted) }
  when (category) {
    SettingsCategory.Bikes -> Garage(state, columns = if (wide) 2 else 1)
    SettingsCategory.Updates -> UpdatesBody(wide, gutter)
    SettingsCategory.Capture ->
      Panel {
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
    SettingsCategory.Notifications ->
      Panel {
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
          onCheckedChange = {
            sink(SettingsEvent.SetNotifications(n.copy(notSeenReminders = it)))
          },
        )
      }
    SettingsCategory.Licences -> LicencesList()
    // Opens as its own screen on a phone, and in the pane on a wide window.
    SettingsCategory.Diagnostics -> Unit
  }
}

/** A category's icon, name and one-line summary of where it stands. */
private data class CategoryInfo(
  val icon: ImageVector,
  val label: String,
  val summary: String,
  val badge: Boolean = false,
)

private fun SettingsUiState.info(category: SettingsCategory, update: UpdateStatus): CategoryInfo =
  when (category) {
    SettingsCategory.Bikes -> CategoryInfo(LucideIcons.Pin, "Bikes", opensFirstLabel)
    SettingsCategory.Capture ->
      CategoryInfo(
        LucideIcons.Bluetooth,
        "Capture",
        "Background capture ${if (backgroundCapture) "on" else "off"} · 60 s refresh",
      )
    SettingsCategory.Notifications -> {
      val on =
        listOf(notifications.batteryAlerts, notifications.preRide, notifications.notSeenReminders)
          .count { it }
      CategoryInfo(LucideIcons.Bell, "Notifications", "$on of 3 on")
    }
    SettingsCategory.Updates ->
      CategoryInfo(
        LucideIcons.Download,
        "App updates",
        update.summary(),
        badge = update is UpdateStatus.Available,
      )
    SettingsCategory.Diagnostics ->
      CategoryInfo(LucideIcons.Activity, "Diagnostics", "Raw adverts, hub tables, scan profile")
    SettingsCategory.Licences ->
      CategoryInfo(LucideIcons.FileText, "Licences", "Barlow, JetBrains Mono, Lucide")
  }

/** App updates shows only on builds that update themselves (Drop's). */
private fun visibleCategories(update: UpdateStatus): List<SettingsCategory> =
  SettingsCategory.entries.filter { it != SettingsCategory.Updates || update != UpdateStatus.Off }

private fun SettingsCategory.title(): String =
  when (this) {
    SettingsCategory.Bikes -> "Bikes."
    SettingsCategory.Capture -> "Capture."
    SettingsCategory.Notifications -> "Notifications."
    SettingsCategory.Updates -> "App updates."
    SettingsCategory.Diagnostics -> "Diagnostics."
    SettingsCategory.Licences -> "Licences."
  }

private fun SettingsCategory.blurb(): String? =
  when (this) {
    SettingsCategory.Bikes -> null
    SettingsCategory.Capture ->
      "How Cassette hears your bike: in the background whenever it wakes, and for a while " +
        "after you tap Refresh."
    SettingsCategory.Notifications ->
      "One switch for each kind of notification. Android's notification settings can still " +
        "turn one off."
    // The update hero says it all.
    SettingsCategory.Updates -> null
    SettingsCategory.Licences -> "Third-party fonts and icons Cassette ships."
    SettingsCategory.Diagnostics -> null
  }

private fun UpdateStatus.summary(): String =
  when (this) {
    UpdateStatus.Off -> ""
    UpdateStatus.Checking -> "Checking…"
    UpdateStatus.UpToDate -> "Up to date"
    is UpdateStatus.Available ->
      if (build.reinstall != null) "Build ${build.number} needs a reinstall"
      else "Build ${build.number} is ready"
    is UpdateStatus.Failed -> "Couldn't check"
  }

@Composable
private fun Footnote(modifier: Modifier = Modifier) {
  Text(
    "Cassette · Unofficial. Not affiliated with SRAM.",
    style = CassetteTheme.type.bodySmall,
    color = CassetteTheme.colors.muted,
    textAlign = TextAlign.Center,
    modifier = modifier,
  )
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
  TitledScreen("Licences.", onBack = state.onBack, modifier) { LicencesList() }
}

@Composable
private fun LicencesList() {
  LICENCES.forEach { (name, licence, copyright) ->
    SettingsSection(name) {
      Text(licence, style = CassetteTheme.type.itemTitle, color = CassetteTheme.colors.text)
      Text(copyright, style = CassetteTheme.type.bodySmall, color = CassetteTheme.colors.muted)
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
