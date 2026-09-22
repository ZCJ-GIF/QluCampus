// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.domain.usecase

import com.dawncourse.core.domain.model.*
import com.dawncourse.core.domain.repository.GradeRepository
import javax.inject.Inject

class RefreshGradesUseCase @Inject constructor(private val repository: GradeRepository) {
    suspend operator fun invoke(accountId: String, term: AcademicTerm) = repository.refresh(accountId, term)
}

class ExportGradesUseCase @Inject constructor(private val repository: GradeRepository) {
    suspend operator fun invoke(snapshot: GradeSnapshot) = repository.exportXlsx(snapshot)
}

/** 不使用位置拼接；一对多或标识冲突时宁可保留独立绩点记录。 */
object GradeMatcher {
    fun match(snapshot: GradeSnapshot): MatchedGrades {
        val groups = snapshot.details.groupBy { Triple(it.courseCode, it.className, it.courseName) }.values.toList()
        fun matches(rows: List<GradeDetail>, point: GradePoint): Boolean {
            val c = rows.first()
            if (c.courseCode.isNotBlank() && point.courseCode.isNotBlank()) {
                return c.courseCode == point.courseCode &&
                    (point.className.isBlank() || c.className == point.className)
            }
            return c.courseName == point.courseName &&
                (point.className.isBlank() || c.className == point.className)
        }
        val used = mutableSetOf<Int>()
        val courses = groups.map { rows ->
            val candidates = snapshot.points.indices.filter { matches(rows, snapshot.points[it]) }
            val index = candidates.singleOrNull()?.takeIf { i -> groups.count { matches(it, snapshot.points[i]) } == 1 }
            if (index != null) used += index
            CourseGrade(rows, index?.let(snapshot.points::get))
        }
        return MatchedGrades(courses, snapshot.points.filterIndexed { i, _ -> i !in used })
    }
}
