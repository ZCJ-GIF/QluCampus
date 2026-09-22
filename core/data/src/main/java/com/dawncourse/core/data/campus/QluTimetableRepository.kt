// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.data.campus

import com.dawncourse.core.domain.model.*
import com.dawncourse.core.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QluTimetableRepository @Inject constructor(
    private val api: QluSchoolApi, private val sessions: QluSessionRepository, private val dao: CampusDao,
    private val imports: ImportCommitRepository, private val htmlParser: SchoolHtmlParser
) : SchoolTimetableRepository {
    override suspend fun preview(accountId: String, term: AcademicTerm) = withContext(Dispatchers.IO) {
        val ticket = sessions.ticket(accountId)
        val raw = api.timetable(term, ticket)
        val courses = if (raw.trimStart().startsWith("<")) {
            if (raw.contains("type=\"password\"", true) || raw.contains("login", true)) throw SchoolLoginRequired()
            htmlParser.parse(raw)
        } else QluTimetableParser.parse(raw)
        sessions.check(ticket)
        if (courses.isEmpty()) throw SchoolDataException("该学期未返回可导入课程，现有课表未改动")
        SchoolTimetablePreview(accountId, term, courses, System.currentTimeMillis(), ticket.revision)
    }

    override suspend fun commit(preview: SchoolTimetablePreview, semesterStart: Long): ImportCommitResult = withContext(Dispatchers.IO) {
        sessions.commit(QluSessionRepository.Ticket(preview.accountId, preview.sessionRevision)) {
            val old = dao.imported(preview.accountId, preview.term.year, preview.term.semester)
            val account = dao.account(preview.accountId) ?: throw SchoolLoginRequired()
            val destination = when {
                old != null -> ImportDestination.OverwriteSemester(old.profileId, old.semesterId)
                else -> ImportDestination.NewSemester(account.profileId)
            }
            val request = ImportCommitRequest(destination,
                NewSemesterSpec(preview.term.label, semesterStart, maxOf(20, preview.courses.maxOf { it.endWeek })),
                preview.courses, "齐鲁工大 · ${SchoolAccount(preview.accountId).maskedNumber}",
                schoolImport = SchoolImportStamp(preview.accountId, preview.term, System.currentTimeMillis()))
            imports.commit(request)
        }
    }
    override suspend fun lastImportedAt(accountId: String, term: AcademicTerm) = dao.imported(accountId, term.year, term.semester)?.importedAt

    override fun observeImports(accountId: String) = dao.observeImports(accountId)

    override suspend fun save(preview: SchoolTimetablePreview, semesterStart: Long, target: SchoolImportTarget?, newName: String): ImportCommitResult =
        saveToTarget(preview, semesterStart, target, newName, false)

    override suspend fun refresh(target: SchoolImportTarget): ImportCommitResult {
        val preview = preview(target.accountId, target.term)
        return saveToTarget(preview, target.startDate, target, target.profileName, true)
    }

    private suspend fun saveToTarget(preview: SchoolTimetablePreview, semesterStart: Long, target: SchoolImportTarget?, newName: String, refresh: Boolean): ImportCommitResult = withContext(Dispatchers.IO) {
        val date = java.time.Instant.ofEpochMilli(semesterStart).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        require(date.dayOfWeek == java.time.DayOfWeek.MONDAY && date.year in preview.term.year..preview.term.year + 1) { "请选择所选学年内的首周周一" }
        require(target == null || target.accountId == preview.accountId && target.term == preview.term) { "不能覆盖其他账号或学期的课表" }
        sessions.commit(QluSessionRepository.Ticket(preview.accountId, preview.sessionRevision)) {
            imports.commit(ImportCommitRequest(
                destination = target?.let { ImportDestination.OverwriteSemester(it.profileId, it.semesterId) } ?: ImportDestination.NewProfile,
                semester = NewSemesterSpec(if (refresh) requireNotNull(target).semesterName else preview.term.label,
                    semesterStart, maxOf(target?.weekCount ?: 20, preview.courses.maxOf { it.endWeek })),
                courses = preview.courses, newProfileName = newName.trim(),
                schoolImport = SchoolImportStamp(preview.accountId, preview.term, System.currentTimeMillis()),
                schoolTarget = target, preserveSelection = refresh
            ))
        }
    }
}
