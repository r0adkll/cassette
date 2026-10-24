package com.r0adkll.cassette.common.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.r0adkll.cassette.common.compose.theme.StatusTone

/** Glass is iOS only (decision 0017): the solid chip. */
@Composable
actual fun BikeChip(
  name: String,
  tone: StatusTone,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier,
) {
  SolidBikeChip(name, tone, selected, onClick, modifier)
}
