// QluCampus 0.2.10, GPL-3.0.
package com.dawncourse.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dawncourse.core.domain.model.*
import com.dawncourse.core.ui.components.customTextColor
import com.dawncourse.core.ui.components.resolveCampusText
import com.dawncourse.core.ui.theme.getTypography
import com.dawncourse.core.ui.util.ReadableColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CampusTextSettings(settings: AppSettings, onFont: (AppFontStyle) -> Unit, onChange: (CampusAppearance) -> Unit) {
    val value = settings.campusAppearance
    var picker by remember { mutableStateOf(false) }
    var hex by remember(value.customTextColor) { mutableStateOf(value.customTextColor) }
    // Keep the controls legible even if a user deliberately selects white on white.
    val scheme = MaterialTheme.colorScheme
    val controlText = ReadableColors.bestText(scheme.surface)
    MaterialTheme(colorScheme = scheme.copy(onSurface = controlText, onSurfaceVariant = controlText)) {
        Surface(shape = RoundedCornerShape(16.dp), color = scheme.surface, contentColor = controlText) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("字体与文字颜色", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AppFontStyle.SYSTEM to "系统字体", AppFontStyle.SERIF to "衬线字体",
                        AppFontStyle.MONOSPACE to "等宽字体", AppFontStyle.BOLD to "加粗字体").forEach { (style, label) ->
                        FilterChip(settings.fontStyle == style, onClick = { onFont(style) },
                            label = { Text(label, style = getTypography(style).bodyMedium) })
                    }
                }
                Text("应用于课程、地点、日期和导航等文字；中文字体外观随手机系统提供的字形显示。", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampusTextColorMode.entries.forEach { mode ->
                        FilterChip(value.textColorMode == mode, onClick = { onChange(value.copy(textColorMode = mode)) }, label = { Text(mode.title) })
                    }
                }
                Text(when (value.textColorMode) {
                    CampusTextColorMode.STYLE -> "保持当前风格的文字配色。"
                    CampusTextColorMode.AUTO_BW -> "浅底用黑字，深底用白字。栏位按所在位置的背景、亮度和遮罩判断；课程卡片必要时提高覆盖层，保证课程与地点清晰。"
                    CampusTextColorMode.CUSTOM -> "课程、日期、导航与普通面板使用你选择的颜色；合格、挂科和错误提示保留原有颜色。"
                }, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Color(0xFFF4F1EA), Color(0xFF253042)).forEach { background ->
                        val fg = resolveCampusText(value.textColorMode, value.customTextColor, background, ReadableColors.bestText(background))
                        Column(Modifier.weight(1f).background(background, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Text("课程 ABC", style = getTypography(settings.fontStyle).bodyMedium, color = fg)
                            Text("菏泽北楼207", style = getTypography(settings.fontStyle).bodySmall, color = fg)
                        }
                    }
                }
                if (value.textColorMode == CampusTextColorMode.CUSTOM) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("#000000", "#FFFFFF", "#202124", "#254A70", "#285E4B", "#694F80", "#873A4A", "#805F31").forEach { color ->
                            Box(Modifier.size(38.dp).background(customTextColor(color), CircleShape)
                                .border(if (value.customTextColor.equals(color, true)) 3.dp else 1.dp, scheme.primary, CircleShape)
                                .selectable(value.customTextColor.equals(color, true), role = Role.RadioButton,
                                    onClick = { onChange(value.copy(customTextColor = color)) })
                                .semantics { contentDescription = "文字颜色 $color" })
                        }
                    }
                    OutlinedButton(onClick = { picker = true }) { Text("调节色相、饱和度和亮度") }
                    OutlinedTextField(hex, { hex = it.take(7) }, singleLine = true, label = { Text("文字色值 #RRGGBB") },
                        isError = !CampusTextColorMode.validColor(hex), modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("使用六位颜色值，例如 #254A70；自定义颜色不会自动改成黑白。") })
                    Button(onClick = { onChange(value.copy(customTextColor = hex)) }, enabled = CampusTextColorMode.validColor(hex)) { Text("应用文字颜色") }
                }
                TextButton(onClick = { onFont(AppFontStyle.SYSTEM); onChange(value.copy(textColorMode = CampusTextColorMode.STYLE, customTextColor = "#202124")) }) {
                    Text("恢复默认字体与文字颜色")
                }
            }
        }
        if (picker) ColorPickerDialog(customTextColor(value.customTextColor), { picker = false }, {
            onChange(value.copy(customTextColor = "#%06X".format(it.toArgb() and 0xFFFFFF)))
            picker = false
        })
    }
}
