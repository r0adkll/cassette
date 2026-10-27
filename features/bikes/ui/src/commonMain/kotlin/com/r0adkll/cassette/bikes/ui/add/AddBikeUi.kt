package com.r0adkll.cassette.bikes.ui.add

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.LocalAppPlatform
import com.r0adkll.cassette.common.compose.LocalScreenLayout
import com.r0adkll.cassette.common.compose.ScreenLayout
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.CodeBadge
import com.r0adkll.cassette.common.compose.components.FooterColumn
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.ScrollColumn
import com.r0adkll.cassette.common.compose.components.SplitPanes
import com.r0adkll.cassette.common.compose.components.StatusLabel
import com.r0adkll.cassette.common.compose.components.Toggle
import com.r0adkll.cassette.common.compose.components.TopBarNavigation
import com.r0adkll.cassette.common.compose.components.pulses
import com.r0adkll.cassette.common.compose.components.rememberPulses
import com.r0adkll.cassette.common.compose.currentVerticalHinge
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.AddBikeScreen
import com.r0adkll.cassette.core.BikeType
import com.slack.circuit.codegen.annotations.CircuitInject
import dev.zacsweers.metro.AppScope

@CircuitInject(AddBikeScreen::class, AppScope::class)
@Composable
fun AddBikeUi(state: AddBikeUiState, modifier: Modifier = Modifier) {
  val sink = state.eventSink
  val step = state.step
  if (LocalScreenLayout.current == ScreenLayout.Landscape) {
    LandscapeAddBike(state, modifier)
    return
  }
  // Wide windows put waking and found on one screen (decision 0014), and split the rest alike.
  if (LocalScreenLayout.current != ScreenLayout.Column) {
    if (step is AddBikeStep.Wake || step is AddBikeStep.Found) WakeAndFound(state, modifier)
    else WideAddBike(state, modifier)
    return
  }
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    FooterColumn(
      footer = { Footer(state, sink) },
      modifier = Modifier.widthIn(max = 480.dp).fillMaxSize(),
      padding = Modifier.safeDrawingPadding().padding(horizontal = 20.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      top = { Header(state, onClose = { sink(AddBikeEvent.Close) }) },
    ) {
      Intro(state)
      StepContent(state, sink)
    }
  }
}

/**
 * A phone on its side (decision 0021): the step's title, what to do and its buttons on the left,
 * what it shows (the pulse, the hub's components, the name, the meters) on the right.
 */
@Composable
private fun LandscapeAddBike(state: AddBikeUiState, modifier: Modifier = Modifier) {
  val sink = state.eventSink
  val step = state.step
  val padding = Modifier.safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp)
  SplitPanes(
    start = {
      ScrollColumn(
        Modifier.fillMaxSize(),
        padding = padding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Header(state, onClose = { sink(AddBikeEvent.Close) })
        Intro(state)
        // The buttons sit at the foot when everything fits.
        Spacer(Modifier.weight(1f))
        Footer(state, sink)
      }
    },
    end = {
      ScrollColumn(
        Modifier.fillMaxSize(),
        padding = padding,
        // The pulse sits in the middle of its pane.
        verticalArrangement =
          if (step is AddBikeStep.Wake) Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
          else Arrangement.spacedBy(16.dp),
      ) {
        StepContent(state, sink)
      }
    },
    modifier = modifier.fillMaxSize(),
    divider = true,
  )
}

/** The step's title and what to do, above its content (or beside it, on a phone on its side). */
@Composable
private fun Intro(state: AddBikeUiState) {
  val (title, body) =
    when (val step = state.step) {
      is AddBikeStep.Wake ->
        if (state.toBike != null)
          "Wake the other system." to
            "Shake the bike or press an AXS button. Cassette listens for a hub that isn't on ${state.toBike} yet."
        else
          "Wake your bike." to
            "Shake the bike or press the AXS button on a shifter or controller. Cassette listens for its hub (the rear derailleur on most bikes), which reports for the rest."
      is AddBikeStep.Found -> foundTitle(state, step) to foundBody(state, step)
      is AddBikeStep.Name -> "Name it." to null
      is AddBikeStep.PowerMeter ->
        "Power meter?" to
          "Power meters don't join the hub, so pick yours from what's nearby. Spin the crank: yours gets stronger."
      is AddBikeStep.KeepFresh ->
        "Keep it fresh." to
          "When the bike wakes, Cassette reads its batteries in the background, so widgets and alerts stay current without opening the app."
    }
  Title(title)
  body?.let { Body(it) }
}

