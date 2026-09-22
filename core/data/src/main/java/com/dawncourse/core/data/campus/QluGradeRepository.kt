// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.data.campus

import com.dawncourse.core.domain.model.*
import com.dawncourse.core.domain.repository.GradeRepository
import com.dawncourse.core.domain.repository.GradeAccessRepository
import com.dawncourse.core.domain.usecase.GradeVisibility
import kotlinx.coroutines.flow.combine
import com.google.gson.Gson
import com.dawncourse.core.data.repository.OperationalDataMutationGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QluGradeRepository @Inject constructor(private val dao: CampusDao, private val api: QluSchoolApi,
    private val sessions: QluSessionRepository, private val mutationGate: OperationalDataMutationGate, private val access: GradeAccessRepository) : GradeRepository {
    private val gson = Gson()
    override fun observeAll(accountId: String) = combine(dao.observeAllGrades(accountId), dao.observeSummaries(accountId), access.unlocked) { details, summaries, unlocked ->
        val full = details.map { gson.fromJson(it.payload, GradeSnapshot::class.java) }
        val totals = summaries.map { gson.fromJson(it.payload, GradeSnapshot::class.java) }
        (if (unlocked) full + totals.filter { total -> full.none { it.term == total.term } }
        else (totals + full.map(GradeVisibility::summary)).groupBy { it.term }.values.map { rows -> rows.maxBy { it.fetchedAt } })
    }
    override fun observe(accountId: String, term: AcademicTerm) = observeAll(accountId).map { rows -> rows.firstOrNull { it.term == term } }

    override suspend fun refresh(accountId: String, term: AcademicTerm) = withContext(Dispatchers.IO) {
        val ticket = sessions.ticket(accountId)
        val detailed = access.unlocked.value
        val summary = if (!detailed) XlsxTableCodec.read(api.gradeSummary(term, ticket)) else null
        val details = if (detailed) QluGradeParser.details(XlsxTableCodec.read(api.gradeDetails(term, ticket))) else QluGradeParser.summary(requireNotNull(summary))
        val points = QluGradeParser.points(summary ?: XlsxTableCodec.read(api.gradePoints(term, ticket)))
        val snapshot = GradeSnapshot(accountId, term, System.currentTimeMillis(), details, points)
        sessions.commit(ticket) {
            mutationGate.withMutation {
                if (detailed) {
                    if (!access.unlocked.value) throw SchoolDataException("平时成绩已锁定，未保存本次查询")
                    dao.saveGrades(CampusGradeEntity(accountId, term.year, term.semester, gson.toJson(snapshot), snapshot.fetchedAt))
                } else dao.saveSummary(CampusGradeSummaryEntity(accountId, term.year, term.semester, gson.toJson(snapshot), snapshot.fetchedAt))
            }
        }
    }
    override suspend fun exportXlsx(snapshot: GradeSnapshot): ByteArray = withContext(Dispatchers.IO) {
        sessions.check(sessions.ticket(snapshot.accountId))
        QluGradeParser.export(if (access.unlocked.value) snapshot else GradeVisibility.summary(snapshot))
    }
}
