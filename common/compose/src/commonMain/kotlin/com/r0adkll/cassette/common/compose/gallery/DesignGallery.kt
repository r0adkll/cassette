package com.r0adkll.cassette.common.compose.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.ComponentRow
import com.r0adkll.cassette.common.compose.components.HistoryChart
import com.r0adkll.cassette.common.compose.components.IconCircleButton
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.components.StatCell
import com.r0adkll.cassette.common.compose.components.StatusBlock
import com.r0adkll.cassette.common.compose.components.StatusBlockMeta
import com.r0adkll.cassette.common.compose.components.StatusBlockText
import com.r0adkll.cassette.common.compose.components.ThickBar
import com.r0adkll.cassette.common.compose.components.ValueStatusBlock
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone

/**
 * The design-system spike: the approved bike and component screens built from real components, with
 * every status state. Real readings from the validation bike; low, charge-now and stale are
 * samples.
 */
@Composable
fun DesignGallery(
  onToggleTheme: () -> Unit,
  modifier: Modifier = Modifier,
  initialState: StatusTone = StatusTone.Good,
) {
  var state by remember { mutableStateOf(initialState) }
  val colors = CassetteTheme.colors
  Column(
    modifier
      .fillMaxSize()
      .background(colors.background)
      .verticalScroll(rememberScrollState())
      .safeDrawingPadding()
      .padding(vertical = 24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
      Kicker("Design system", Modifier.weight(1f))
      Text(
        if (colors.isDark) "LIGHT" else "DARK",
        style = CassetteTheme.type.label,
        color = colors.text,
        modifier =
          Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onToggleTheme).padding(8.dp),
      )
    }
    StatePicker(state, onPick = { state = it }, Modifier.padding(horizontal = 20.dp))
    BikeScreen(sampleFor(state))
    Spacer(Modifier.height(8.dp))
    ComponentScreen(sampleFor(state).components.first())
    Spacer(Modifier.height(8.dp))
    Column(
      Modifier.padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Kicker("Thick bars (large widget)")
      sampleFor(state).components.forEach {
        ThickBar(it.code, it.name, it.value, it.level, it.tone)
      }
      Spacer(Modifier.height(8.dp))
      Kicker("Buttons")
      CassetteButton("Refresh now", onClick = {}, trailing = "60 s scan")
      CassetteButton("Share diagnostics", onClick = {}, icon = LucideIcons.Share)
      CassetteButton("Skip", onClick = {}, primary = false)
    }
  }
}

@Composable
private fun StatePicker(
  selected: StatusTone,
  onPick: (StatusTone) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = CassetteTheme.colors
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    StatusTone.entries.forEach { tone ->
      val on = tone == selected
      val shape = RoundedCornerShape(15.dp)
      Text(
        tone.name.uppercase(),
        style = CassetteTheme.type.label.copy(fontSize = CassetteTheme.type.label.fontSize * 0.92f),
        color = if (on) colors.onInk else colors.text,
        modifier =
          Modifier.clip(shape)
            .then(
              if (on) Modifier.background(colors.ink) else Modifier.border(1.dp, colors.line, shape)
            )
            .clickable { onPick(tone) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
      )
    }
  }
}

@Composable
private fun BikeScreen(bike: SampleBike) {
  val colors = CassetteTheme.colors
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
      Kicker("Cassette", Modifier.weight(1f))
      IconCircleButton(LucideIcons.Settings, "Settings", onClick = {})
    }
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
      Text(
        "Road bike",
        style =
          CassetteTheme.type.display.copy(fontSize = CassetteTheme.type.display.fontSize * 1.07f),
        color = colors.text,
        modifier = Modifier.weight(1f),
      )
      Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Kicker("1 of 1")
        Icon(
          LucideIcons.ChevronDown,
          null,
          tint = colors.muted,
          modifier = Modifier.size(14.dp),
        )
      }
    }
    StatusBlock(bike.title, bike.tone) {
      StatusBlockText(bike.summary)
      StatusBlockMeta("${bike.seen} · background capture on")
    }
    Column(
      Modifier.padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      bike.components.forEach {
        ComponentRow(it.code, it.name, it.source, it.value, it.level, it.tone, onClick = {})
      }
      Spacer(Modifier.height(10.dp))
      CassetteButton("Refresh now", onClick = {}, trailing = "60 s scan")
    }
  }
}

