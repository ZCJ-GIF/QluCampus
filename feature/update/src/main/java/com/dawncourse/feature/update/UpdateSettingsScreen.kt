// QluCampus, GPL-3.0.
package com.dawncourse.feature.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSettingsScreen(currentVersion: String, currentVersionCode: Long, onBack: () -> Unit, viewModel: UpdateViewModel) {
    val config by viewModel.preferences.collectAsState()
    val state by viewModel.uiState.collectAsState()
    var editing by rememberSaveable { mutableStateOf(false) }
    var source by rememberSaveable { mutableStateOf("") }
    val busy = state is UpdateUiState.Checking || state is UpdateUiState.Downloading ||
        state is UpdateUiState.ReadyToInstall || state is UpdateUiState.InstallHandoff
    Scaffold(topBar = { TopAppBar(title = { Text("软件更新") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("齐鲁课表 $currentVersion", style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("自动检查更新", style = MaterialTheme.typography.titleMedium)
                    Text("打开应用时检查，每 6 小时最多一次", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = config.automatic, onCheckedChange = viewModel::setAutomatic)
            }
            Text(if (config.lastCheckAt == 0L) "尚未检查" else "上次检查：" +
                Instant.ofEpochMilli(config.lastCheckAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
            if (state is UpdateUiState.Checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = { viewModel.checkUpdate(true, currentVersionCode) }, enabled = !busy && config.sourceUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(if (state is UpdateUiState.Checking) "正在检查…" else "检查更新")
            }
            Text("发现新版本后可查看说明、下载并安装。安装前由系统确认，原有课表和设置保留。", style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Text("更新来源", style = MaterialTheme.typography.titleMedium)
            Text(config.sourceUrl.ifBlank { "尚未配置发布地址，作者发布后才能联网检查。" }, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { source = config.sourceUrl; editing = true }, enabled = !busy) { Text("设置更新地址") }
            Text("只从作者提供的地址获取安装包。检查更新不会发送学校账号、Cookie 或成绩。", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (editing) {
        val valid = source.isBlank() || normalizeUpdateSourceUrl(source) != null
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("设置更新地址") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("填写作者提供的 HTTPS 版本文件地址，或 GitHub 公开仓库链接（main 分支）。留空可停用联网更新。")
                OutlinedTextField(source, { source = it }, label = { Text("更新地址") }, singleLine = true, isError = !valid)
                if (!valid) Text("请输入不含账号密码的有效 HTTPS 地址", color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TextButton(onClick = { viewModel.setSourceUrl(source); editing = false }, enabled = valid) { Text("保存") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("取消") } })
    }
}
