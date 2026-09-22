// QluCampus 0.2.0, GPL-3.0.
package com.dawncourse.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dawncourse.core.domain.model.CampusAppearance
import com.dawncourse.core.ui.components.WelcomeNotice

@Composable
fun CampusAppearanceSettings(value: CampusAppearance, onChange: (CampusAppearance) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    var showNotice by remember { mutableStateOf(false) }
    PreferenceCategory("清晰度与桌面小组件") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row { Text("标准周课表布局", Modifier.weight(1f)); Switch(value.fitTimetableToScreen, { onChange(value.copy(fitTimetableToScreen = it)) }) }
            Text("默认首屏显示至第 9、10 节，卡片优先显示课程名称和上课地点；底部半透明导航上方滑动可看全末节，教师等信息点开查看。第 11 节以后或放大字号时可滚动；关闭后使用完整信息布局。", style = MaterialTheme.typography.bodySmall)
            Text("文字大小 ${(draft.fontScale * 100).toInt()}%")
            Slider(draft.fontScale, { draft = draft.copy(fontScale = it) }, valueRange = .85f..1.5f, onValueChangeFinished = { onChange(draft) })
            Text("课程名称 · 上课地点（字号随卡片空间适配）", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { onChange(value.copy(fontScale = 1f)) }) { Text("恢复默认字号") }
            Row { Text("高颜色对比度", Modifier.weight(1f)); Switch(value.highContrast, { onChange(value.copy(highContrast = it)) }) }
            Text("开启后课程采用实色背景，优先保证课程区分与文字清晰。", style = MaterialTheme.typography.bodySmall)
            Row { Text("局部毛玻璃", Modifier.weight(1f)); Switch(value.glassEnabled, { onChange(value.copy(glassEnabled = it)) }) }
            Text("模糊强度 ${draft.glassRadius.toInt()}")
            Slider(draft.glassRadius, { draft = draft.copy(glassRadius = it) }, valueRange = 0f..32f, enabled = value.glassEnabled, onValueChangeFinished = { onChange(draft) })
            Text("卡片覆盖层 ${(draft.glassOpacity * 100).toInt()}%")
            Slider(draft.glassOpacity, { draft = draft.copy(glassOpacity = it) }, valueRange = .4f..1f, enabled = value.glassEnabled, onValueChangeFinished = { onChange(draft) })
            WidgetPinControls()
            TextButton(onClick = { showNotice = true }) { Text("免责声明与作者联系方式") }
        }
    }
    if (showNotice) WelcomeNotice(onClose = { showNotice = false }, allowRemember = false)
}