@Composable
private fun ComponentScreen(component: SampleComponent) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
      IconCircleButton(LucideIcons.ArrowLeft, "Back", onClick = {})
      Spacer(Modifier.weight(1f))
      Kicker("Road bike")
    }
    Text(
      component.name,
      style = type.title,
      color = colors.text,
      modifier = Modifier.padding(horizontal = 20.dp),
    )
    ValueStatusBlock(
      value = component.value.removePrefix("≈"),
      headline = component.headline,
      source = component.source,
      level = component.level,
      tone = component.tone,
    )
    Column(
      Modifier.padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      listOf(
          listOf("Voltage" to "7.87 V", "Shifts" to "12,406", "Firmware" to "2.55.8"),
          listOf("Model" to "1066", "Serial" to "1405188718", "Last read" to "2 min"),
        )
        .forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { (label, value) ->
              StatCell(label, value, Modifier.weight(1f))
            }
          }
        }
      Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Kicker("30 days", Modifier.weight(1f))
          Text("SAMPLE DATA", style = type.status, color = colors.muted)
        }
        HistoryChart(SampleHistory)
        Text("Charged 12 days ago (sample)", style = type.body, color = colors.text)
      }
    }
  }
}

private data class SampleComponent(
  val code: String,
  val name: String,
  val source: String,
  val value: String,
  val level: Float,
  val tone: StatusTone,
) {
  val headline: String
    get() =
      when (tone) {
        StatusTone.Good -> "Good."
        StatusTone.Low -> "Low."
        StatusTone.Critical -> "Charge now."
        StatusTone.Stale -> "Old reading."
        StatusTone.Unknown -> "Unknown."
      }
}

private data class SampleBike(
  val title: String,
  val summary: String,
  val seen: String,
  val tone: StatusTone,
  val components: List<SampleComponent>,
)

private val RealComponents =
  listOf(
    SampleComponent(
      "RD",
      "Rear derailleur",
      "Reported · fw 2.55.8",
      "78%",
      0.78f,
      StatusTone.Good,
    ),
    SampleComponent(
      "FD",
      "Front derailleur",
      "Estimated from 8.09 V",
      "≈96%",
      0.96f,
      StatusTone.Good,
    ),
    SampleComponent("L", "Left shifter", "CR2032 coin cell", "2.91 V", 0.89f, StatusTone.Good),
    SampleComponent("R", "Right shifter", "CR2032 coin cell", "2.89 V", 0.86f, StatusTone.Good),
    SampleComponent("PM", "Power meter", "Quarq DZero", "85%", 0.85f, StatusTone.Good),
  )

private fun sampleFor(tone: StatusTone): SampleBike =
  when (tone) {
    StatusTone.Good ->
      SampleBike(
        "All good.",
        "Next to charge: rear derailleur, in about 3 weeks (est.)",
        "Seen 2 min ago",
        tone,
        RealComponents,
      )
    StatusTone.Low ->
      SampleBike(
        "Charge soon.",
        "Front derailleur at 18%, about 5 days left (est.)",
        "Seen 2 min ago",
        tone,
        RealComponents.map {
          if (it.code == "FD")
            it.copy(
              value = "18%",
              level = 0.18f,
              tone = tone,
              source = "Estimated from 7.46 V",
            )
          else it
        },
      )
    StatusTone.Critical ->
      SampleBike(
        "Charge now.",
        "Rear derailleur reports critical, 4% left",
        "Seen 2 min ago",
        tone,
        RealComponents.map {
          if (it.code == "RD")
            it.copy(
              value = "4%",
              level = 0.04f,
              tone = tone,
              source = "Reported critical · fw 2.55.8",
            )
          else it
        },
      )
    StatusTone.Unknown ->
      SampleBike(
        "No readings yet.",
        "Wake the bike so Cassette can read its batteries.",
        "Never seen",
        tone,
        RealComponents.map { it.copy(tone = tone, value = "—", level = 0f) },
      )
    StatusTone.Stale ->
      SampleBike(
        "Not seen in 16 days.",
        "Readings below are from 30 Sep. Ride or refresh to update.",
        "Seen 16 days ago",
        tone,
        RealComponents.map { it.copy(tone = tone) },
      )
  }

private val SampleHistory =
  listOf(62, 58, 55, 51, 48, 44, 41, 37, 98, 97, 95, 93, 91, 89, 88, 86, 84, 83, 81, 80, 78).map {
    it / 100f
  }
