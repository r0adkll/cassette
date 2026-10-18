package com.r0adkll.cassette.common.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Glass is iOS only (decision 0016): the solid circle. */
@Composable
actual fun GlassIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier,
) {
  Box(modifier) { CircleButton(icon, contentDescription, onClick) }
}
