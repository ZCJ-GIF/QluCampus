// QluCampus 0.2.0, GPL-3.0.
package com.dawncourse.feature.timetable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dawncourse.core.domain.model.*
import com.dawncourse.core.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class SchoolTimetableBarViewModel @Inject constructor(private val profiles: TimetableProfileRepository,
    private val school: SchoolTimetableRepository, sessions: SchoolSessionRepository) : ViewModel() {
    val context = profiles.observeActiveContext().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val summaries = profiles.observeProfileSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val targets = sessions.account.flatMapLatest { a -> a?.let { school.observeImports(it.studentNumber) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val account = sessions.account
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    fun switch(profileId: Long, semesterId: Long?) = viewModelScope.launch {
        val result = profiles.switch(profileId)
        if (result is ProfileMutationResult.Success && semesterId != null) profiles.setActiveSemester(profileId, semesterId)
    }
    fun clearMessage() { message = null }
    fun refresh(target: SchoolImportTarget) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                message = when (val result = school.refresh(target)) {
                    is ImportCommitResult.Success -> "${target.profileName}已刷新，首周日期已沿用"
                    is ImportCommitResult.Rejected -> result.reason
                    is ImportCommitResult.Inconsistent -> result.reason
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: SchoolLoginRequired) { message = "请在成绩页重新登录学校后刷新" }
            catch (e: SchoolDataException) { message = e.message }
            catch (_: Exception) { message = "刷新失败，请检查校园网络或 VPN；原课表已保留" }
            finally { busy = false }
        }
    }
}

@Composable
fun SchoolTimetableControls(onManage: () -> Unit, vm: SchoolTimetableBarViewModel = hiltViewModel(),
    content: @Composable (profileName: String, onChoose: () -> Unit, onRefresh: () -> Unit, canRefresh: Boolean) -> Unit) {
    val context by vm.context.collectAsState()
    val summaries by vm.summaries.collectAsState()
    val targets by vm.targets.collectAsState()
    val account by vm.account.collectAsState()
    val target = targets.firstOrNull { it.accountId == account?.studentNumber && it.profileId == context?.profile?.id && it.semesterId == context?.semester?.id }
    var showPicker by remember { mutableStateOf(false) }
    val refreshHint = if (target != null) "首周 ${java.time.Instant.ofEpochMilli(target.startDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate()} · 更新 ${java.time.Instant.ofEpochMilli(target.importedAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))}\n刷新会替换课程的手动修改" else "先导入学校课表，或登录此课表所属账号以刷新"
    content(context?.profile?.name ?: "选择课表", { showPicker = true }, { target?.let(vm::refresh) }, target != null && !vm.busy)
    if (showPicker) AlertDialog(onDismissRequest = { showPicker = false }, title = { Text("切换课表") }, text = {
        LazyColumn { item { Text(refreshHint, style = MaterialTheme.typography.bodySmall) }; summaries.forEach { summary -> item {
            TextButton(onClick = { vm.switch(summary.profile.id, summary.activeSemester?.id); showPicker = false }) {
                Text("${if (summary.isActive) "✓ " else ""}${summary.profile.name}\n${summary.activeSemester?.name ?: "尚无学期"}")
            }
        } } }
    }, confirmButton = { TextButton(onClick = { showPicker = false; onManage() }) { Text("新建与管理课表") } })
    vm.message?.let { text -> AlertDialog(onDismissRequest = vm::clearMessage, text = { Text(text) }, confirmButton = { TextButton(onClick = vm::clearMessage) { Text("确定") } }) }
}
