package com.r0adkll.cassette.bikes.ui.add

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.CodeBadge
import com.r0adkll.cassette.common.compose.components.IconCircleButton
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.StatusLabel
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
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(state.step.number, onClose = { sink(AddBikeEvent.Close) })
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val step = state.step) {
                    is AddBikeStep.Wake -> Wake(step, sink)
                    is AddBikeStep.Found -> Found(step, sink)
                    is AddBikeStep.Name -> Name(step, sink)
                    is AddBikeStep.PowerMeter -> PowerMeter(step, sink)
                    is AddBikeStep.KeepFresh -> KeepFresh(step)
                }
            }
            Footer(state.step, sink)
        }
    }
}

@Composable
private fun Header(step: Int, onClose: () -> Unit) {
    val colors = CassetteTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(LucideIcons.X, "Close", onClick = onClose)
        Spacer(Modifier.weight(1f))
        Kicker("Step $step of $ADD_BIKE_STEPS")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(ADD_BIKE_STEPS) { i ->
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
        style =
            CassetteTheme.type.display.copy(fontSize = CassetteTheme.type.display.fontSize * 0.93f),
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
                CassetteTheme.type.bodySmall.copy(
                    fontSize = CassetteTheme.type.body.fontSize * 0.87f
                ),
            color = colors.muted,
        )
    }
}

@Composable
private fun ColumnScope.Wake(step: AddBikeStep.Wake, sink: (AddBikeEvent) -> Unit) {
    val colors = CassetteTheme.colors
    Title("Wake your bike.")
    Body(
        "Shake the bike or press the AXS button on a shifter. Cassette listens for the rear derailleur, which reports for the whole bike."
    )
    val pulse by
        rememberInfiniteTransition()
            .animateFloat(
                0f,
                1f,
                infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Restart),
            )
    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(260.dp)) {
            val max = size.minDimension / 2
            drawCircle(colors.line, radius = max, style = Stroke(1.5.dp.toPx()))
            drawCircle(colors.line, radius = max * 0.75f, style = Stroke(1.5.dp.toPx()))
            drawCircle(
                colors.brand.copy(alpha = 1f - pulse),
                radius = max * (0.3f + 0.7f * pulse),
                style = Stroke(2.5.dp.toPx()),
            )
            drawCircle(colors.panel, radius = max * 0.3f)
            drawCircle(colors.line, radius = max * 0.3f, style = Stroke(1.dp.toPx()))
        }
        Icon(
            LucideIcons.BluetoothSearching,
            null,
            tint = colors.text,
            modifier = Modifier.size(34.dp),
        )
    }
    Row(
        Modifier.align(Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(colors.brand))
        Spacer(Modifier.width(8.dp))
        val m = step.seconds / 60
        val s = (step.seconds % 60).toString().padStart(2, '0')
        Kicker(if (step.bluetoothReady) "Listening · $m:$s" else "Bluetooth is off or not allowed")
    }
    if (step.showTips) {
        Panel {
            Text("Not finding it?", style = CassetteTheme.type.itemTitle, color = colors.text)
            Body(
                "Wake it again: AXS components sleep after a minute or so. Keep the phone near the rear derailleur. " +
                    "If the official app is connected to the bike, close it; a connected bike may stop advertising."
            )
        }
    }
}

@Composable
private fun Found(step: AddBikeStep.Found, sink: (AddBikeEvent) -> Unit) {
    val colors = CassetteTheme.colors
    Title(if (step.failed) "Couldn't read it." else "Found a bike.")
    Body(
        when {
            step.failed -> "The hub stopped answering. Wake the bike again and retry."
            step.reading -> "Reading the hub's list of components…"
            else ->
                "The rear derailleur is the hub. It listed ${step.components.size} components; more can join later."
        }
    )
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker("Hub", Modifier.weight(1f))
            Text("SRAM ${step.hubSerial}", style = CassetteTheme.type.mono, color = colors.muted)
        }
        step.components.forEach { row ->
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
                            CassetteTheme.type.numeral.copy(
                                fontSize = CassetteTheme.type.numeral.fontSize * 0.85f
                            ),
                        color = colors.text,
                    )
                    StatusLabel(row.tone)
                }
            }
        }
    }
}

@Composable
private fun Name(step: AddBikeStep.Name, sink: (AddBikeEvent) -> Unit) {
    val colors = CassetteTheme.colors
    val type = CassetteTheme.type
    Title("Name it.")
    Kicker("Name")
    BasicTextField(
        value = step.name,
        onValueChange = { sink(AddBikeEvent.NameChanged(it)) },
        singleLine = true,
        textStyle =
            type.numeral.copy(fontSize = type.numeral.fontSize * 1.08f, color = colors.text),
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
    Title("Power meter?")
    Body(
        "Power meters don't join the hub, so pick yours from what's nearby. Spin the crank: yours gets stronger."
    )
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
    Title("Keep it fresh.")
    Body(
        "When the bike wakes, Cassette reads its batteries in the background, so widgets and alerts stay current without opening the app."
    )
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
                Text("Nearby devices", style = CassetteTheme.type.itemTitle, color = colors.text)
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
private fun Footer(step: AddBikeStep, sink: (AddBikeEvent) -> Unit) {
    when (step) {
        is AddBikeStep.Wake ->
            CassetteButton(
                "Not finding it?",
                onClick = { sink(AddBikeEvent.ToggleTips) },
                primary = false,
            )
        is AddBikeStep.Found ->
            when {
                step.failed ->
                    CassetteButton(
                        "Try again",
                        onClick = { sink(AddBikeEvent.Retry) },
                        icon = LucideIcons.RefreshCw,
                    )
                step.reading -> CassetteButton("Reading…", onClick = {}, primary = false)
                else -> CassetteButton("Continue", onClick = { sink(AddBikeEvent.Continue) })
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
                if (selected) Modifier.background(colors.ink)
                else Modifier.border(1.dp, colors.line, shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
        Text(
            label.uppercase(),
            style =
                CassetteTheme.type.button.copy(
                    fontSize = CassetteTheme.type.button.fontSize * 0.88f
                ),
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
