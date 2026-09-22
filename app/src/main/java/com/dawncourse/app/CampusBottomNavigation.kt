// QluCampus 0.2.2, GPL-3.0. Compact navigation leaves room for the evening timetable.
package com.dawncourse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dawncourse.core.ui.theme.LocalAppSettings
import com.dawncourse.core.ui.components.glassSurface
import com.dawncourse.core.ui.components.WallpaperArea
import com.dawncourse.core.ui.components.rememberBackdropText
import com.dawncourse.core.domain.model.CampusTextColorMode
import com.dawncourse.core.domain.model.AppFontStyle

@Composable
internal fun CampusBottomNavigation(activeRoute: String, gradeUnlocked: Boolean, onNavigate: (String) -> Unit) {
    val tabs = listOf("timetable" to "课表", "grades" to "成绩", "classrooms" to "空教室") +
        if (gradeUnlocked) listOf("grade_details" to "平时成绩") else emptyList()
    val overlay = activeRoute == "timetable"
    val extend = LocalAppSettings.current.campusAppearance.wallpaperOnNavigation && !LocalAppSettings.current.wallpaperUri.isNullOrBlank()
    val text = rememberBackdropText(WallpaperArea.NAVIGATION, forceOpaque = !extend)
    val textOverride = LocalAppSettings.current.campusAppearance.textColorMode != CampusTextColorMode.STYLE
    Row(
        Modifier.fillMaxWidth()
            .then(text.modifier)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .then(if (extend) Modifier.glassSurface(area = WallpaperArea.NAVIGATION)
                else Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = if (overlay) .76f else 1f)))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .height(60.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { (route, label) ->
            val selected = activeRoute == route
            val color = if (textOverride) text.color else if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .selectable(selected = selected, role = Role.Tab, onClick = { onNavigate(route) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    when (route) {
                        "timetable" -> Icons.Default.CalendarMonth
                        "classrooms" -> Icons.Default.MeetingRoom
                        "grade_details" -> Icons.Default.LockOpen
                        else -> Icons.Default.Assessment
                    }, contentDescription = null, modifier = Modifier.size(22.dp), tint = color
                )
                Spacer(Modifier.height(3.dp))
                Text(label, color = color, fontSize = 12.sp, lineHeight = 16.sp,
                    fontWeight = if (LocalAppSettings.current.fontStyle == AppFontStyle.BOLD) FontWeight.Bold else if (selected) FontWeight.Medium else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}
