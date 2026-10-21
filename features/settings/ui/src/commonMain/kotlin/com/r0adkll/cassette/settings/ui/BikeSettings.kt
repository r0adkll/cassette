package com.r0adkll.cassette.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.summary.code
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.ScreenLayout
import com.r0adkll.cassette.common.compose.components.ChoiceChips
import com.r0adkll.cassette.common.compose.components.CodeBadge
import com.r0adkll.cassette.common.compose.components.DestructiveButton
import com.r0adkll.cassette.common.compose.components.FloatingTopBar
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.SettingsSection
import com.r0adkll.cassette.common.compose.components.SettingsSwitchRow
import com.r0adkll.cassette.common.compose.components.TopBarButton
import com.r0adkll.cassette.common.compose.components.TopBarTitle
import com.r0adkll.cassette.common.compose.components.topBarMargin
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.BikeSettingsScreen
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BikeSettingsUiState(
  /** Null while loading, and once the bike is gone. */
  val bike: BikeSettingsModel?,
  /** Remove was tapped once; the next tap removes. */
  val confirmRemove: Boolean,
  val eventSink: (BikeSettingsEvent) -> Unit,
) : CircuitUiState

data class BikeSettingsModel(
  val name: String,
  val type: BikeType,
  val opensFirst: Boolean,
  val components: List<ComponentLine>,
)

/** A component on the bike's page: its code, name and serial (or the key it's known by). */
data class ComponentLine(val code: String, val name: String, val serial: String)

sealed interface BikeSettingsEvent : CircuitUiEvent {
  data object Back : BikeSettingsEvent

  /** Saved as typed; a blank name is ignored. */
  data class Rename(val name: String) : BikeSettingsEvent

  data class SetType(val type: BikeType) : BikeSettingsEvent

  data class SetOpensFirst(val opensFirst: Boolean) : BikeSettingsEvent

  /** First tap asks, second (within [RemoveTimeout]) removes and goes back. */
  data object Remove : BikeSettingsEvent
}

/** How long "Tap again to remove" waits for the second tap. */
internal val RemoveTimeout = 5.seconds

@CircuitInject(BikeSettingsScreen::class, AppScope::class)
@Inject
class BikeSettingsPresenter(
  private val screen: BikeSettingsScreen,
  private val navigator: Navigator,
  private val bikes: BikeRepository,
  private val settings: CassetteSettings,
) : Presenter<BikeSettingsUiState> {

  @Composable
  override fun present(): BikeSettingsUiState {
    val scope = rememberCoroutineScope()
    val id = BikeId(screen.bikeId)
    val overview by remember { bikes.observeOverview(id) }.collectAsRetainedState(null)
    val pinned by remember { settings.pinnedBike }.collectAsRetainedState(null)
    var confirmRemove by remember { mutableStateOf(false) }
    // An unanswered "Tap again to remove" goes back to Remove bike.
    LaunchedEffect(confirmRemove) {
      if (confirmRemove) {
        delay(RemoveTimeout)
        confirmRemove = false
      }
    }

    val bike = overview?.bike
    val model = overview?.let { o ->
      BikeSettingsModel(
        name = o.bike.name,
        type = o.bike.type,
        opensFirst = pinned == o.bike.id,
        components =
          o.components.map {
            ComponentLine(
              it.component.kind.code,
              it.component.kind.label,
              it.component.serial?.toString() ?: it.component.key,
            )
          },
      )
    }

    return BikeSettingsUiState(model, confirmRemove) { event ->
      when (event) {
        BikeSettingsEvent.Back -> navigator.pop()
        is BikeSettingsEvent.Rename -> {
          val name = event.name.trim()
          if (bike != null && name.isNotEmpty() && name != bike.name)
            scope.launch { bikes.updateBike(id, name, bike.type) }
        }
        is BikeSettingsEvent.SetType ->
          if (bike != null) scope.launch { bikes.updateBike(id, bike.name, event.type) }
        is BikeSettingsEvent.SetOpensFirst ->
          scope.launch { settings.setPinnedBike(if (event.opensFirst) id else null) }
        BikeSettingsEvent.Remove ->
          if (!confirmRemove) confirmRemove = true
          else
          // Deleting also clears it as the bike that opens first.
          scope.launch {
              bikes.deleteBike(id)
              navigator.pop()
            }
      }
    }
  }
}

/**
 * A bike's page (iteration 13): name and type, opens first, its components, remove. Wide windows
 * split it in two: the bike on the left (where its photo will go), its components on the right.
 */