private fun foundTitle(state: AddBikeUiState, step: AddBikeStep.Found): String =
  when {
    step.failed -> "Couldn't read it."
    state.toBike == null -> "Found a bike."
    step.choosing -> "Found ${count(step.systems.size).lowercase()} systems."
    else -> "Found a system."
  }

/**
 * What the hubs said (decision 0028): the one hub and how many components it listed, or how many
 * hubs answered and what to do about a system that isn't this bike's.
 */
private fun foundBody(state: AddBikeUiState, step: AddBikeStep.Found): String {
  val hub = step.systems.firstOrNull()
  return when {
    step.failed -> "The hub stopped answering. Wake the bike again and retry."
    hub == null -> ""
    step.choosing && step.systems.any { it.faint && !it.on } ->
      "${count(step.systems.size)} hubs answered. One was faint, so it starts off: it may be another bike nearby."
    step.choosing ->
      "${count(step.systems.size)} hubs answered, each with its own components. Switch off a system that isn't this bike's."
    hub.reading -> "${hub.theHub} is the hub. Reading its list of components…"
    state.toBike != null ->
      "${hub.theHub} is its hub. It listed ${hub.components.size} components, which join ${state.toBike}."
    step.listening ->
      "${hub.theHub} is a hub. It listed ${hub.components.size} components; listening a few seconds more for another hub."
    else ->
      "${hub.theHub} is the hub. It listed ${hub.components.size} components; more can join later."
  }
}

/** "The dropper post", to start a sentence about the system's hub. */
private val FoundSystem.theHub: String
  get() =
    if (hubName == "AXS") "An AXS component"
    else "The ${hubName.replaceFirstChar { it.lowercase() }}"

private fun count(n: Int) =
  when (n) {
    2 -> "Two"
    3 -> "Three"
    4 -> "Four"
    else -> n.toString()
  }

@Composable
private fun ColumnScope.StepContent(state: AddBikeUiState, sink: (AddBikeEvent) -> Unit) {
  when (val step = state.step) {
    is AddBikeStep.Wake -> Wake(step)
    is AddBikeStep.Found -> Found(step, sink)
    is AddBikeStep.Name -> Name(step, sink)
    is AddBikeStep.PowerMeter -> PowerMeter(step, sink)
    is AddBikeStep.KeepFresh -> KeepFresh(step)
  }
}

@Composable
private fun Header(state: AddBikeUiState, onClose: () -> Unit) {
  val colors = CassetteTheme.colors
  val step = state.step.number
  Row(verticalAlignment = Alignment.CenterVertically) {
    // Already outside the scrolling steps; glass on iOS (iteration 14).
    TopBarNavigation(LucideIcons.X, "Close", onClick = onClose)
    Spacer(Modifier.weight(1f))
    Kicker("Step $step of ${state.steps}")
  }
  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    repeat(state.steps) { i ->
      Box(
        Modifier.weight(1f)
          .height(4.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(if (i < step) colors.text else colors.track)
      )
    }
  }
}

@Composable
private fun Title(text: String) {
  Text(
    text,
    style = CassetteTheme.type.display.copy(fontSize = CassetteTheme.type.display.fontSize * 0.93f),
    color = CassetteTheme.colors.text,
  )
}

@Composable
private fun Body(text: String) {
  Text(text, style = CassetteTheme.type.body, color = CassetteTheme.colors.muted)
}

@Composable
private fun Note(text: String, icon: ImageVector = LucideIcons.Info) {
  val colors = CassetteTheme.colors
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Icon(icon, null, tint = colors.muted, modifier = Modifier.size(16.dp).padding(top = 2.dp))
    Text(
      text,
      style =
        CassetteTheme.type.bodySmall.copy(fontSize = CassetteTheme.type.body.fontSize * 0.87f),
      color = colors.muted,
    )
  }
}

@Composable
private fun ColumnScope.Wake(step: AddBikeStep.Wake) {
  ListeningPulse(listening = true, Modifier.fillMaxWidth())
  ListeningLabel(step, Modifier.align(Alignment.CenterHorizontally))
  Known(step.known)
  if (step.showTips) Tips(adding = step.known.isNotEmpty())
}

