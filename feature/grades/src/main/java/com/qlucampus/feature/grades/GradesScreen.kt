// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.qlucampus.feature.grades

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.dawncourse.core.ui.components.CampusPanelCard
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dawncourse.core.domain.usecase.GradeMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun timeLabel(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
private fun supplied(value: String) = value.ifBlank { "未提供" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(importMode: Boolean = false, breakdownMode: Boolean = false, onLogin: () -> Unit, onBack: () -> Unit,
    onImported: () -> Unit, viewModel: GradesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val targets by viewModel.importTargets.collectAsState()
    val unlocked by viewModel.detailsUnlocked.collectAsState()
    if (breakdownMode && !unlocked) { Text("此功能已锁定"); return }
    var showGpa by rememberSaveable { mutableStateOf(false) }
    if (showGpa) {
        GpaScreen(onBack = { showGpa = false }, initialYear = state.term.year, initialTerm = state.term.semester)
        return
    }
    val context = LocalContext.current
    val vpnGate = rememberATrustQueryGate(state.account?.studentNumber to state.term)
    var year by rememberSaveable { mutableStateOf(state.term.year.toString()) }
    var term by rememberSaveable { mutableIntStateOf(state.term.semester) }
    val snackbar = remember { SnackbarHostState() }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) viewModel.exportTo { bytes -> withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("无法保存文件")
            stream.use { it.write(bytes) }
        } }
    }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.message(null) } }
    Scaffold(containerColor = Color.Transparent, topBar = { TopAppBar(title = { Text(if (importMode) "导入学校课表" else if (breakdownMode) "平时成绩" else "成绩") },
        navigationIcon = { if (importMode) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }) },
        snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("齐鲁工业大学", style = MaterialTheme.typography.titleLarge)
                        Text(state.account?.let { "当前账号 ${it.maskedNumber}" } ?: "登录学校账号，课表与成绩共享登录状态")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vpnGate.request(onLogin) }, enabled = !state.busy) { Text(if (state.account == null) "登录学校" else "重新登录 / 切换账号") }
                            if (state.account != null) TextButton(onClick = viewModel::logout, enabled = !state.busy) { Text("退出") }
                        }
                    }
                }
            }
            item { ATrustQuerySettings(vpnGate) }
            item {
                Text("学年与学期", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = year, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) year = it },
                    label = { Text("学年起始年份，如 2025") }, singleLine = true, enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..2).forEach { n -> FilterChip(selected = term == n, onClick = { term = n; viewModel.select(year, n) }, enabled = !state.busy, label = { Text("第 $n 学期") }) }
                    TextButton(onClick = { viewModel.select(year, term) }, enabled = !state.busy) { Text("应用学期") }
                }
                Text(state.term.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (importMode) {
                    Button(onClick = { vpnGate.request(viewModel::previewTimetable) }, enabled = !state.busy && state.account != null && year == state.term.year.toString() && term == state.term.semester, modifier = Modifier.fillMaxWidth()) { Text("读取课表并预览") }
                    Text(state.lastImported?.let { "上次导入：${timeLabel(it)}" } ?: "导入后可离线查看；请先连接校园网或 VPN。", style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { vpnGate.request(viewModel::refreshGrades) }, enabled = !state.busy && state.account != null && year == state.term.year.toString() && term == state.term.semester) { Text("刷新成绩") }
                        OutlinedButton(onClick = { viewModel.prepareExport()?.let(save::launch) }, enabled = !state.busy && state.snapshot != null) { Text("导出 Excel") }
                    }
                    OutlinedButton(onClick = { showGpa = true }, enabled = state.account != null) { Text("选择课程计算 GPA") }
                    Text(state.snapshot?.let { "本机记录 · 更新于 ${timeLabel(it.fetchedAt)}" } ?: "尚无本机成绩记录，登录后点击刷新。", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!importMode) {
                val snapshot = state.snapshot
                if (snapshot != null) {
                    val displaySnapshot = if (breakdownMode && unlocked) snapshot else com.dawncourse.core.domain.usecase.GradeVisibility.summary(snapshot)
                    val matched = GradeMatcher.match(displaySnapshot)
                    if (matched.courses.isEmpty() && matched.unmatchedPoints.isEmpty()) item { Text("学校暂未发布该学期成绩") }
                    items(matched.courses) { course ->
                        val total = course.details.singleOrNull { isTotalGrade(it.component) }
                        val passed = total?.let { gradePassed(it.component, it.score) }
                        val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        val tint = when (passed) {
                            true -> if (darkSurface) Color(0xFF173824) else Color(0xFFE8F5E9)
                            false -> if (darkSurface) Color(0xFF432421) else Color(0xFFFDECEA)
                            null -> Color.Unspecified
                        }
                        CampusPanelCard(tint = tint, forceOpaque = passed != null, outlined = true) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val first = course.details.first()
                                Text(first.courseName, style = MaterialTheme.typography.titleMedium)
                                if (first.courseCode.isNotBlank() || first.className.isNotBlank()) Text(listOf(first.courseCode, first.className).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                course.details.forEach { part -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(supplied(part.component), Modifier.weight(1f)); GradeScore(part.component, part.score)
                                } }
                                (if (breakdownMode && unlocked) listOf("平时", "期末", "总评") else listOf("总评")).filter { label -> course.details.none { it.component.contains(label) } }.forEach { label ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${label}成绩", Modifier.weight(1f)); Text("未提供")
                                    }
                                }
                                HorizontalDivider()
                                Text("学分 ${supplied(first.credits)}  ·  绩点 ${supplied(course.point?.point.orEmpty())}", style = MaterialTheme.typography.bodySmall)
                                Text("学分绩点 ${supplied(course.point?.weightedPoint.orEmpty())}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (matched.unmatchedPoints.isNotEmpty()) item { Text("独立绩点记录", style = MaterialTheme.typography.titleMedium); Text("以下记录无法与课程唯一匹配，按学校原始结果单独展示。", style = MaterialTheme.typography.bodySmall) }
                    items(matched.unmatchedPoints) { point -> CampusPanelCard(outlined = true) { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(point.courseName); Text("绩点 ${supplied(point.point)} · 学分绩点 ${supplied(point.weightedPoint)}")
                    } } }
                }
            }
            item { Text("仅显示学校已提供的数据。学校联调待验证。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    state.preview?.let { preview ->
        var date by rememberSaveable(preview.term) { mutableStateOf("") }
        var picker by remember { mutableStateOf(false) }
        var targetId by rememberSaveable(preview.term) { mutableStateOf<Long?>(null) }
        var name by rememberSaveable(preview.term) { mutableStateOf("${preview.term.label}课表") }
        val eligible = targets.filter { it.accountId == preview.accountId && it.term == preview.term }
        val target = eligible.firstOrNull { it.semesterId == targetId }
        if (picker) com.dawncourse.core.ui.components.MondayPicker(
            initial = runCatching { java.time.LocalDate.parse(date) }.getOrNull(), academicYear = preview.term.year,
            onDismiss = { picker = false }, onSelect = { date = it.toString(); picker = false })
        AlertDialog(onDismissRequest = { if (!state.busy) viewModel.dismissPreview() }, title = { Text("确认导入 ${preview.courses.map { it.name }.distinct().size} 门课程") },
            text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(preview.term.label)
                    FilterChip(targetId == null, { targetId = null }, label = { Text("新建独立课表") })
                    if (targetId == null) OutlinedTextField(name, { name = it }, label = { Text("新课表名称") }, singleLine = true)
                    eligible.forEach { row -> FilterChip(targetId == row.semesterId, {
                        targetId = row.semesterId
                        date = Instant.ofEpochMilli(row.startDate).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    }, label = { Text("替换：${row.profileName}") }) }
                    Text(if (targetId == null) "保存为独立课表，其他课表不变。" else "替换此课表课程，包括手动修改；其他课表不变。")
                }
                item { OutlinedButton(onClick = { picker = true }) { Text(if (date.isBlank()) "日历选择首周周一" else "首周周一：$date") } }
                items(preview.courses.distinctBy { listOf(it.name, it.teacher, it.location, it.dayOfWeek, it.startSection, it.duration) }) { c ->
                    Text("${c.name} · 周${c.dayOfWeek} ${c.startSection}–${c.startSection + c.duration - 1}节\n${c.location} ${c.teacher}")
                }
            } }, confirmButton = { Button(onClick = { viewModel.importTimetable(date, target, name, onImported) }, enabled = !state.busy && date.isNotBlank() && (targetId == null && name.isNotBlank() || target != null)) { Text("确认导入") } },
            dismissButton = { TextButton(onClick = viewModel::dismissPreview, enabled = !state.busy) { Text("取消") } })
    }
}

/** 只标注学校返回的数字总评；平时、期末及未提供/文字成绩不套用及格线。 */
private fun isTotalGrade(component: String) = component.contains("总评") || component.trim() == "成绩"
private fun gradePassed(component: String, score: String): Boolean? =
    if (isTotalGrade(component)) score.trim().toBigDecimalOrNull()?.let { it >= java.math.BigDecimal("60") } else null

@Composable
private fun GradeScore(component: String, score: String) {
    val passed = gradePassed(component, score)
    val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val color = when (passed) {
        true -> if (darkSurface) Color(0xFF85DB9D) else Color(0xFF176B32)
        false -> if (darkSurface) Color(0xFFFFB4AB) else Color(0xFFB3261E)
        null -> LocalContentColor.current
    }
    val label = when (passed) { true -> " · 合格"; false -> " · 挂科"; null -> "" }
    Text(supplied(score) + label, style = MaterialTheme.typography.titleMedium, color = color)
}
