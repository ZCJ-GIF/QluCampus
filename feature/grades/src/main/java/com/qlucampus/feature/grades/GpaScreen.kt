// QluCampus 0.2.0, GPL-3.0.
package com.qlucampus.feature.grades

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.dawncourse.core.ui.components.glassSurface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dawncourse.core.domain.repository.*
import com.dawncourse.core.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class GpaViewModel @Inject constructor(sessions: SchoolSessionRepository, grades: GradeRepository) : ViewModel() {
    val account = sessions.account
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val courses = account.flatMapLatest { a -> a?.let { grades.observeAll(it.studentNumber).map(CalculateSelectedGpa::courses) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val chosen = MutableStateFlow<Set<GpaCourseKey>>(emptySet())
    val selected = chosen.asStateFlow()
    private var owner: String? = null
    fun syncAccount(id: String?) { if (owner != id) { chosen.value = emptySet(); owner = id } }
    fun toggle(key: GpaCourseKey) { if (key.account == account.value?.studentNumber) chosen.update { if (key in it) it - key else it + key } }
    fun selectAll(rows: List<GpaCourse>) { chosen.update { it + rows.filter { c -> c.issue == null && c.key.account == account.value?.studentNumber }.map { c -> c.key } } }
    fun clear() { chosen.value = emptySet() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpaScreen(onBack: () -> Unit, initialYear: Int, initialTerm: Int, vm: GpaViewModel = hiltViewModel()) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val account by vm.account.collectAsState()
    val rows by vm.courses.collectAsState()
    val chosen by vm.selected.collectAsState()
    LaunchedEffect(account) { vm.syncAccount(account?.studentNumber) }
    var allTerms by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("$initialYear/$initialTerm") }
    val safeRows = rows.filter { it.key.account == account?.studentNumber }
    val visible = safeRows.filter { allTerms || "${it.key.term.year}/${it.key.term.semester}" == filter }
    val result = CalculateSelectedGpa.calculate(safeRows, chosen.filter { it.account == account?.studentNumber }.toSet())
    Scaffold(containerColor = Color.Transparent, topBar = { TopAppBar(title = { Text("所选课程 GPA") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Card { Column(Modifier.padding(16.dp)) {
                Text(result.gpa?.toPlainString() ?: "未计算", style = MaterialTheme.typography.headlineLarge)
                Text("计入 ${result.count} 门 · 学分 ${result.credits.stripTrailingZeros().toPlainString()}")
                Text("加权合计 ${result.weighted.stripTrailingZeros().toPlainString()}")
                Text("Σ（学分 × 绩点）÷ Σ学分；这是所选课程的计算结果。", style = MaterialTheme.typography.bodySmall)
            } } }
            item {
                Row { FilterChip(allTerms, { allTerms = !allTerms }, label = { Text("所有已缓存学期") }) }
                if (!allTerms) safeRows.map { it.key.term }.distinct().forEach { term ->
                    FilterChip(filter == "${term.year}/${term.semester}", { filter = "${term.year}/${term.semester}" }, label = { Text(term.label) })
                }
                Row { TextButton(onClick = { vm.selectAll(visible) }) { Text("全选本筛选") }; TextButton(onClick = vm::clear) { Text("清空选择") } }
                Text("已选记录跨筛选保留；重修记录由你决定是否计入。", style = MaterialTheme.typography.bodySmall)
            }
            if (visible.isEmpty()) item { Text("没有可显示的成绩，请先在成绩页刷新相应学期。") }
            items(visible, key = { it.key.toString() }) { c ->
                OutlinedCard(Modifier.fillMaxWidth().clickable(enabled = c.issue == null) { vm.toggle(c.key) }) {
                    Row(Modifier.padding(12.dp)) {
                        Checkbox(c.key in chosen, { vm.toggle(c.key) }, enabled = c.issue == null)
                        Column(Modifier.weight(1f)) {
                            Text(c.key.name, style = MaterialTheme.typography.titleMedium)
                            Text(c.key.term.label, style = MaterialTheme.typography.bodySmall)
                            Text(listOf(c.key.code, c.key.className).filter(String::isNotBlank).joinToString(" · "))
                            Text(c.issue ?: "学分 ${c.credits} · 绩点 ${c.point}", color = if (c.issue == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
