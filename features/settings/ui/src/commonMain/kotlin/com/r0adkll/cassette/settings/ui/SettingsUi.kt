package com.r0adkll.cassette.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.AppInfo
import com.r0adkll.cassette.common.compose.AppPermission
import com.r0adkll.cassette.common.compose.AppPlatform
import com.r0adkll.cassette.common.compose.LocalAppInfo
import com.r0adkll.cassette.common.compose.LocalAppPlatform
import com.r0adkll.cassette.common.compose.LocalAppUpdates
import com.r0adkll.cassette.common.compose.LocalPermissionController
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.PermissionState
import com.r0adkll.cassette.common.compose.ScreenLayout
import com.r0adkll.cassette.common.compose.UpdateStatus
import com.r0adkll.cassette.common.compose.components.CategoryItem
import com.r0adkll.cassette.common.compose.components.Chevron
import com.r0adkll.cassette.common.compose.components.DetailPaneGutter
import com.r0adkll.cassette.common.compose.components.DetailPaneTitle
import com.r0adkll.cassette.common.compose.components.DetailPaneWidth
import com.r0adkll.cassette.common.compose.components.FloatingTopBar
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.LocalInDetailPane
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.SettingsRow
import com.r0adkll.cassette.common.compose.components.SettingsSection
import com.r0adkll.cassette.common.compose.components.SettingsSwitchRow
import com.r0adkll.cassette.common.compose.components.SplitPanes
import com.r0adkll.cassette.common.compose.components.TopBarNavigation
import com.r0adkll.cassette.common.compose.components.TopBarSpacer
import com.r0adkll.cassette.common.compose.components.WaitingDot
import com.r0adkll.cassette.common.compose.endPane
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.startPane
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.DiagnosticsScreen
import com.r0adkll.cassette.common.screens.SettingsCategory
import com.r0adkll.cassette.common.screens.SettingsScreen
import com.r0adkll.cassette.settings.api.ThemeMode
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import dev.zacsweers.metro.AppScope

/**
 * A screen with a back button and a big title, content capped at phone width. Back floats while the
 * content scrolls under it (iteration 14). [kicker] is a small label over the title.
 */
@Composable
internal fun TitledScreen(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  kicker: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      Modifier.widthIn(max = 480.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .safeDrawingPadding()
        .padding(horizontal = 20.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      TitleBlock(title, kicker)
      content()
    }
    BackBar(onBack)
  }
}

/** [TitledScreen] for content that runs long: [content] is a lazy list's, without its spacing. */
@Composable
internal fun TitledLazyScreen(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  kicker: String? = null,
  content: LazyListScope.() -> Unit,
) {
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    LazyColumn(
      Modifier.widthIn(max = 480.dp).fillMaxWidth(),
      contentPadding =
        PaddingValues(
          start = insets.calculateStartPadding(direction) + 20.dp,
          top = insets.calculateTopPadding() + 24.dp,
          end = insets.calculateEndPadding(direction) + 20.dp,
          bottom = insets.calculateBottomPadding() + 24.dp,
        ),
    ) {
      item { TitleBlock(title, kicker, Modifier.padding(bottom = 14.dp)) }
      content()
    }
    BackBar(onBack)
  }
}

/**
 * [TitledScreen] split in two on anything wider than a phone (decision 0022): back, the title and
 * what the screen says ([start]) on the left; what it lists ([end], a lazy list) on the right. Each
 * pane scrolls on its own and keeps the phone's width, against the split, so a tablet's panes read
 * as one page rather than drifting to its edges.
 */
@Composable
internal fun TitledPanes(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  kicker: String? = null,
  start: @Composable ColumnScope.() -> Unit,
  end: LazyListScope.() -> Unit,
) {
  SplitPanes(
    start = {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Column(
          Modifier.widthIn(max = 480.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          TitleBlock(title, kicker)
          start()
        }
        BackBar(onBack)
      }
    },
    end = {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        // The pane has already dropped the start bar, which contentPadding wouldn't know: the sides
        // pad as a modifier, the top and bottom as content padding so the list scrolls under them.
        val vertical = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical).asPaddingValues()
        LazyColumn(
          Modifier.widthIn(max = 480.dp)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
          contentPadding =
            PaddingValues(
              start = 20.dp,
              top = vertical.calculateTopPadding() + 24.dp,
              end = 20.dp,
              bottom = vertical.calculateBottomPadding() + 24.dp,
            ),
          content = end,
        )
      }
    },
    modifier = modifier.fillMaxSize(),
    divider = LocalScreenLayout.current == ScreenLayout.Landscape,
  )
}

