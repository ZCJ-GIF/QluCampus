// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.qlucampus.feature.grades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dawncourse.core.domain.model.*
import com.dawncourse.core.domain.repository.*
import com.dawncourse.core.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import javax.inject.Inject

private fun currentTerm(): AcademicTerm {
    val now = LocalDate.now()
    return AcademicTerm(if (now.monthValue >= 8) now.year else now.year - 1, if (now.monthValue in 2..7) 2 else 1)
}
data class GradesUiState(
    val account: SchoolAccount? = null, val term: AcademicTerm = currentTerm(),
    val snapshot: GradeSnapshot? = null, val preview: SchoolTimetablePreview? = null,
    val lastImported: Long? = null, val busy: Boolean = false, val message: String? = null
)

@HiltViewModel
class GradesViewModel @Inject constructor(private val sessions: SchoolSessionRepository,
    private val grades: GradeRepository, private val refresh: RefreshGradesUseCase,
    private val export: ExportGradesUseCase, private val timetable: SchoolTimetableRepository, access: GradeAccessRepository) : ViewModel() {
    private val mutable = MutableStateFlow(GradesUiState())
    val state = mutable.asStateFlow()
    val detailsUnlocked = access.unlocked
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val importTargets = sessions.account.flatMapLatest { account ->
        account?.let { timetable.observeImports(it.studentNumber) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val selection = MutableStateFlow(currentTerm())
    private var pendingExport: GradeSnapshot? = null
    init {
        viewModelScope.launch {
            combine(sessions.account, selection) { a, t -> a to t }.collectLatest { (account, term) ->
                mutable.update { it.copy(account = account, term = term, snapshot = null, preview = null, lastImported = null) }
                if (account != null) {
                    mutable.update { it.copy(lastImported = timetable.lastImportedAt(account.studentNumber, term)) }
                    grades.observe(account.studentNumber, term).collect { snapshot -> mutable.update { it.copy(snapshot = snapshot) } }
                }
            }
        }
    }
    fun select(year: String, semester: Int) {
        if (state.value.busy) return
        runCatching { AcademicTerm(year.toInt(), semester) }.onSuccess { selection.value = it }
            .onFailure { message("学年应为 2000–2100 之间的四位数字") }
    }
    fun message(text: String?) { mutable.update { it.copy(message = text) } }
    fun dismissPreview() { mutable.update { it.copy(preview = null) } }
    fun refreshGrades() = task {
        val s = state.value; val a = s.account ?: throw SchoolLoginRequired()
        refresh(a.studentNumber, s.term); message("成绩已更新并保存在本机")
    }
    fun previewTimetable() = task {
        val s = state.value; val a = s.account ?: throw SchoolLoginRequired()
        val preview = timetable.preview(a.studentNumber, s.term)
        mutable.update { it.copy(preview = preview) }
    }
    fun importTimetable(date: String, target: SchoolImportTarget?, newName: String, onSuccess: () -> Unit) = task {
        val preview = state.value.preview ?: return@task
        val start = runCatching { LocalDate.parse(date) }.getOrElse { throw SchoolDataException("开学日期格式应为 YYYY-MM-DD") }
        if (start.dayOfWeek != DayOfWeek.MONDAY) throw SchoolDataException("请选择第 1 教学周的星期一")
        if (start.year !in preview.term.year..preview.term.year + 1) throw SchoolDataException("开学日期与所选学年不一致")
        when (val result = timetable.save(preview, start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), target, newName)) {
            is ImportCommitResult.Success -> { dismissPreview(); message("课表已导入"); onSuccess() }
            is ImportCommitResult.Rejected -> throw SchoolDataException(result.reason)
            is ImportCommitResult.Inconsistent -> throw SchoolDataException("课表保存需要恢复处理：${result.reason}")
        }
    }
    fun exportTo(save: suspend (ByteArray) -> Unit) = task {
        val snapshot = pendingExport ?: throw SchoolDataException("导出已取消，请重新选择导出")
        pendingExport = null
        save(export(snapshot)); message("Excel 已保存，包含成绩明细和绩点两个工作表")
    }
    fun prepareExport(): String? {
        pendingExport = state.value.snapshot
        return pendingExport?.let { "成绩单_${it.term.year}_${it.term.semester}.xlsx" }
    }
    fun logout() = task { sessions.logout(); message("已退出学校账号") }
    private fun task(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: SchoolLoginRequired) { message(e.message) }
            catch (e: SchoolDataException) { message(e.message) }
            catch (_: javax.net.ssl.SSLException) { message("学校连接证书或 TLS 握手失败，请确认网络或 VPN") }
            catch (_: java.io.IOException) { message("无法连接学校，请检查校园网络或 VPN 后重试；原数据已保留") }
            catch (_: Exception) { message("操作未完成，原有数据已保留。请重试或更新学校适配") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
