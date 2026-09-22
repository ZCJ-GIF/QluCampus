// QluCampus 0.2.0, GPL-3.0.
package com.dawncourse.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun WelcomeNotice(onClose: (Boolean) -> Unit, allowRemember: Boolean = true) {
    var hide by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { onClose(hide) }, title = { Text("使用说明与免责声明") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("齐鲁课表是个人开发的辅助工具，并非学校官方应用。课表、成绩及空教室信息以学校教务系统和实际安排为准。")
            Text("网络、学校接口变化或程序错误可能造成更新延迟或显示偏差。选课、考试及教室使用前，请再次核对学校通知。所选课程 GPA 仅反映你勾选的数据。")
            Text("学校登录通过学校原网页完成；本应用默认不保存学校密码。请妥善保管设备与账号。")
            SelectionContainer { Text("作者微信：a3130149711\n遇到 Bug 请联系作者，反馈时请遮挡学号、成绩及其他个人信息。") }
            if (allowRemember) Row {
                Checkbox(hide, { hide = it })
                Text("下次不再显示", Modifier.padding(top = 12.dp))
            }
        }
    }, confirmButton = { TextButton(onClick = { onClose(hide) }) { Text("我知道了") } })
}
