// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.domain.usecase

import com.dawncourse.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class GradeMatcherTest {
    private fun snapshot(details: List<GradeDetail>, points: List<GradePoint>) = GradeSnapshot("test-account", AcademicTerm(2025, 2), 1, details, points)
    @Test fun reversedGpaOrderDoesNotChangeAssociation() {
        val s = snapshot(listOf(GradeDetail("有机化学", "C1", component = "平时", score = "92"),
            GradeDetail("有机化学", "C1", component = "总评", score = "88"), GradeDetail("数学", "M1", score = "60")),
            listOf(GradePoint("数学", point = "1.0"), GradePoint("有机化学", point = "3.8")))
        val result = GradeMatcher.match(s)
        assertEquals("3.8", result.courses.first().point?.point)
        assertEquals(2, result.courses.first().details.size)
        assertEquals("1.0", result.courses.last().point?.point)
        assertTrue(result.unmatchedPoints.isEmpty())
    }
    @Test fun ambiguousSameNameIsNotMatched() {
        val s = snapshot(listOf(GradeDetail("英语", "E1", "A"), GradeDetail("英语", "E2", "B")), listOf(GradePoint("英语", point = "4")))
        val result = GradeMatcher.match(s)
        assertTrue(result.courses.all { it.point == null })
        assertEquals(1, result.unmatchedPoints.size)
    }
    @Test fun distinctCodesOverrideMatchingNames() {
        val result = GradeMatcher.match(snapshot(listOf(GradeDetail("英语", "E1")), listOf(GradePoint("英语", "E2", point = "4"))))
        assertNull(result.courses.single().point)
    }
    @Test fun duplicatePointRecordsStaySeparate() {
        val point = GradePoint("英语", point = "4")
        val result = GradeMatcher.match(snapshot(listOf(GradeDetail("英语")), listOf(point, point)))
        assertNull(result.courses.single().point)
        assertEquals(2, result.unmatchedPoints.size)
    }
    @Test fun academicTermsUseSchoolCodes() {
        assertEquals("3", AcademicTerm(2025, 1).schoolTermCode)
        assertEquals("12", AcademicTerm(2025, 2).schoolTermCode)
    }
}
