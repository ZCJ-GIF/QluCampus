// QluCampus 0.2.0, GPL-3.0.
package com.qlucampus.feature.grades

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dawncourse.core.domain.repository.*
import com.dawncourse.core.domain.model.*
import com.dawncourse.core.ui.components.glassSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import java.time.*

@HiltViewModel
class ClassroomsViewModel @Inject constructor(private val repo: ClassroomRepository, sessions: SchoolSessionRepository,
    profiles: TimetableProfileRepository, school: SchoolTimetableRepository) : ViewModel() {
    val account = sessions.account
    val officialUrl = repo.officialUrl
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val target = combine(profiles.observeActiveContext(), sessions.account.flatMapLatest { a -> a?.let { school.observeImports(it.studentNumber) } ?: flowOf(emptyList()) }) { context, targets ->
        targets.firstOrNull { it.profileId == context?.profile?.id && it.semesterId == context?.semester?.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    var options by mutableStateOf<ClassroomOptions?>(null); private set
    var result by mutableStateOf<ClassroomResult?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    init { viewModelScope.launch { account.collect { options = null; result = null; message = null } } }
    private fun run(action: suspend (String) -> Unit) {
        if (busy) return
        val owner = account.value?.studentNumber
        if (owner == null) { message = "请先登录学校"; return }
        busy = true
        viewModelScope.launch {
            try { action(owner) } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: SchoolLoginRequired) { if (account.value?.studentNumber == owner) message = "学校登录已失效，请重新登录" }
            catch (e: SchoolDataException) { if (account.value?.studentNumber == owner) message = e.message }
            catch (e: IllegalArgumentException) { if (account.value?.studentNumber == owner) message = e.message }
            catch (_: Exception) { if (account.value?.studentNumber == owner) message = "查询失败，请检查学校网络或 VPN；历史结果不代表当前空闲" }
            finally { busy = false }
        }
    }
    fun load(campus: String = "") = run { owner ->
        val source = target.value
        val loaded = repo.options(owner, campus, source?.term)
        if (account.value?.studentNumber == owner && target.value == source) { options = loaded; message = null }
    }
    fun query(date: LocalDate, start: Int, end: Int, campus: String, building: String, roomType: String) = run { owner ->
        val source = target.value ?: throw SchoolDataException("请先选择已导入的学校课表，用于确定学期和首周日期")
        require(source.accountId == owner)
        val first = Instant.ofEpochMilli(source.startDate).atZone(ZoneId.systemDefault()).toLocalDate()
        require(!date.isBefore(first) && date.isBefore(first.plusWeeks(source.weekCount.toLong()))) { "查询日期不在当前课表学期内" }
        val loaded = repo.query(owner, ClassroomQuery(source.term, first, date, start, end, campus, building, roomType))
        if (account.value?.studentNumber == owner && target.value == source) { result = loaded; message = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomsScreen(onLogin: () -> Unit, onOfficial: (String) -> Unit, vm: ClassroomsViewModel = hiltViewModel()) {
    val account by vm.account.collectAsState()
    val target by vm.target.collectAsState()
    val vpnGate = rememberATrustQueryGate(account?.studentNumber to target?.semesterId)
    var date by remember { mutableStateOf(LocalDate.now()) }
    var calendar by remember { mutableStateOf(false) }
    var start by remember { mutableIntStateOf(1) }
    var end by remember { mutableIntStateOf(2) }
    var campus by remember(account) { mutableStateOf("") }
    var building by remember(account, campus) { mutableStateOf("") }
    var roomType by remember(account) { mutableStateOf("") }
    val maxSection = vm.options?.sections?.maxOrNull() ?: 16
    LaunchedEffect(vm.options) {
        vm.options?.let { loaded ->
            campus = loaded.selectedCampus
            if (loaded.buildings.none { it.id == building }) building = ""
            start = start.coerceAtMost(maxSection)
            end = end.coerceIn(start, maxSection)
        }
    }
    Scaffold(containerColor = Color.Transparent, topBar = { TopAppBar(title = { Text("空教室查询") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("查询结果以学校安排和实际占用为准。按当前课表的首周日期换算教学周。", style = MaterialTheme.typography.bodySmall) }
            item { ATrustQuerySettings(vpnGate) }
            item { OutlinedButton(onClick = { vpnGate.request(onLogin) }) { Text(if (account == null) "登录学校" else "重新登录学校") }
                Button(onClick = { val selectedCampus = campus; vpnGate.request { vm.load(selectedCampus) } }, enabled = account != null && !vm.busy) { Text("加载学校查询选项") }
            }
            item { Card(Modifier.glassSurface(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(target?.let { "${it.profileName}\n${it.term.label}" } ?: "请先导入并选择学校课表")
                    if (vm.options?.nativeQuery != false) {
                    OutlinedButton(onClick = { calendar = true }) { Text("日期：$date") }
                    Row { Text("从第 $start 节", Modifier.weight(1f)); TextButton(onClick = { if (start > 1) start-- }) { Text("−") }; TextButton(onClick = { if (start < maxSection) { start++; end = maxOf(end, start) } }) { Text("＋") } }
                    Row { Text("至第 $end 节", Modifier.weight(1f)); TextButton(onClick = { if (end > start) end-- }) { Text("−") }; TextButton(onClick = { if (end < maxSection) end++ }) { Text("＋") } }
                    RoomOptionMenu("校区", vm.options?.campuses.orEmpty(), campus, allowAll = false, enabled = !vm.busy) { campus = it; building = ""; vm.load(it) }
                    RoomOptionMenu("教学楼", vm.options?.buildings.orEmpty(), building, enabled = !vm.busy) { building = it }
                    RoomOptionMenu("场地类别", vm.options?.roomTypes.orEmpty(), roomType, enabled = !vm.busy) { roomType = it }
                    if (vm.options?.nativeQuery == true) Button(onClick = {
                        val queryDate = date; val first = start; val last = end
                        val selectedCampus = campus; val selectedBuilding = building; val selectedType = roomType
                        vpnGate.request { vm.query(queryDate, first, last, selectedCampus, selectedBuilding, selectedType) }
                    }, enabled = !vm.busy && target != null && campus.isNotBlank() && campus == vm.options?.selectedCampus) { Text("查询整个时段空闲的教室") }
                    }
                    if (vm.options?.nativeQuery != true) Text(if (vm.options == null) "可直接使用学校官方查询；加载选项后检查是否支持应用内筛选。" else vm.options!!.message,
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { val url = vm.options?.officialUrl ?: vm.officialUrl; vpnGate.request { onOfficial(url) } }) { Text("在应用内打开学校查询") }
                }
            } }
            if (vm.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            vm.message?.let { m -> item { Text(m, color = MaterialTheme.colorScheme.error) } }
            vm.result?.let { result ->
                item { Text("查询记录：${result.query.date} 第 ${result.query.startSection}–${result.query.endSection} 节\n校区 ${result.query.campus.ifBlank { "全部" }} · 教学楼 ${result.query.building.ifBlank { "全部" }}\n查询于 ${Instant.ofEpochMilli(result.fetchedAt).atZone(ZoneId.systemDefault()).toLocalTime().withNano(0)}，${result.rooms.size} 间；刷新后才代表新查询。") }
                if (result.rooms.isEmpty()) item { Text("学校接口本次返回空列表，请在官方查询页面核对日期和节次；当前适配尚未完成学校实测。") }
                items(result.rooms, key = { it.id }) { room -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(room.name, style = MaterialTheme.typography.titleMedium)
                    Text(listOf(room.campus, room.building).filter(String::isNotBlank).joinToString(" · "))
                    Text("容量：${room.capacity.ifBlank { "未提供" }}")
                } } }
            }
        }
    }
    if (calendar) {
        val state = rememberDatePickerState(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        com.dawncourse.core.ui.components.DawnDatePickerDialog(state, { calendar = false }, {
            state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; calendar = false
        })
    }
}

@Composable
private fun RoomOptionMenu(label: String, options: List<ClassroomOption>, selected: String, allowAll: Boolean = true, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text("$label：${options.firstOrNull { it.id == selected }?.label ?: if (allowAll) "全部" else "请选择"}") }
        DropdownMenu(expanded, { expanded = false }) {
            if (allowAll) DropdownMenuItem({ Text("全部") }, { onSelect(""); expanded = false })
            options.forEach { option -> DropdownMenuItem({ Text(option.label) }, { onSelect(option.id); expanded = false }) }
        }
    }
}
