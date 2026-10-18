package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Glass is iOS only (decision 0016): the solid circle, centred in the top bar's slot. */
@Composable
actual fun GlassIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier,
) {
  Box(modifier.size(TopBarButtonSize), contentAlignment = Alignment.Center) {
    CircleButton(icon, contentDescription, onClick)
  }
}
