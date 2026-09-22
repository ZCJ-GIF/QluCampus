// QluCampus 0.2.3, GPL-3.0. Help remains available when an OEM launcher silently ignores pin requests.
package com.dawncourse.feature.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
internal fun WidgetPinControls() {
    val context = LocalContext.current
    val host = remember(context) { AndroidWidgetPinHost(context) }
    val preferences = remember(context) { context.getSharedPreferences(WIDGET_PIN_PREFS, Context.MODE_PRIVATE) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var requestedStatus by rememberSaveable { mutableStateOf(WidgetPinStatus.READY) }
    var requestToken by rememberSaveable { mutableStateOf<String?>(null) }
    var registrationReport by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmedToken by remember(preferences) { mutableStateOf(preferences.getString(WIDGET_PIN_CONFIRMED, null)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == WIDGET_PIN_CONFIRMED) confirmedToken = prefs.getString(key, null)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        // Include a confirmation delivered while this screen was not composed.
        confirmedToken = preferences.getString(WIDGET_PIN_CONFIRMED, null)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun request() {
        val result = requestWidgetPin(host, UUID.randomUUID().toString())
        requestedStatus = result.status
        requestToken = result.token
        showHelp = true
    }
    Button(onClick = { request() }) { Text("添加今日课表到桌面") }
    TextButton(onClick = { showHelp = true }) { Text("桌面小组件添加帮助") }
    TextButton(onClick = { registrationReport = widgetRegistrationReport(context) }) { Text("检测桌面组件") }
    if (showHelp) {
        val status = confirmedWidgetPinStatus(WidgetPinRequest(requestedStatus, requestToken), confirmedToken)
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("添加今日课表") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(when (status) {
                        WidgetPinStatus.READY -> "可以通过系统弹窗添加，也可以从桌面手动添加。"
                        WidgetPinStatus.REQUESTED -> "已向桌面发出添加请求，尚未确认成功。如果没有出现系统弹窗，请按下方步骤手动添加。"
                        WidgetPinStatus.CONFIRMED -> "桌面已确认添加今日课表"
                        WidgetPinStatus.UNAVAILABLE -> "当前桌面未接受应用内添加，请按下方步骤手动添加。"
                        WidgetPinStatus.FAILED -> "暂时无法调起桌面添加，请按下方步骤手动添加。"
                    })
                    Text("小米 / Redmi / POCO（澎湃 OS、MIUI）", style = MaterialTheme.typography.titleSmall)
                    Text("1. 回到桌面，双指捏合，进入添加小部件。\n2. 点击搜索，找到「安卓小部件」。\n3. 在「齐鲁课表」下找到「今日课表」，拖到桌面空白区域。")
                    Text("小部件中心首页可能不显示本应用，请进入安卓小部件列表。部分系统版本也可从「全部」或「更多」进入。", style = MaterialTheme.typography.bodySmall)
                    Text("其他桌面：长按桌面空白处 → 小组件 → 齐鲁课表 → 今日课表。添加后可长按调整尺寸，课程列表可以上下滑动。")
                    TextButton(onClick = { registrationReport = widgetRegistrationReport(context) }) { Text("列表里找不到？检测组件") }
                }
            },
            confirmButton = {
                Column {
                    if (status != WidgetPinStatus.CONFIRMED) TextButton(onClick = { request() }) {
                        Text(if (status == WidgetPinStatus.READY) "请求系统添加" else "再次请求系统添加")
                    }
                    TextButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: RuntimeException) {
                            Toast.makeText(context, "请使用手机的回到桌面手势或主屏幕键", Toast.LENGTH_LONG).show()
                        }
                    }) { Text("回到桌面手动添加") }
                }
            },
            dismissButton = { TextButton(onClick = { showHelp = false }) { Text("关闭") } }
        )
    }
    registrationReport?.let { report ->
        AlertDialog(
            onDismissRequest = { registrationReport = null },
            title = { Text("桌面组件检测") },
            text = { SelectionContainer { Text(report, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) } },
            confirmButton = { TextButton(onClick = { registrationReport = widgetRegistrationReport(context) }) { Text("重新检测") } },
            dismissButton = { TextButton(onClick = { registrationReport = null }) { Text("关闭检测") } }
        )
    }
}