/**
 * Hubs heard that are the bike's systems already (adding a system), so the rider knows the phone
 * hears the bike.
 */
@Composable
private fun Known(known: List<KnownSystem>) {
  if (known.isEmpty()) return
  val colors = CassetteTheme.colors
  Kicker("Heard, already on this bike")
  Panel {
    known.forEach { hub ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CodeBadge(hub.code)
        Column(Modifier.weight(1f)) {
          Text("${hub.hubName} system", style = CassetteTheme.type.itemTitle, color = colors.text)
          Text("SRAM ${hub.hubSerial}", style = CassetteTheme.type.mono, color = colors.muted)
        }
        Icon(
          LucideIcons.Check,
          "On this bike",
          tint = colors.muted,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

/**
 * The Bluetooth mark in its circle. While [listening], rings leave the circle as on the tabletop
 * refresh, and the circle swells and flushes red as each one goes; once the hub is heard the last
 * rings finish and the circle settles with a green edge.
 */
@Composable
private fun ListeningPulse(listening: Boolean, modifier: Modifier = Modifier) {
  val colors = CassetteTheme.colors
  val pulses = rememberPulses(emitting = listening, color = colors.brand)
  val edge by animateColorAsState(if (listening) colors.line else colors.good, tween(450))
  Box(
    // The rings spread past the box, about 4.5 times the circle's size, fading out as they go.
    modifier.height(260.dp).pulses(pulses, from = CircleRadius, to = 175.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier.size(CircleRadius * 2).graphicsLayer {
        val swell = 1f + 0.08f * pulses.beat
        scaleX = swell
        scaleY = swell
      },
      contentAlignment = Alignment.Center,
    ) {
      Canvas(Modifier.fillMaxSize()) {
        val beat = pulses.beat
        drawCircle(colors.panel)
        drawCircle(
          lerp(edge, colors.brand, beat),
          style = Stroke(mix(if (listening) 1.dp.toPx() else 2.dp.toPx(), 3.dp.toPx(), beat)),
        )
      }
      Icon(
        if (listening) LucideIcons.BluetoothSearching else LucideIcons.Bluetooth,
        null,
        tint = colors.text,
        modifier = Modifier.size(34.dp),
      )
    }
  }
}

/** The Bluetooth circle's radius; the pulses leave from its edge. */
private val CircleRadius = 39.dp

private fun mix(from: Float, to: Float, t: Float) = from + (to - from) * t

/** "Listening · 0:38", with the red dot. */
@Composable
private fun ListeningLabel(step: AddBikeStep.Wake, modifier: Modifier = Modifier) {
  val colors = CassetteTheme.colors
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(colors.brand))
    Spacer(Modifier.width(8.dp))
    val m = step.seconds / 60
    val s = (step.seconds % 60).toString().padStart(2, '0')
    Kicker(if (step.bluetoothReady) "Listening · $m:$s" else "Bluetooth is off or not allowed")
  }
}

@Composable
private fun Tips(adding: Boolean = false) {
  Panel {
    Text(
      "Not finding it?",
      style = CassetteTheme.type.itemTitle,
      color = CassetteTheme.colors.text,
    )
    Body(
      if (adding)
        "Wake it again: AXS components sleep after a minute or so. Press the button on the other system's controller. " +
          "If the official app is connected to the bike, close it; a connected bike may stop advertising."
      else
        "Wake it again: AXS components sleep after a minute or so. Keep the phone near the bike. " +
          "If the official app is connected to the bike, close it; a connected bike may stop advertising."
    )
    if (adding) Body("Most bikes have one hub, and this one has it already.")
  }
}

@Composable
private fun Found(step: AddBikeStep.Found, sink: (AddBikeEvent) -> Unit) {
  step.systems.forEach { system ->
    Panel { SystemContent(system, step.choosing, sink, rows = { FoundRows(system.components) }) }
  }
  if (step.listening) ListeningForHub(step.listenSeconds)
}

/**
 * A system's group: its name and hub serial, with a switch when there's a choice (decision 0028),
 * then its components, or why there are none to show.
 */
@Composable
private fun ColumnScope.SystemContent(
  system: FoundSystem,
  choosing: Boolean,
  sink: (AddBikeEvent) -> Unit,
  rows: @Composable ColumnScope.() -> Unit,
) {
  val colors = CassetteTheme.colors
  if (choosing) {
    // The whole heading is the switch, as a settings row is.
    val interactions = remember { MutableInteractionSource() }
    Row(
      Modifier.fillMaxWidth()
        .heightIn(min = 44.dp)
        .toggleable(
          system.on,
          interactions,
          indication = null,
          role = Role.Switch,
          onValueChange = { sink(AddBikeEvent.ToggleSystem(system.hubSerial)) },
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(Modifier.weight(1f)) {
        Kicker("${system.hubName} system")
        Text("SRAM ${system.hubSerial}", style = CassetteTheme.type.mono, color = colors.muted)
      }
      Toggle(system.on, onCheckedChange = null, interactionSource = interactions)
    }
  } else {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Kicker("${system.hubName} system", Modifier.weight(1f))
      Text("SRAM ${system.hubSerial}", style = CassetteTheme.type.mono, color = colors.muted)
    }
  }
  val count = system.components.size
  when {
    !system.on && system.faint -> {
      Kicker(if (count > 0) "Heard faintly · $count components" else "Heard faintly")
      Text(
        if (count > 0)
          "${sentence(system.components.map { it.name })}. Switch it on if they're on this bike."
        else "Switch it on if it's this bike's.",
        style = CassetteTheme.type.bodySmall,
        color = colors.muted,
      )
    }
    !system.on ->
      Text(
        if (count > 0) "Not on this bike. Its $count components stay someone else's."
        else "Not on this bike.",
        style = CassetteTheme.type.bodySmall,
        color = colors.muted,
      )
    system.failed ->
      Text(
        "The hub stopped answering. Wake the bike and try again.",
        style = CassetteTheme.type.bodySmall,
        color = colors.low,
      )
    system.reading ->
      Text("Reading the hub…", style = CassetteTheme.type.bodySmall, color = colors.muted)
    else -> rows()
  }
}

/** "Rear derailleur, front derailleur, right shifter": names run on in a sentence. */
private fun sentence(names: List<String>) =
  names
    .mapIndexed { i, name -> if (i == 0) name else name.replaceFirstChar { it.lowercase() } }
    .joinToString()

/** The components a hub listed, as rows of a panel. */
@Composable
private fun FoundRows(rows: List<FoundRow>) {
  val colors = CassetteTheme.colors
  rows.forEach { row ->
    Row(
      Modifier.padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      CodeBadge(row.code)
      Column(Modifier.weight(1f)) {
        Text(row.name, style = CassetteTheme.type.itemTitle, color = colors.text)
        Text(row.source, style = CassetteTheme.type.bodySmall, color = colors.muted)
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
          row.value,
          style =
            CassetteTheme.type.numeral.copy(fontSize = CassetteTheme.type.numeral.fontSize * 0.85f),
          color = colors.text,
        )
        StatusLabel(row.tone)
      }
    }
  }
}

/** After the first hub, while another can still join (decision 0028). */
@Composable
private fun ListeningForHub(seconds: Int) {
  val colors = CassetteTheme.colors
  DashedBox {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(colors.brand))
        Spacer(Modifier.width(8.dp))
        Kicker("Listening for another hub · 0:${seconds.toString().padStart(2, '0')}")
      }
      Text(
        "Most bikes have one. On a mountain bike, the rear shock or the dropper can head a second system.",
        style = CassetteTheme.type.bodySmall,
        color = colors.muted,
      )
    }
  }
}

