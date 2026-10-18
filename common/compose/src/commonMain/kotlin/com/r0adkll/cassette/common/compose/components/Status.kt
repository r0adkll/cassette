package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone

val StatusTone.icon: ImageVector
  get() =
    when (this) {
      StatusTone.Good -> LucideIcons.CircleCheck
      StatusTone.Low -> LucideIcons.BatteryLow
      StatusTone.Critical -> LucideIcons.BatteryWarning
      StatusTone.Stale -> LucideIcons.History
      StatusTone.Unknown -> LucideIcons.CircleHelp
    }

/** The word that always accompanies a status colour, so colour is never the only signal. */
val StatusTone.word: String
  get() =
    when (this) {
      StatusTone.Good -> "GOOD"
      StatusTone.Low -> "LOW"
      StatusTone.Critical -> "CHARGE NOW"
      StatusTone.Stale -> "OLD"
      StatusTone.Unknown -> "UNKNOWN"
    }

/** Content colour inside a [StatusBlock]. */
private val LocalBlockContent = staticCompositionLocalOf { Color.Unspecified }

/**
 * The full-width block under the bike name. Its colour is the status. Callers make it bleed to the
 * screen edges; [horizontalPadding] keeps its text aligned with the rest of the screen.
 */
@Composable
fun StatusBlock(
  title: String,
  tone: StatusTone,
  modifier: Modifier = Modifier,
  horizontalPadding: Dp = 20.dp,
  titleStyle: TextStyle =
    CassetteTheme.type.display.copy(fontSize = CassetteTheme.type.display.fontSize * 0.93f),
  content: @Composable ColumnScope.() -> Unit = {},
) {
  val colors = CassetteTheme.colors.block(tone)
  val outline = colors.outline
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .background(colors.container)
        .then(if (outline != null) Modifier.border(1.dp, outline) else Modifier)
        .padding(horizontal = horizontalPadding, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    CompositionLocalProvider(LocalBlockContent provides colors.content) {
      Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          title,
          style = titleStyle,
          color = colors.content,
          modifier = Modifier.weight(1f),
        )
        Icon(
          tone.icon,
          contentDescription = tone.word,
          tint = colors.content,
          modifier = Modifier.size(34.dp),
        )
      }
      content()
    }
  }
}

/** Container colour inside a status block, for a [StatusBlockButton] drawn in reverse. */
private val LocalBlockContainer = staticCompositionLocalOf { Color.Unspecified }

/**
 * The dashboard's status block (decision 0014): [kicker], [title] and [content] on the left, the
 * status icon in the top-right corner (as on [StatusBlock]), and [aside] (the meta line and
 * Refresh) at the bottom of a fixed column on the right. Like [StatusBlock], callers make it bleed
 * to the edges of its pane.
 */
@Composable
fun WideStatusBlock(
  title: String,
  tone: StatusTone,
  modifier: Modifier = Modifier,
  kicker: String? = null,
  horizontalPadding: Dp = 24.dp,
  asideWidth: Dp = 240.dp,
  content: @Composable ColumnScope.() -> Unit = {},
  aside: @Composable ColumnScope.() -> Unit = {},
) {
  val colors = CassetteTheme.colors.block(tone)
  val outline = colors.outline
  val type = CassetteTheme.type
  // Both columns take the taller one's height: the text sits at the bottom on the left, and the
  // right column has the icon in the top corner and the aside at the bottom.
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min)
        .background(colors.container)
        .then(if (outline != null) Modifier.border(1.dp, outline) else Modifier)
        .padding(horizontal = horizontalPadding, vertical = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    CompositionLocalProvider(
      LocalBlockContent provides colors.content,
      LocalBlockContainer provides colors.container,
    ) {
      Column(
        Modifier.weight(1f).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
      ) {
        if (kicker != null)
          Text(kicker.uppercase(), style = type.label, color = colors.content.copy(alpha = 0.85f))
        Text(
          title,
          style = type.display.copy(fontSize = type.display.fontSize * 1.07f),
          color = colors.content,
        )
        content()
      }
      Column(Modifier.width(asideWidth).fillMaxHeight()) {
        Icon(
          tone.icon,
          contentDescription = tone.word,
          tint = colors.content,
          modifier = Modifier.align(Alignment.End).size(34.dp),
        )
        Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { aside() }
      }
    }
  }
}

