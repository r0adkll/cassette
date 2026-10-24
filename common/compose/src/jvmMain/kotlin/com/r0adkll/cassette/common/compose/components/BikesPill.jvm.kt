package com.r0adkll.cassette.common.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.r0adkll.cassette.common.compose.theme.StatusTone

/** Renders show the Android app's solid pill. */
@Composable
internal actual fun PlatformBikesPill(
  count: Int,
  attention: StatusTone?,
  attentionLabel: String?,
  description: String,
  onClick: () -> Unit,
  modifier: Modifier,
) {
  SolidBikesPill(count, attention, attentionLabel, description, onClick, modifier)
}
