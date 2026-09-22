// QluCampus 0.2.10, GPL-3.0.
package com.dawncourse.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CampusPanelCard(modifier: Modifier = Modifier, tint: Color = Color.Unspecified,
    forceOpaque: Boolean = false, outlined: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val text = rememberBackdropText(WallpaperArea.PANEL, tint = tint, forceOpaque = forceOpaque)
    val decorated = modifier.glassSurface(shape, tint, forceOpaque).then(text.modifier)
    CampusTextColors(text.color) {
        if (outlined) OutlinedCard(decorated, shape = shape,
            colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent, contentColor = text.color), content = content)
        else Card(decorated, shape = shape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent, contentColor = text.color), content = content)
    }
}
