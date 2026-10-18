package com.r0adkll.cassette.bikes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.r0adkll.cassette.common.compose.LocalAppUpdates
import com.r0adkll.cassette.common.compose.UpdateStatus
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.SplitPanes
import com.r0adkll.cassette.common.compose.components.TopBarButton
import com.r0adkll.cassette.common.compose.currentVerticalHinge
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteRed
import com.r0adkll.cassette.common.compose.theme.CassetteTheme

/**
 * No bike yet, unfolded (decision 0014): split like onboarding, which leads here. The panel half
 * has the wordmark and, at its foot, "No bike yet."; the other half says how adding one goes, with
 * Add bike beneath and Settings at the top.
 */
@Composable
internal fun WideEmptyBike(
  eventSink: (BikeEvent) -> Unit,
  modifier: Modifier = Modifier,
  hinge: Rect? = currentVerticalHinge(),
) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  SplitPanes(
    start = {
      Column(
        Modifier.fillMaxSize()
          .background(colors.panel)
          .safeDrawingPadding()
          .padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Lined up with the Settings button across the hinge.
        Row(Modifier.padding(vertical = 13.dp)) { Kicker("Cassette") }
        Spacer(Modifier.weight(1f))
        Text(
          "No bike yet.",
          style = type.display.copy(fontSize = 80.sp, lineHeight = 0.88.em),
          color = colors.text,
        )
        Text(
          "Add your AXS bike: wake it, and Cassette finds the hub and every component.",
          style = type.body,
          color = colors.muted,
        )
      }
    },
    end = {
      Column(
        Modifier.fillMaxSize()
          .safeDrawingPadding()
          .padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        val update by LocalAppUpdates.current.status.collectAsState()
        TopBarButton(
          LucideIcons.Settings,
          if (update is UpdateStatus.Available) "Settings, app update available" else "Settings",
          onClick = { eventSink(BikeEvent.OpenSettings) },
          modifier = Modifier.align(Alignment.End),
          badge = update is UpdateStatus.Available,
        )
        Column(
          Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp),
          verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
          Step("1", "Wake it.", "Shake the bike or press the AXS button on a shifter.")
          Step(
            "2",
            "Cassette finds the hub.",
            "The rear derailleur lists every component on the bike, with its battery.",
          )
          Step(
            "3",
            "Name it.",
            "Pick its power meter if it has one, and Cassette keeps it fresh from then on.",
          )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          CassetteButton("Add bike", onClick = { eventSink(BikeEvent.AddBike) })
          Text(
            "Developers: Settings → Diagnostics can load the validation bike's readings.",
            style = type.bodySmall,
            color = colors.muted,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    },
    modifier = modifier.fillMaxSize(),
    hinge = hinge,
  )
}

/** A step of adding a bike: a big red number, a title and a line, as on the unfolded welcome. */
@Composable
private fun Step(number: String, title: String, text: String) {
  val type = CassetteTheme.type
  Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(
      number,
      style = type.numeral.copy(fontSize = 34.sp, lineHeight = 0.9.em),
      color = CassetteRed,
      modifier = Modifier.width(28.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, style = type.numeral.copy(fontSize = 24.sp), color = CassetteTheme.colors.text)
      Text(text, style = type.body, color = CassetteTheme.colors.muted)
    }
  }
}
