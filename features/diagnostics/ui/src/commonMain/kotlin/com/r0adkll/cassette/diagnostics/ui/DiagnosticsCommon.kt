package com.r0adkll.cassette.diagnostics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.components.IconCircleButton
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.protocol.ComponentKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * A diagnostics screen: back button and [action] in a row, then the title. A null [onBack] (the
 * screen's root inside Settings' pane) leaves the row out, with its [action].
 */
@Composable
internal fun DiagnosticsScaffold(
  title: String,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  action: @Composable () -> Unit = {},
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
      if (onBack != null)
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconCircleButton(LucideIcons.ArrowLeft, "Back", onClick = onBack)
          Spacer(Modifier.weight(1f))
          action()
        }
      Text(title, style = CassetteTheme.type.title, color = CassetteTheme.colors.text)
      content()
    }
  }
}

/** A monospace key/value grid line. */
@Composable
internal fun MonoLine(key: String, value: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      key,
      style = CassetteTheme.type.mono,
      color = CassetteTheme.colors.muted,
      modifier = Modifier.widthIn(min = 84.dp),
    )
    Text(value, style = CassetteTheme.type.mono, color = CassetteTheme.colors.text)
  }
}

internal fun agoShort(elapsed: Duration): String =
  when {
    elapsed < 1.minutes -> "${elapsed.inWholeSeconds} s ago"
    elapsed.inWholeMinutes < 60 -> "${elapsed.inWholeMinutes} min ago"
    elapsed.inWholeHours < 24 -> "${elapsed.inWholeHours} h ago"
    else -> "${elapsed.inWholeDays} d ago"
  }

internal val ComponentKind.short: String
  get() =
    when (this) {
      ComponentKind.REAR_DERAILLEUR -> "RD"
      ComponentKind.FRONT_DERAILLEUR -> "FD"
      ComponentKind.LEFT_SHIFTER -> "L"
      ComponentKind.RIGHT_SHIFTER -> "R"
      ComponentKind.POWER_METER -> "PM"
      else -> label
    }
