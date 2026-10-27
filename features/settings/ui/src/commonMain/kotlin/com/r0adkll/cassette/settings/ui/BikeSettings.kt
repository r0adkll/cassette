package com.r0adkll.cassette.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.ChoiceChips
import com.r0adkll.cassette.common.compose.components.CodeBadge
import com.r0adkll.cassette.common.compose.components.DestructiveButton
import com.r0adkll.cassette.common.compose.components.FloatingTopBar
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.SettingsSection
import com.r0adkll.cassette.common.compose.components.SettingsSwitchRow
import com.r0adkll.cassette.common.compose.components.TopBarNavigation
import com.r0adkll.cassette.common.compose.components.TopBarSpacer
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.AddBikeScreen
import com.r0adkll.cassette.common.screens.BikeSettingsScreen
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.Component
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
  /** The system whose remove was tapped once; the next tap removes it. */
  val confirmRemoveSystem: Long? = null,
  val eventSink: (BikeSettingsEvent) -> Unit,
) : CircuitUiState

data class BikeSettingsModel(
  val name: String,
  val type: BikeType,
  val opensFirst: Boolean,
  /** Its components under their systems, the one it was added with first (decision 0028). */
  val systems: List<SystemLines>,
  /** Components in no system: the power meter. */
  val others: List<ComponentLine> = emptyList(),
)

/**
 * One of the bike's systems: named after its hub ("Dropper post system"), with the hub's serial,
 * and its components, the hub first.
 */
data class SystemLines(
  val hubSerial: Long,
  val name: String,
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

  /** Adds another system to the bike: waking and found (decision 0028). */
  data object AddSystem : BikeSettingsEvent

  /** First tap asks, second (within [RemoveTimeout]) takes the system and its components off. */
  data class RemoveSystem(val hubSerial: Long) : BikeSettingsEvent
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
    var confirmRemoveSystem by remember { mutableStateOf<Long?>(null) }
    // An unanswered "Tap again to remove" goes back to Remove bike.
    LaunchedEffect(confirmRemove) {
      if (confirmRemove) {
        delay(RemoveTimeout)
        confirmRemove = false
      }
    }
    LaunchedEffect(confirmRemoveSystem) {
      if (confirmRemoveSystem != null) {
        delay(RemoveTimeout)
        confirmRemoveSystem = null
      }
    }

    val bike = overview?.bike
    val model = overview?.let { o ->
      val (inSystems, others) = o.components.map { it.component }.partition { it.hubSerial != null }
      BikeSettingsModel(
        name = o.bike.name,
        type = o.bike.type,
        opensFirst = pinned == o.bike.id,
        systems =
          inSystems
            .groupBy { it.hubSerial!! }
            .map { (serial, members) ->
              val hub = members.firstOrNull { it.serial == serial }
              SystemLines(
                hubSerial = serial,
                name = hub?.let { "${it.kind.label} system" } ?: "Components",
                components = members.sortedByDescending { it == hub }.map { it.line() },
              ) to (hub?.addedAt ?: members.minOf { it.addedAt })
            }
            // The system it was added with first, then the others as they joined.
            .sortedWith(compareBy({ it.first.hubSerial != o.bike.hubSerial }, { it.second }))
            .map { it.first },
        others = others.map { it.line() },
      )
    }

    return BikeSettingsUiState(model, confirmRemove, confirmRemoveSystem = confirmRemoveSystem) {
      event ->
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
        BikeSettingsEvent.AddSystem -> navigator.goTo(AddBikeScreen(screen.bikeId))
        is BikeSettingsEvent.RemoveSystem ->
          if (confirmRemoveSystem != event.hubSerial) confirmRemoveSystem = event.hubSerial
          else {
            confirmRemoveSystem = null
            scope.launch { bikes.removeSystem(id, event.hubSerial) }
          }
      }
    }
  }

  private fun Component.line() = ComponentLine(kind.code, kind.label, serial?.toString() ?: key)
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
      Components(model, state.confirmRemoveSystem, sink)
      AddSystem(sink)
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
        AddSystem(sink)
        Remove(state.confirmRemove, sink)
      },
      end = { if (model != null) Components(model, state.confirmRemoveSystem, sink) },
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
        .padding(horizontal = 32.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      TopBarSpacer()
      Text(title, style = CassetteTheme.type.title, color = CassetteTheme.colors.text)
      Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        val column = Arrangement.spacedBy(14.dp)
        Column(Modifier.weight(1f), verticalArrangement = column, content = start)
        Column(Modifier.weight(1f), verticalArrangement = column, content = end)
      }
    }
    FloatingTopBar(
      Modifier.widthIn(max = 1040.dp)
        .safeDrawingPadding()
        .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
      TopBarNavigation(LucideIcons.ArrowLeft, "Back", onClick = onBack)
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

/**
 * The bike's components under their systems (decision 0028), then the power meter. With two systems
 * or more, each ends in its own remove.
 */
@Composable
private fun Components(
  model: BikeSettingsModel,
  confirmRemoveSystem: Long?,
  sink: (BikeSettingsEvent) -> Unit,
) {
  val colors = CassetteTheme.colors
  val removable = model.systems.size > 1
  model.systems.forEach { system ->
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Kicker(system.name, Modifier.weight(1f))
        if (system.hubSerial > 0)
          Text(
            "SRAM ${system.hubSerial}",
            style = CassetteTheme.type.mono,
            color = colors.muted,
          )
      }
      Panel {
        Lines(system.components)
        if (removable) {
          HorizontalDivider(color = colors.line, thickness = 1.dp)
          RemoveSystem(system, model.name, confirmRemoveSystem == system.hubSerial, sink)
        }
      }
    }
  }
  if (model.others.isNotEmpty())
    SettingsSection(if (model.others.size == 1) "Power meter" else "Power meters") {
      Lines(model.others)
    }
}

