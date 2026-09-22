// QluCampus 0.2.0, GPL-3.0.
package com.dawncourse.core.domain.usecase
import com.dawncourse.core.domain.model.*

object GradeVisibility {
    fun summary(snapshot: GradeSnapshot): GradeSnapshot = snapshot.copy(details = snapshot.details
        .groupBy { Triple(it.courseCode, it.className, it.courseName) }.values.map { parts ->
            val total = parts.singleOrNull { it.component.contains("总评") || it.component == "成绩" }
            val credits = parts.map { it.credits.trim().toBigDecimalOrNull()?.stripTrailingZeros() }.distinct()
            parts.first().copy(component = "总评", score = total?.score.orEmpty(),
                credits = if (credits.size == 1 && credits.single() != null) parts.first().credits else "")
        })
}