@Composable
private fun Name(
  step: AddBikeStep.Name,
  sink: (AddBikeEvent) -> Unit,
  /** False where a heading over the step already says "Name" (wide windows). */
  labelled: Boolean = true,
) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  if (labelled) Kicker("Name")
  BasicTextField(
    value = step.name,
    onValueChange = { sink(AddBikeEvent.NameChanged(it)) },
    singleLine = true,
    textStyle = type.numeral.copy(fontSize = type.numeral.fontSize * 1.08f, color = colors.text),
    cursorBrush = SolidColor(colors.brand),
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(colors.panel)
        .border(1.5.dp, colors.text, RoundedCornerShape(16.dp))
        .padding(horizontal = 16.dp, vertical = 14.dp),
  )
  Kicker("Type")
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    BikeType.entries.forEach { option ->
      Chip(
        label = option.label,
        icon = option.icon,
        selected = option == step.type,
        onClick = { sink(AddBikeEvent.TypeSelected(option)) },
      )
    }
  }
  Note("A photo of the bike comes in a later version; Cassette shows the bike type until then.")
}

@Composable
private fun PowerMeter(step: AddBikeStep.PowerMeter, sink: (AddBikeEvent) -> Unit) {
  val colors = CassetteTheme.colors
  if (step.meters.isEmpty())
    Note(
      "No power meter heard yet. Spin the crank to wake it, or skip.",
      LucideIcons.BluetoothSearching,
    )
  step.meters.forEach { meter ->
    val selected = meter.name == step.selected
    Panel(onClick = { sink(AddBikeEvent.MeterSelected(if (selected) null else meter.name)) }) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CodeBadge("PM")
        Column(Modifier.weight(1f)) {
          Text(meter.name, style = CassetteTheme.type.itemTitle, color = colors.text)
          Text(meter.detail, style = CassetteTheme.type.bodySmall, color = colors.muted)
        }
        SignalBars(meter.bars)
        Box(
          Modifier.size(26.dp)
            .clip(CircleShape)
            .background(if (selected) colors.ink else colors.panel)
            .border(1.5.dp, if (selected) colors.ink else colors.line, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          if (selected)
            Icon(
              LucideIcons.Check,
              null,
              tint = colors.onInk,
              modifier = Modifier.size(16.dp),
            )
        }
      }
    }
  }
}

