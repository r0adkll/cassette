package com.r0adkll.cassette.common.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.theme.CassetteRed
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import kotlinx.coroutines.delay

/** The one primary action on a screen ("REFRESH NOW"), or an outlined secondary one. */
@Composable
fun CassetteButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  primary: Boolean = true,
  icon: ImageVector? = null,
  trailing: String? = null,
) {
  val colors = CassetteTheme.colors
  val shape = RoundedCornerShape(26.dp)
  val content = if (primary) colors.onInk else colors.text
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(52.dp)
        .clip(shape)
        .then(
          if (primary) Modifier.background(colors.ink)
          else Modifier.border(1.dp, colors.line, shape)
        )
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 22.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement =
      if (trailing != null) Arrangement.SpaceBetween
      else Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (icon != null)
        Icon(
          icon,
          contentDescription = null,
          tint = content,
          modifier = Modifier.size(18.dp),
        )
      Text(text.uppercase(), style = CassetteTheme.type.button, color = content)
    }
    if (trailing != null)
      Text(trailing.uppercase(), style = CassetteTheme.type.button, color = content)
  }
}

/**
 * A round 44dp icon button on a panel: back, settings, close. [badge] adds a red dot, for something
 * waiting behind it (an app update behind Settings).
 */
@Composable
fun IconCircleButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  badge: Boolean = false,
) {
  Box(modifier) {
    CircleButton(icon, contentDescription, onClick)
    if (badge)
      Box(
        Modifier.align(Alignment.TopEnd)
          .size(12.dp)
          .clip(CircleShape)
          .background(CassetteTheme.colors.background)
          .padding(2.dp)
          .clip(CircleShape)
          .background(CassetteRed)
      )
  }
}

@Composable
private fun CircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
  val colors = CassetteTheme.colors
  Box(
    Modifier.size(44.dp)
      .clip(CircleShape)
      .background(colors.panel)
      .border(1.dp, colors.line, CircleShape)
      .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      icon,
      contentDescription = contentDescription,
      tint = colors.text,
      modifier = Modifier.size(20.dp),
    )
  }
}

/**
 * An action that can't be undone ("REMOVE BIKE"), in the critical red: outlined at first, filled
 * while [armed] (waiting for the second tap), when its [icon] shakes every second or so. The first
 * tap ticks and the second buzzes.
 */
@Composable
fun DestructiveButton(
  text: String,
  onClick: () -> Unit,
  armed: Boolean,
  icon: ImageVector,
  modifier: Modifier = Modifier,
) {
  val colors = CassetteTheme.colors
  val shape = RoundedCornerShape(26.dp)
  val content = if (armed) Color.White else colors.critical
  val haptics = LocalHapticFeedback.current
  val angle = remember { Animatable(0f) }
  LaunchedEffect(armed) {
    if (!armed) {
      angle.animateTo(0f, tween(80))
      return@LaunchedEffect
    }
    while (true) {
      for (to in listOf(-14f, 12f, -9f, 6f, -3f, 0f)) angle.animateTo(to, tween(55))
      delay(900)
    }
  }
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(52.dp)
        .clip(shape)
        .then(
          if (armed) Modifier.background(colors.critical)
          else Modifier.border(1.dp, colors.critical, shape)
        )
        .clickable(role = Role.Button) {
          // A tick to arm it, a firmer buzz when it goes.
          haptics.performHapticFeedback(
            if (armed) HapticFeedbackType.Confirm else HapticFeedbackType.ContextClick
          )
          onClick()
        }
        .padding(horizontal = 22.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = content,
      modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = angle.value },
    )
    Text(text.uppercase(), style = CassetteTheme.type.button, color = content)
  }
}
