// QluCampus 0.2.0, GPL-3.0.
package com.dawncourse.core.domain.usecase

import com.dawncourse.core.domain.model.*
import java.math.BigDecimal
import java.math.RoundingMode

data class GpaCourseKey(val account: String, val term: AcademicTerm, val code: String, val className: String, val name: String)
data class GpaCourse(val key: GpaCourseKey, val credits: BigDecimal?, val point: BigDecimal?, val issue: String?)
data class GpaResult(val count: Int, val credits: BigDecimal, val weighted: BigDecimal, val gpa: BigDecimal?)

/** Uses published points only. Repeated grade components never become repeated courses. */
object CalculateSelectedGpa {
    fun courses(snapshots: List<GradeSnapshot>): List<GpaCourse> = snapshots.flatMap { snapshot ->
        GradeMatcher.match(snapshot).courses.map { course ->
            val first = course.details.first()
            val credits = course.details.map { it.credits.trim().toBigDecimalOrNull()?.stripTrailingZeros() }.distinct()
            val credit = credits.singleOrNull()
            val point = course.point?.point?.trim()?.toBigDecimalOrNull()
            val issue = when {
                credits.size != 1 -> "学分记录不一致"
                credit == null -> "学校未提供有效学分"
                credit.signum() <= 0 -> "学分必须大于 0"
                course.point == null -> "绩点缺失或无法唯一匹配"
                point == null || point.signum() < 0 -> "学校未提供有效绩点"
                else -> null
            }
            GpaCourse(GpaCourseKey(snapshot.accountId, snapshot.term, first.courseCode, first.className, first.courseName), credit, point, issue)
        }
    }.distinctBy { it.key }

    fun calculate(courses: List<GpaCourse>, selected: Set<GpaCourseKey>): GpaResult {
        require(selected.map { it.account }.distinct().size <= 1) { "不能混合不同账号的成绩" }
        val valid = courses.distinctBy { it.key }.filter { it.key in selected && it.issue == null && it.credits != null && it.credits.signum() > 0 && it.point != null && it.point.signum() >= 0 }
        val credits = valid.fold(BigDecimal.ZERO) { total, c -> total + c.credits!! }
        val weighted = valid.fold(BigDecimal.ZERO) { total, c -> total + c.credits!! * c.point!! }
        return GpaResult(valid.size, credits, weighted, if (credits.signum() == 0) null else weighted.divide(credits, 4, RoundingMode.HALF_UP))
    }
}
