// QluCampus 0.2.8, GPL-3.0.
package com.dawncourse.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dawncourse.core.domain.model.AppSettings
import com.dawncourse.core.domain.model.CampusAppearance
import com.dawncourse.core.domain.model.CampusStyle
import com.dawncourse.core.ui.theme.campusStyleColors
import com.dawncourse.core.ui.theme.softColorScheme

@Composable
fun CampusStyleSettings(settings: AppSettings, onChange: (CampusAppearance) -> Unit, onPickWallpaper: () -> Unit) {
    val value = settings.campusAppearance
    PreferenceCategory("配色风格与背景") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("选择一套喜欢的配色", style = MaterialTheme.typography.titleMedium)
            Text("同步调整界面与自动分配的课程颜色，支持浅色和深色模式。", style = MaterialTheme.typography.bodySmall)
            CampusStyle.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { style ->
                        val selected = style == value.style
                        val colors = campusStyleColors(style)
                        val preview = softColorScheme(colors.seed, false)
                        Surface(Modifier.weight(1f).selectable(selected, role = Role.RadioButton,
                            onClick = { onChange(value.copy(style = style)) }),
                            shape = RoundedCornerShape(16.dp), color = preview.surface,
                            border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp,
                                if (selected) preview.primary else preview.outlineVariant)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(style.title + if (selected) " ✓" else "", color = preview.onSurface, style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    colors.courses.take(4).forEach { color -> Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(6.dp)).background(color)) }
                                }
                                Text(style.description, color = preview.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            HorizontalDivider()
            Text("自定义背景应用范围", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onPickWallpaper) { Text(if (settings.wallpaperUri == null) "选择自定义背景" else "更换自定义背景") }
            Text(if (settings.wallpaperUri == null) "选择图片后，下面开启的区域会一起显示背景。" else "各区域共享同一张图片，保留文字遮罩；毛玻璃和覆盖层可在下方调整。", style = MaterialTheme.typography.bodySmall)
            AppearanceToggle("课表顶栏与日期栏", value.wallpaperOnHeader) { onChange(value.copy(wallpaperOnHeader = it)) }
            AppearanceToggle("底部导航栏", value.wallpaperOnNavigation) { onChange(value.copy(wallpaperOnNavigation = it)) }
            AppearanceToggle("课程卡片", value.wallpaperOnCourses) { onChange(value.copy(wallpaperOnCourses = it)) }
            AppearanceToggle("设置与查询面板", value.wallpaperOnPanels) { onChange(value.copy(wallpaperOnPanels = it)) }
            HorizontalDivider()
            AppearanceToggle("课程颜色适应背景", value.adaptiveCourseColors) { onChange(value.copy(adaptiveCourseColors = it)) }
            Text("自动调整课程颜色、文字明暗和最低遮罩浓度，让课程与地点清楚可读。高对比度课程保持原有实色，非本周课程仍为灰色。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AppearanceToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange, Modifier.semantics { contentDescription = title })
    }
}