@Composable
private fun TitleBlock(title: String, kicker: String?, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
    TopBarSpacer()
    if (kicker != null) Kicker(kicker)
    Text(title, style = CassetteTheme.type.title, color = CassetteTheme.colors.text)
  }
}

@Composable
private fun BackBar(onBack: () -> Unit) {
  FloatingTopBar(
    Modifier.widthIn(max = 480.dp)
      .safeDrawingPadding()
      .padding(horizontal = 20.dp, vertical = 24.dp)
  ) {
    TopBarNavigation(LucideIcons.ArrowLeft, "Back", onClick = onBack)
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
    // Diagnostics opens its own screen, so only a wide window lands on it here.
    null,
    SettingsCategory.Diagnostics -> CategoryList(state, modifier)
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
        val info = state.info(category, update, LocalAppPlatform.current, LocalAppInfo.current)
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
  // Both panes and the divider run under the system bars (decision 0016).
  Row(modifier.fillMaxSize()) {
    Box(Modifier.fillMaxHeight().startPane()) {
      Column(
        Modifier.fillMaxHeight()
          .verticalScroll(rememberScrollState())
          .safeDrawingPadding()
          .width(320.dp)
          .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        TopBarSpacer()
        Text(
          "Settings.",
          style = CassetteTheme.type.title,
          color = colors.text,
          modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 10.dp),
        )
        categories.forEach { category ->
          val info = state.info(category, update, LocalAppPlatform.current, LocalAppInfo.current)
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
      FloatingTopBar(
        Modifier.safeDrawingPadding().width(320.dp).padding(start = 20.dp, top = 24.dp)
      ) {
        TopBarNavigation(LucideIcons.ArrowLeft, "Back", onClick = { sink(SettingsEvent.Back) })
      }
    }
    Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
    Box(Modifier.weight(1f).fillMaxHeight().endPane()) {
      if (selected == SettingsCategory.Diagnostics) {
        // Its screens lay out as the categories do here.
        CompositionLocalProvider(LocalInDetailPane provides true) { diagnostics() }
      } else {
        // The pane's spare width stays on the right.
        Column(
          Modifier.widthIn(max = DetailPaneWidth)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = DetailPaneGutter, vertical = 24.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          DetailPaneTitle(selected.title())
          CategoryBody(selected, state, wide = true, gutter = DetailPaneGutter)
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
  category.blurb(LocalAppPlatform.current)?.let {
    Text(it, style = CassetteTheme.type.body, color = colors.muted)
  }
  when (category) {
    SettingsCategory.Bikes -> Garage(state, columns = if (wide) 2 else 1)
    SettingsCategory.Updates -> UpdatesBody(wide, gutter)
    SettingsCategory.Capture -> {
      CapturePanel(state)
      if (LocalAppPlatform.current == AppPlatform.Ios) OnIphone(state)
    }
    SettingsCategory.Notifications -> {
      NotificationPermission()
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
    }
    SettingsCategory.Appearance ->
      ThemeChoices(state.themeMode, onSelect = { sink(SettingsEvent.SetThemeMode(it)) })
    SettingsCategory.About ->
      AboutBody(
        crashReports = state.crashReports,
        onCrashReports = { sink(SettingsEvent.SetCrashReports(it)) },
        onLicences = { sink(SettingsEvent.OpenLicences) },
      )
    // Opens as its own screen on a phone, and in the pane on a wide window.
    SettingsCategory.Diagnostics -> Unit
  }
}

@Composable
private fun CapturePanel(state: SettingsUiState) {
  val sink = state.eventSink
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
}

/**
 * How iOS lets Cassette listen (iteration 14, decision 0017): iOS sets the cadence, and swiping the
 * app away stops background capture until it's opened again.
 */
@Composable
private fun OnIphone(state: SettingsUiState) {
  SettingsSection("On iPhone") {
    SettingsRow(
      "iOS decides when Cassette listens",
      subtitle =
        "It hears the bike when it wakes nearby, but can miss a short stop. Refresh always reads it.",
      icon = LucideIcons.Info,
      trailing = {},
    )
    SettingsRow(
      "Keep Cassette in the app switcher",
      subtitle = "Swiping it away stops background capture until you open it again.",
      icon = LucideIcons.AppWindow,
      trailing = {},
    )
    SettingsRow(
      state.lastHeard ?: "Not heard in the background yet",
      subtitle = state.wakes,
      icon = LucideIcons.History,
      divider = false,
      trailing = {},
    )
  }
}

/**
 * Above the switches while Cassette can't post notifications: asks again, or opens the system
 * settings once the phone won't ask (onboarding may have been skipped).
 */
@Composable
private fun NotificationPermission() {
  val controller = LocalPermissionController.current
  val states by controller.states.collectAsState()
  val state = states[AppPermission.Notifications] ?: return
  if (state == PermissionState.Granted) return
  Panel {
    SettingsRow(
      "Notifications are off",
      subtitle =
        if (state == PermissionState.Askable) "Allow them so these switches can work"
        else "Turned off for Cassette in the phone's settings. Tap to open them",
      icon = LucideIcons.BellOff,
      onClick = {
        if (state == PermissionState.Askable) controller.request(AppPermission.Notifications)
        else controller.openSettings()
      },
      divider = false,
    )
  }
}

/** A category's icon, name and one-line summary of where it stands. */
private data class CategoryInfo(
  val icon: ImageVector,
  val label: String,
  val summary: String,
  val badge: Boolean = false,
)

private fun SettingsUiState.info(
  category: SettingsCategory,
  update: UpdateStatus,
  platform: AppPlatform,
  appInfo: AppInfo,
): CategoryInfo =
  when (category) {
    SettingsCategory.Bikes -> CategoryInfo(LucideIcons.Bike, "Bikes", opensFirstLabel)
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
    SettingsCategory.Appearance ->
      CategoryInfo(
        LucideIcons.SunMoon,
        "Appearance",
        if (themeMode == ThemeMode.System) "Follows the phone" else "${themeMode.label} theme",
      )
    SettingsCategory.Updates ->
      CategoryInfo(
        LucideIcons.Download,
        "App updates",
        update.summary(),
        badge = update is UpdateStatus.Available,
      )
    SettingsCategory.Diagnostics ->
      CategoryInfo(
        LucideIcons.Activity,
        "Diagnostics",
        when (platform) {
          AppPlatform.Android -> "Raw adverts, hub tables, scan profile"
          // iOS has no scan profiles to choose (decision 0017).
          AppPlatform.Ios -> "Raw adverts, hub tables, ride tests"
        },
      )
    SettingsCategory.About ->
      CategoryInfo(
        LucideIcons.Info,
        "About",
        "Version ${appInfo.version} · help, privacy, licences",
      )
  }

/** App updates shows only on builds that update themselves (Drop's). */
private fun visibleCategories(update: UpdateStatus): List<SettingsCategory> =
  SettingsCategory.entries.filter { it != SettingsCategory.Updates || update != UpdateStatus.Off }

private fun SettingsCategory.title(): String =
  when (this) {
    SettingsCategory.Bikes -> "Bikes."
    SettingsCategory.Capture -> "Capture."
    SettingsCategory.Notifications -> "Notifications."
    SettingsCategory.Appearance -> "Appearance."
    SettingsCategory.Updates -> "App updates."
    SettingsCategory.Diagnostics -> "Diagnostics."
    SettingsCategory.About -> "About."
  }

private fun SettingsCategory.blurb(platform: AppPlatform): String? =
  when (this) {
    SettingsCategory.Bikes -> null
    SettingsCategory.Capture ->
      "How Cassette hears your bike: in the background whenever it wakes, and for a while " +
        "after you tap Refresh."
    SettingsCategory.Notifications ->
      when (platform) {
        AppPlatform.Android ->
          "One switch for each kind of notification. Android's notification settings can " +
            "still turn one off."
        AppPlatform.Ios ->
          "One switch for each kind of notification. The Settings app can still turn them off."
      }
    SettingsCategory.Appearance ->
      when (platform) {
        AppPlatform.Android ->
          "Light, dark, or whichever the phone is set to. Widgets, the Quick Settings tile and " +
            "the watch follow the phone."
        AppPlatform.Ios -> "Light, dark, or whichever the phone is set to."
      }
    // The update hero says it all.
    SettingsCategory.Updates -> null
    SettingsCategory.About ->
      "Cassette is an unofficial battery monitor for SRAM AXS. It isn't made or endorsed by SRAM, " +
        "and what it reads stays on your phone."
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