@Composable
private fun SignalBars(bars: Int) {
  val colors = CassetteTheme.colors
  Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
    repeat(4) { i ->
      Box(
        Modifier.width(4.dp)
          .height((6 + i * 4).dp)
          .clip(RoundedCornerShape(2.dp))
          .background(if (i < bars) colors.text else colors.track)
      )
    }
  }
}

@Composable
private fun KeepFresh(step: AddBikeStep.KeepFresh) {
  val colors = CassetteTheme.colors
  Panel {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.track),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          LucideIcons.Bluetooth,
          null,
          tint = colors.text,
          modifier = Modifier.size(20.dp),
        )
      }
      Column(Modifier.weight(1f)) {
        Text(
          LocalAppPlatform.current.bluetoothPermission,
          style = CassetteTheme.type.itemTitle,
          color = colors.text,
        )
        Text(
          "Hear the bike when it wakes",
          style = CassetteTheme.type.bodySmall,
          color = colors.muted,
        )
      }
      if (step.bluetoothReady) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            LucideIcons.CircleCheck,
            null,
            tint = colors.good,
            modifier = Modifier.size(14.dp),
          )
          Text("ALLOWED", style = CassetteTheme.type.status, color = colors.good)
        }
      } else {
        Text("NOT ALLOWED", style = CassetteTheme.type.status, color = colors.low)
      }
    }
  }
  Note("Tip: wake the bike now and then, even off the bike. Each wake is a fresh reading.")
}

@Composable
private fun Footer(
  state: AddBikeUiState,
  sink: (AddBikeEvent) -> Unit,
  /** The found step's way on; the wide screen says "This is my bike", as in iteration 10. */
  foundLabel: String = "Continue",
) {
  when (val step = state.step) {
    is AddBikeStep.Wake ->
      CassetteButton(
        "Not finding it?",
        onClick = { sink(AddBikeEvent.ToggleTips) },
        primary = false,
      )
    is AddBikeStep.Found -> {
      val retry = { sink(AddBikeEvent.Retry) }
      val onward = state.toBike?.let { "Add to $it" } ?: foundLabel
      when {
        step.failed -> CassetteButton("Try again", onClick = retry, icon = LucideIcons.RefreshCw)
        step.reading -> CassetteButton("Reading…", onClick = {}, primary = false)
        step.saving.isEmpty() ->
          CassetteButton("Switch a system on", onClick = {}, primary = false, enabled = false)
        step.partlyFailed ->
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CassetteButton(onward, onClick = { sink(AddBikeEvent.Continue) })
            CassetteButton(
              "Try again",
              onClick = retry,
              primary = false,
              icon = LucideIcons.RefreshCw,
            )
          }
        else -> CassetteButton(onward, onClick = { sink(AddBikeEvent.Continue) })
      }
    }
    is AddBikeStep.Name -> CassetteButton("Continue", onClick = { sink(AddBikeEvent.Continue) })
    is AddBikeStep.PowerMeter ->
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (step.selected != null)
          CassetteButton("Attach", onClick = { sink(AddBikeEvent.Continue) })
        CassetteButton("Skip", onClick = { sink(AddBikeEvent.Skip) }, primary = false)
      }
    is AddBikeStep.KeepFresh ->
      CassetteButton(
        if (step.saving) "Saving…" else "Finish",
        onClick = { sink(AddBikeEvent.Continue) },
      )
  }
}