@Composable
private fun ColumnScope.Lines(lines: List<ComponentLine>) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  lines.forEachIndexed { i, component ->
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
    if (i < lines.lastIndex) HorizontalDivider(color = colors.line, thickness = 1.dp)
  }
}

/** A system's remove, at the foot of its group: a red line, then the second-tap button. */
@Composable
private fun RemoveSystem(
  system: SystemLines,
  bike: String,
  confirm: Boolean,
  sink: (BikeSettingsEvent) -> Unit,
) {
  val colors = CassetteTheme.colors
  val remove = { sink(BikeSettingsEvent.RemoveSystem(system.hubSerial)) }
  if (confirm) {
    Column(
      Modifier.padding(vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      DestructiveButton(
        "Tap again to remove",
        onClick = remove,
        armed = true,
        icon = LucideIcons.Trash,
      )
      Text(
        "Takes these ${system.components.size} components and their history off $bike. Add them again with Add a system.",
        style = CassetteTheme.type.bodySmall,
        color = colors.muted,
      )
    }
  } else {
    Row(
      Modifier.fillMaxWidth()
        .heightIn(min = 44.dp)
        .clickable(onClickLabel = "Remove ${system.name.lowercase()}", onClick = remove),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(LucideIcons.Trash, null, tint = colors.critical, modifier = Modifier.size(18.dp))
      Text(
        "Remove this system".uppercase(),
        style = CassetteTheme.type.label,
        color = colors.critical,
      )
    }
  }
}

@Composable
private fun AddSystem(sink: (BikeSettingsEvent) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    CassetteButton(
      "Add a system",
      onClick = { sink(BikeSettingsEvent.AddSystem) },
      primary = false,
      icon = LucideIcons.Plus,
    )
    Text(
      "Wake the bike to add a hub that isn't on it yet. Each system has its own hub, such as a rear shock's or a dropper's.",
      style = CassetteTheme.type.bodySmall,
      color = CassetteTheme.colors.muted,
    )
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
