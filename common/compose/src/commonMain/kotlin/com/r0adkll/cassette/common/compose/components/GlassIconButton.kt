package com.r0adkll.cassette.common.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A top-bar circle button that floats over scrolling content (iteration 14, G1; decision 0017). On
 * iOS it's native Liquid Glass (a frosted material before iOS 26); elsewhere it's the solid
 * [IconCircleButton] circle.
 */
@Composable
expect fun GlassIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
)