/**
 * A button inside a status block, in reverse: the block's content colour with its container's text.
 */
@Composable
fun StatusBlockButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  trailing: String? = null,
) {
  val background = LocalBlockContent.current
  val content = LocalBlockContainer.current
  val shape = RoundedCornerShape(24.dp)
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(48.dp)
        .clip(shape)
        .background(background)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 18.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (icon != null)
      Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
    Text(
      text.uppercase(),
      style = CassetteTheme.type.button,
      color = content,
      modifier = Modifier.weight(1f),
    )
    if (trailing != null)
      Text(trailing.uppercase(), style = CassetteTheme.type.button, color = content)
  }
}

/** A sentence inside a [StatusBlock]: what to charge next, or what's wrong. */
@Composable
fun StatusBlockText(text: String) {
  Text(text, style = CassetteTheme.type.body, color = LocalBlockContent.current)
}

/**
 * Small uppercase line inside a [StatusBlock], with a dot: "SEEN 2 MIN AGO · BACKGROUND CAPTURE
 * ON".
 */
@Composable
fun StatusBlockMeta(text: String) {
  val color = LocalBlockContent.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
    Text(
      text.uppercase(),
      style =
        CassetteTheme.type.status.copy(fontSize = CassetteTheme.type.bodySmall.fontSize * 0.92f),
      color = color.copy(alpha = 0.85f),
    )
  }
}

/** Status icon and word, in the status colour: "✓ GOOD". */
@Composable
fun StatusLabel(
  tone: StatusTone,
  modifier: Modifier = Modifier,
  color: Color = CassetteTheme.colors.status(tone),
) {
  Row(
    modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(tone.icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
    Text(tone.word, style = CassetteTheme.type.status, color = color)
  }
}

/** A thin bar showing a level from 0 to 1 in the status colour. */
@Composable
fun LevelBar(level: Float, tone: StatusTone, modifier: Modifier = Modifier, height: Dp = 4.dp) {
  val shape = RoundedCornerShape(height / 2)
  Box(modifier.fillMaxWidth().height(height).clip(shape).background(CassetteTheme.colors.track)) {
    Box(
      Modifier.fillMaxWidth(level.coerceIn(0.04f, 1f))
        .height(height)
        .clip(shape)
        .background(CassetteTheme.colors.status(tone))
    )
  }
}

/**
 * A component's status block: the big value beside its status headline and source, then a level bar
 * (the component screen).
 */
@Composable
fun ValueStatusBlock(
  value: String,
  headline: String,
  source: String,
  level: Float,
  tone: StatusTone,
  modifier: Modifier = Modifier,
  horizontalPadding: Dp = 20.dp,
) {
  val colors = CassetteTheme.colors.block(tone)
  val outline = colors.outline
  val type = CassetteTheme.type
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .background(colors.container)
        .then(if (outline != null) Modifier.border(1.dp, outline) else Modifier)
        .padding(horizontal = horizontalPadding, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(value, style = type.numeralLarge, color = colors.content)
      Column(
        Modifier.padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            tone.icon,
            contentDescription = null,
            tint = colors.content,
            modifier = Modifier.size(26.dp),
          )
          Text(
            headline,
            style = type.title.copy(fontSize = type.title.fontSize * 0.65f),
            color = colors.content,
          )
        }
        Text(
          source,
          style = type.bodySmall.copy(fontSize = type.body.fontSize * 0.87f),
          color = colors.content,
        )
      }
    }
    val shape = RoundedCornerShape(4.dp)
    Box(
      Modifier.fillMaxWidth()
        .height(8.dp)
        .clip(shape)
        .background(colors.content.copy(alpha = 0.22f))
    ) {
      Box(
        Modifier.fillMaxWidth(level.coerceIn(0.04f, 1f))
          .height(8.dp)
          .clip(shape)
          .background(colors.content)
      )
    }
  }
}