@Composable
private fun Chip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
  val colors = CassetteTheme.colors
  val shape = RoundedCornerShape(20.dp)
  val content = if (selected) colors.onInk else colors.text
  Row(
    Modifier.height(40.dp)
      .clip(shape)
      .then(
        if (selected) Modifier.background(colors.ink) else Modifier.border(1.dp, colors.line, shape)
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
    Text(
      label.uppercase(),
      style = CassetteTheme.type.button.copy(fontSize = CassetteTheme.type.button.fontSize * 0.88f),
      color = content,
    )
  }
}

private val BikeType.label: String
  get() =
    when (this) {
      BikeType.Road -> "Road"
      BikeType.Gravel -> "Gravel"
      BikeType.Mtb -> "MTB"
      BikeType.Other -> "Other"
    }

private val BikeType.icon: ImageVector
  get() =
    when (this) {
      BikeType.Road -> LucideIcons.Bike
      BikeType.Gravel -> LucideIcons.Route
      BikeType.Mtb -> LucideIcons.Mountain
      BikeType.Other -> LucideIcons.CircleHelp
    }

/**
 * Waking and found as one screen on wide windows (decision 0014, iteration 10), split at the hinge:
 * the listening pulse and timer on the left, each system's components arriving on the right
 * (decision 0028), with the button that moves on beneath them.
 */
@Composable
internal fun WakeAndFound(
  state: AddBikeUiState,
  modifier: Modifier = Modifier,
  hinge: Rect? = currentVerticalHinge(),
) {
  val colors = CassetteTheme.colors
  val sink = state.eventSink
  val step = state.step
  val found = step as? AddBikeStep.Found
  val adding = state.toBike != null
  SplitPanes(
    start = {
      Column(
        Modifier.fillMaxSize()
          .safeDrawingPadding()
          .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Header(state, onClose = { sink(AddBikeEvent.Close) })
        Title(
          when {
            found != null -> foundTitle(state, found)
            adding -> "Wake the other system."
            else -> "Wake your bike."
          }
        )
        Body(
          when {
            found != null -> foundBody(state, found)
            adding ->
              "Shake the bike or press an AXS button. Cassette listens for a hub that isn't on ${state.toBike} yet."
            else -> "Shake the bike or press the AXS button on a shifter. Keep the phone close."
          }
        )
        Spacer(Modifier.weight(1f))
        ListeningPulse(listening = found == null, Modifier.fillMaxWidth())
        if (step is AddBikeStep.Wake) {
          ListeningLabel(step, Modifier.align(Alignment.CenterHorizontally))
          Text(
            if (step.showTips) "Hide tips" else "Not finding it?",
            style = CassetteTheme.type.itemTitle,
            color = colors.muted,
            modifier =
              Modifier.align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = { sink(AddBikeEvent.ToggleTips) })
                .padding(horizontal = 10.dp, vertical = 6.dp),
          )
        } else if (found != null) {
          val hubs = found.systems
          Kicker(
            if (hubs.size == 1) "Hub · SRAM ${hubs.single().hubSerial}"
            else "${hubs.size} hubs · ${found.saving.sumOf { it.components.size }} components",
            Modifier.align(Alignment.CenterHorizontally),
          )
        }
      }
    },
    end = {
      Column(
        Modifier.fillMaxSize()
          .background(colors.panel)
          .safeDrawingPadding()
          .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        val shown = found?.saving?.sumOf { it.components.size } ?: 0
        Box(Modifier.height(44.dp), contentAlignment = Alignment.CenterStart) {
          Kicker(if (shown == 0) "Found" else "Found · $shown")
        }
        Column(
          Modifier.weight(1f).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          found?.systems?.forEach { system ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              SystemContent(
                system,
                found.choosing,
                sink,
                rows = { system.components.forEach { FoundCard(it) } },
              )
            }
          }
          when {
            step is AddBikeStep.Wake && step.showTips -> Tips(adding = step.known.isNotEmpty())
            step is AddBikeStep.Wake -> {
              Known(step.known)
              WaitingCard("The bike's components appear here as the hub answers.")
            }
            found != null && found.listening -> ListeningForHub(found.listenSeconds)
          }
          if (!adding)
            Note("Power meters aren't in the hubs' tables; you pick one on the next step.")
        }
        when {
          found == null -> CassetteButton("Waiting for the bike…", onClick = {}, primary = false)
          else -> Footer(state, sink, foundLabel = "This is my bike")
        }
      }
    },
    modifier = modifier.fillMaxSize(),
    hinge = hinge,
  )
}

