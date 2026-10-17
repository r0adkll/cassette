package com.r0adkll.cassette.common.compose.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.common.compose.theme.CassetteTheme

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

/** A round 44dp icon button on a panel: back, settings, close. */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CassetteTheme.colors
    Box(
        modifier
            .size(44.dp)
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
