package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone

private val PanelShape = RoundedCornerShape(16.dp)

/** A bordered surface for grouped content. [selected] outlines it in the text colour. */
@Composable
fun Panel(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  selected: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  val colors = CassetteTheme.colors
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(PanelShape)
        .background(colors.panel)
        .then(
          if (selected) Modifier.border(2.dp, colors.text, PanelShape)
          else Modifier.border(1.dp, colors.line, PanelShape)
        )
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    content = content,
  )
}

/** A component's short code (RD, FD, L, R, PM) in a soft tile. Stands in for component icons. */
@Composable
fun CodeBadge(code: String, modifier: Modifier = Modifier) {
  Box(
    modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(CassetteTheme.colors.track),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      code,
      style =
        CassetteTheme.type.numeral.copy(fontSize = CassetteTheme.type.numeral.fontSize * 0.58f),
      color = CassetteTheme.colors.text,
    )
  }
}

/** One component on the bike screen: badge, name and source, value with status, and a level bar. */
@Composable
fun ComponentRow(
  code: String,
  name: String,
  source: String,
  value: String,
  level: Float,
  tone: StatusTone,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  Panel(modifier, onClick) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      CodeBadge(code)
      Column(Modifier.weight(1f)) {
        Text(
          name,
          style = type.itemTitle,
          color = colors.text,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          source,
          style = type.bodySmall,
          color = colors.muted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(value, style = type.numeral, color = colors.text)
        StatusLabel(tone)
      }
    }
    LevelBar(level, tone)
  }
}

/**
 * One component on the dashboard (decision 0014): badge, name and source, status, then the value
 * beside its 30-day [trend] (0 to 1, oldest first), and a level bar. The trend is drawn in the text
 * colour while all is well and in the status colour when it isn't. [selected] outlines it while its
 * sheet is open.
 */
@Composable
fun ComponentTile(
  code: String,
  name: String,
  source: String,
  value: String,
  level: Float,
  tone: StatusTone,
  trend: List<Float>,
  modifier: Modifier = Modifier,
  trendHeight: Dp = 40.dp,
  selected: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  Panel(modifier, onClick, selected) {
    Column(
      Modifier.padding(vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CodeBadge(code)
        Column(Modifier.weight(1f)) {
          Text(
            name,
            style = type.itemTitle,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            source,
            style = type.bodySmall,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        StatusLabel(tone)
      }
      Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          value,
          style = type.numeral.copy(fontSize = type.numeral.fontSize * 1.7f, lineHeight = 0.85.em),
          color = colors.text,
          modifier = Modifier.widthIn(min = 100.dp),
        )
        Sparkline(
          trend,
          Modifier.weight(1f).height(trendHeight),
          color =
            when (tone) {
              StatusTone.Low,
              StatusTone.Critical -> colors.status(tone)
              else -> colors.text
            },
        )
      }
      LevelBar(level, tone)
    }
  }
}

/**
 * A thick bar with the text inside it, filled to [level] with a tint of the status colour (the
 * large widget's style, after Pixel's battery widget).
 */
@Composable
fun ThickBar(
  code: String,
  name: String,
  value: String,
  level: Float,
  tone: StatusTone,
  modifier: Modifier = Modifier,
) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  val shape = RoundedCornerShape(20.dp)
  Box(modifier.fillMaxWidth().height(54.dp).clip(shape).background(colors.track)) {
    Box(
      Modifier.fillMaxWidth(level.coerceIn(0.06f, 1f))
        .fillMaxHeight()
        .clip(shape)
        .background(thickFill(tone))
    )
    Row(
      Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        code,
        style = type.numeral.copy(fontSize = type.numeral.fontSize * 0.58f),
        color = colors.text,
        modifier = Modifier.width(34.dp),
      )
      Column(Modifier.weight(1f)) {
        Text(name, style = type.itemTitle, color = colors.text)
        StatusLabel(tone, color = colors.text.copy(alpha = 0.85f))
      }
      Text(value, style = type.numeral, color = colors.text)
    }
  }
}

@Composable
private fun thickFill(tone: StatusTone) =
  if (CassetteTheme.colors.isDark) {
    when (tone) {
      StatusTone.Good -> androidx.compose.ui.graphics.Color(0xFF1D5C38)
      StatusTone.Low -> androidx.compose.ui.graphics.Color(0xFF6B4800)
      StatusTone.Critical -> androidx.compose.ui.graphics.Color(0xFF8A0019)
      StatusTone.Stale,
      StatusTone.Unknown -> androidx.compose.ui.graphics.Color(0xFF2A2A2F)
    }
  } else {
    when (tone) {
      StatusTone.Good -> androidx.compose.ui.graphics.Color(0xFFBFE8CC)
      StatusTone.Low -> androidx.compose.ui.graphics.Color(0xFFFFE0A3)
      StatusTone.Critical -> androidx.compose.ui.graphics.Color(0xFFFFC7CE)
      StatusTone.Stale,
      StatusTone.Unknown -> androidx.compose.ui.graphics.Color(0xFFE2E2DE)
    }
  }

/** A labelled value in the component screen's stat grid. */
@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
  Panel(modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        label.uppercase(),
        style = CassetteTheme.type.status,
        color = CassetteTheme.colors.muted,
      )
      Text(
        value,
        style =
          CassetteTheme.type.numeral.copy(fontSize = CassetteTheme.type.numeral.fontSize * 0.75f),
        color = CassetteTheme.colors.text,
      )
    }
  }
}

/** Small uppercase label: "ROAD BIKE", "CASSETTE". */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier) {
  Text(
    text.uppercase(),
    style = CassetteTheme.type.label,
    color = CassetteTheme.colors.muted,
    modifier = modifier,
  )
}