/**
 * Naming, the power meter and keeping fresh on wide windows, laid out as [WakeAndFound]: the
 * header, title and what to do on the left; what the step asks for on the right, in the panel
 * colour, with its buttons at the foot where waking and found have theirs. Split at the hinge.
 */
@Composable
internal fun WideAddBike(
  state: AddBikeUiState,
  modifier: Modifier = Modifier,
  hinge: Rect? = currentVerticalHinge(),
) {
  val colors = CassetteTheme.colors
  val sink = state.eventSink
  val step = state.step
  SplitPanes(
    start = {
      Column(
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .safeDrawingPadding()
          .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Header(state, onClose = { sink(AddBikeEvent.Close) })
        Intro(state)
      }
    },
    end = {
      Column(
        Modifier.fillMaxSize()
          .background(colors.panel)
          .safeDrawingPadding()
          .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Level with the close button, as found's "Found" is.
        Box(Modifier.height(44.dp), contentAlignment = Alignment.CenterStart) {
          Kicker(
            when (step) {
              is AddBikeStep.Wake,
              is AddBikeStep.Found -> "Found"
              is AddBikeStep.Name -> "Name"
              is AddBikeStep.PowerMeter ->
                if (step.meters.isEmpty()) "Nearby" else "Nearby · ${step.meters.size}"
              is AddBikeStep.KeepFresh -> "Permission"
            }
          )
        }
        Column(
          Modifier.weight(1f).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (step is AddBikeStep.Name) Name(step, sink, labelled = false)
          else StepContent(state, sink)
        }
        Footer(state, sink)
      }
    },
    modifier = modifier.fillMaxSize(),
    hinge = hinge,
  )
}

/** A component the hub listed: code, name, where its reading comes from, and its value. */
@Composable
private fun FoundCard(row: FoundRow) {
  val colors = CassetteTheme.colors
  Row(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(colors.background)
      .border(1.dp, colors.line, RoundedCornerShape(16.dp))
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    CodeBadge(row.code)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
      Text(row.name, style = CassetteTheme.type.itemTitle, color = colors.text)
      Text(row.source, style = CassetteTheme.type.bodySmall, color = colors.muted)
    }
    Column(horizontalAlignment = Alignment.End) {
      Text(
        row.value,
        style =
          CassetteTheme.type.numeral.copy(fontSize = CassetteTheme.type.numeral.fontSize * 0.85f),
        color = colors.text,
      )
      StatusLabel(row.tone)
    }
  }
}

/** A dashed placeholder for what hasn't answered yet. */
@Composable
private fun WaitingCard(text: String) {
  DashedBox {
    Text(text, style = CassetteTheme.type.bodySmall, color = CassetteTheme.colors.muted)
  }
}

/** A dashed outline around what's still to come. */
@Composable
private fun DashedBox(content: @Composable () -> Unit) {
  val colors = CassetteTheme.colors
  Box(
    Modifier.fillMaxWidth()
      .drawBehind {
        val radius = 16.dp.toPx()
        drawRoundRect(
          colors.line,
          cornerRadius = CornerRadius(radius, radius),
          style =
            Stroke(
              1.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
            ),
        )
      }
      .padding(horizontal = 16.dp, vertical = 18.dp)
  ) {
    content()
  }
}
