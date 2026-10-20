package com.r0adkll.cassette.common.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.AppPlatform
import com.r0adkll.cassette.common.compose.LocalAppPlatform
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import kotlin.math.roundToInt

/**
 * An on/off switch in ink and track colours, moving as each platform's own does: Material 3's on
 * Android, one drawn after UIKit's on iOS. A null [onCheckedChange] draws it only, for a row that
 * handles the tap ([SettingsSwitchRow]); the row passes its [interactionSource] so a press anywhere
 * on it shows on the switch.
 */
@Composable
fun Toggle(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  interactionSource: MutableInteractionSource? = null,
) {
  when (LocalAppPlatform.current) {
    AppPlatform.Android -> MaterialToggle(checked, onCheckedChange, modifier, interactionSource)
    AppPlatform.Ios -> IosToggle(checked, onCheckedChange, modifier, interactionSource)
  }
}

@Composable
private fun MaterialToggle(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier,
  interactionSource: MutableInteractionSource?,
) {
  val colors = CassetteTheme.colors
  Switch(
    checked,
    onCheckedChange,
    modifier,
    interactionSource = interactionSource,
    colors =
      SwitchDefaults.colors(
        checkedThumbColor = colors.onInk,
        checkedTrackColor = colors.ink,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = colors.muted,
        uncheckedTrackColor = colors.track,
        uncheckedBorderColor = colors.muted,
      ),
  )
}

/**
 * UISwitch's size and motion: the thumb slides on a spring and stretches toward the middle while
 * pressed. Its thumb is white except on dark mode's light ink, where it takes the ink's opposite.
 */
@Composable
private fun IosToggle(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier,
  interactionSource: MutableInteractionSource?,
) {
  val colors = CassetteTheme.colors
  val interactions = interactionSource ?: remember { MutableInteractionSource() }
  val pressed by interactions.collectIsPressedAsState()
  val progress by
    animateFloatAsState(
      if (checked) 1f else 0f,
      spring(dampingRatio = 0.8f, stiffness = 500f),
      label = "thumb",
    )
  val thumbWidth by
    animateDpAsState(
      if (pressed) IosThumb + IosStretch else IosThumb,
      tween(150),
      label = "stretch",
    )
  val track by
    animateColorAsState(
      when {
        checked -> colors.ink
        colors.isDark -> colors.line
        else -> colors.track
      },
      tween(200),
      label = "track",
    )
  val thumb by
    animateColorAsState(
      if (checked || !colors.isDark) colors.onInk else colors.text,
      tween(200),
      label = "thumbColor",
    )
  Box(
    modifier
      .size(IosWidth, IosHeight)
      .clip(CircleShape)
      .background(track)
      .then(
        if (onCheckedChange != null)
          Modifier.toggleable(
            checked,
            interactions,
            indication = null,
            role = Role.Switch,
            onValueChange = onCheckedChange,
          )
        else Modifier
      )
      .padding(IosInset)
  ) {
    Box(
      Modifier.offset {
          val travel = (IosWidth - IosInset * 2 - thumbWidth).toPx()
          IntOffset((travel * progress).roundToInt(), 0)
        }
        .width(thumbWidth)
        .height(IosThumb)
        .shadow(2.dp, CircleShape)
        .background(thumb, CircleShape)
    )
  }
}

private val IosWidth = 51.dp
private val IosHeight = 31.dp
private val IosInset = 2.dp
private val IosThumb = 27.dp
private val IosStretch = 7.dp