@CircuitInject(BikeSettingsScreen::class, AppScope::class)
@Composable
fun BikeSettingsUi(state: BikeSettingsUiState, modifier: Modifier = Modifier) {
  val sink = state.eventSink
  val model = state.bike
  // The field keeps what's typed; the title follows the saved name.
  var name by rememberSaveable(model != null) { mutableStateOf(model?.name.orEmpty()) }
  val title = model?.let { "${it.name}." }.orEmpty()
  val onBack = { sink(BikeSettingsEvent.Back) }
  if (LocalScreenLayout.current == ScreenLayout.Column) {
    TitledScreen(title, onBack, modifier) {
      if (model == null) return@TitledScreen
      NameAndType(model, name, onNameChange = { name = it }, sink)
      OpensFirst(model, sink)
      Components(model)
      Remove(state.confirmRemove, sink)
    }
  } else {
    TwoColumnScreen(
      title,
      onBack,
      modifier,
      start = {
        if (model == null) return@TwoColumnScreen
        NameAndType(model, name, onNameChange = { name = it }, sink)
        OpensFirst(model, sink)
        Remove(state.confirmRemove, sink)
      },
      end = { if (model != null) Components(model) },
    )
  }
}

/**
 * [TitledScreen]'s back button and title over two columns, capped so they don't drift apart on a
 * tablet. The page scrolls as one, so the columns stay lined up at the top.
 */
@Composable
private fun TwoColumnScreen(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier,
  start: @Composable ColumnScope.() -> Unit,
  end: @Composable ColumnScope.() -> Unit,
) {
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      Modifier.widthIn(max = 1040.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .safeDrawingPadding()
        .padding(horizontal = 32.dp, vertical = topBarMargin()),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      TopBarTitle(title)
      Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        val column = Arrangement.spacedBy(14.dp)
        Column(Modifier.weight(1f), verticalArrangement = column, content = start)
        Column(Modifier.weight(1f), verticalArrangement = column, content = end)
      }
    }
    FloatingTopBar(
      Modifier.widthIn(max = 1040.dp)
        .safeDrawingPadding()
        .padding(horizontal = 32.dp, vertical = topBarMargin())
    ) {
      TopBarButton(LucideIcons.ArrowLeft, "Back", onClick = onBack)
    }
  }
}

@Composable
private fun NameAndType(
  model: BikeSettingsModel,
  name: String,
  onNameChange: (String) -> Unit,
  sink: (BikeSettingsEvent) -> Unit,
) {
  val colors = CassetteTheme.colors
  SettingsSection("Name and type") {
    BasicTextField(
      value = name,
      onValueChange = {
        onNameChange(it)
        sink(BikeSettingsEvent.Rename(it))
      },
      singleLine = true,
      textStyle = CassetteTheme.type.itemTitle.copy(color = colors.text),
      cursorBrush = SolidColor(colors.brand),
      keyboardOptions =
        KeyboardOptions(
          capitalization = KeyboardCapitalization.Sentences,
          imeAction = ImeAction.Done,
        ),
      modifier =
        Modifier.padding(top = 4.dp)
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(colors.background)
          .border(1.dp, colors.line, RoundedCornerShape(12.dp))
          .padding(horizontal = 12.dp, vertical = 12.dp),
    )
    ChoiceChips(
      BikeType.entries,
      selected = model.type,
      label = { it.label },
      onSelect = { sink(BikeSettingsEvent.SetType(it)) },
      modifier = Modifier.padding(bottom = 4.dp),
    )
  }
}

@Composable
private fun OpensFirst(model: BikeSettingsModel, sink: (BikeSettingsEvent) -> Unit) {
  Panel {
    SettingsSwitchRow(
      "Opens first",
      checked = model.opensFirst,
      onCheckedChange = { sink(BikeSettingsEvent.SetOpensFirst(it)) },
      subtitle = "The app, widgets and tile start here",
      icon = LucideIcons.Pin,
      divider = false,
    )
  }
}

@Composable
private fun Components(model: BikeSettingsModel) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  SettingsSection("Components · ${model.components.size}") {
    model.components.forEachIndexed { i, component ->
      Row(
        Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CodeBadge(component.code)
        Text(
          component.name,
          style = type.itemTitle,
          color = colors.text,
          modifier = Modifier.weight(1f),
        )
        Text(component.serial, style = type.bodySmall, color = colors.muted)
      }
      if (i < model.components.lastIndex) HorizontalDivider(color = colors.line, thickness = 1.dp)
    }
  }
}

@Composable
private fun Remove(confirm: Boolean, sink: (BikeSettingsEvent) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    DestructiveButton(
      if (confirm) "Tap again to remove" else "Remove bike",
      onClick = { sink(BikeSettingsEvent.Remove) },
      armed = confirm,
      icon = LucideIcons.Trash,
    )
    Text(
      "Removes the bike and its history from this phone. Add it again by waking it.",
      style = CassetteTheme.type.bodySmall,
      color = CassetteTheme.colors.muted,
    )
  }
}
