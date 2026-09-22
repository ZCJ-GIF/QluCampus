// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.domain.model

/** 齐鲁课表新增：学校数据模型，不依赖 Android 或网络库。 */
data class AcademicTerm(val year: Int, val semester: Int) {
    init { require(year in 2000..2100 && semester in 1..2) { "请输入有效学年和学期" } }
    val schoolTermCode: String get() = if (semester == 1) "3" else "12"
    val label: String get() = "$year-${year + 1} 学年 · 第 $semester 学期"
}

data class SchoolAccount(val studentNumber: String) {
    val maskedNumber: String get() = if (studentNumber.length > 4) "••••${studentNumber.takeLast(4)}" else studentNumber
}

data class GradeDetail(
    val courseName: String, val courseCode: String = "", val className: String = "",
    val credits: String = "", val component: String = "", val score: String = "",
    val department: String = ""
)

data class GradePoint(
    val courseName: String, val courseCode: String = "", val className: String = "",
    val point: String = "", val weightedPoint: String = ""
)

data class GradeSnapshot(
    val accountId: String, val term: AcademicTerm, val fetchedAt: Long,
    val details: List<GradeDetail>, val points: List<GradePoint>
)

data class CourseGrade(val details: List<GradeDetail>, val point: GradePoint?)
data class MatchedGrades(val courses: List<CourseGrade>, val unmatchedPoints: List<GradePoint>)

data class SchoolTimetablePreview(
    val accountId: String, val term: AcademicTerm, val courses: List<Course>,
    val fetchedAt: Long, val sessionRevision: Long
)

class SchoolLoginRequired : Exception("学校登录已失效，请重新登录")
class SchoolDataException(message: String) : Exception(message)

/** Frozen import destination. Never resolve a different active timetable after a network call. */
data class SchoolImportTarget(
    val accountId: String, val academicYear: Int, val semester: Int,
    val profileId: Long, val semesterId: Long, val importedAt: Long,
    val profileName: String, val semesterName: String, val startDate: Long, val weekCount: Int
) {
    val term: AcademicTerm get() = AcademicTerm(academicYear, semester)
}
