package com.dawncourse.core.domain.usecase

import com.dawncourse.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class SelectedGpaTest {
    private fun snapshot(term: Int = 1, account: String = "a", details: List<GradeDetail> = listOf(
        GradeDetail("数学", "m", credits = "3.0", component = "平时", score = "90"),
        GradeDetail("数学", "m", credits = "3.0", component = "期末", score = "80"),
        GradeDetail("英语", "e", credits = "2.0", component = "总评", score = "50")
    ), points: List<GradePoint> = listOf(GradePoint("数学", "m", point = "4.0"), GradePoint("英语", "e", point = "0"))) =
        GradeSnapshot(account, AcademicTerm(2025, term), 1, details, points)

    @Test fun weightedAverageCountsComponentsOnceAndIncludesZeroPoint() {
        val rows = CalculateSelectedGpa.courses(listOf(snapshot()))
        val result = CalculateSelectedGpa.calculate(rows, rows.map { it.key }.toSet())
        assertEquals(2, result.count); assertEquals("2.4000", result.gpa?.toPlainString())
        assertEquals(0, result.credits.compareTo("5".toBigDecimal()))
    }
    @Test fun selectionChangesDenominator() {
        val rows = CalculateSelectedGpa.courses(listOf(snapshot()))
        assertEquals("4.0000", CalculateSelectedGpa.calculate(rows, setOf(rows.first().key)).gpa?.toPlainString())
        assertNull(CalculateSelectedGpa.calculate(rows, emptySet()).gpa)
    }
    @Test fun ambiguousAndMissingPointsAreNotZero() {
        val rows = CalculateSelectedGpa.courses(listOf(snapshot(points = listOf(GradePoint("数学", "m", point = "3"), GradePoint("数学", "m", point = "4")))))
        assertTrue(rows.all { it.issue != null }); assertNull(CalculateSelectedGpa.calculate(rows, rows.map { it.key }.toSet()).gpa)
    }
    @Test fun zeroNegativeInvalidAndConflictingCreditsExcluded() {
        for (value in listOf("", "无", "0", "-1")) {
            val rows = CalculateSelectedGpa.courses(listOf(snapshot(details = listOf(GradeDetail("数学", "m", credits = value)))))
            assertNotNull(rows.single().issue)
        }
        val rows = CalculateSelectedGpa.courses(listOf(snapshot(details = listOf(GradeDetail("数学", "m", credits = "2"), GradeDetail("数学", "m", credits = "3")))))
        assertNotNull(rows.single().issue)
    }
    @Test fun sortOrderAndTermDoNotMixCourses() {
        val first = snapshot()
        val second = snapshot(2).copy(points = snapshot().points.reversed())
        val rows = CalculateSelectedGpa.courses(listOf(first, second))
        assertEquals(4, rows.size); assertEquals("2.4000", CalculateSelectedGpa.calculate(rows, rows.map { it.key }.toSet()).gpa?.toPlainString())
    }
    @Test(expected = IllegalArgumentException::class) fun differentAccountsCannotMix() {
        val rows = CalculateSelectedGpa.courses(listOf(snapshot(), snapshot(account = "b")))
        CalculateSelectedGpa.calculate(rows, rows.map { it.key }.toSet())
    }
    @Test fun redactionPreservesTotalCreditAndPointWithoutDetailLeak() {
        val redacted = GradeVisibility.summary(snapshot())
        assertTrue(redacted.details.all { it.component == "总评" })
        assertEquals("", redacted.details.first().score)
        assertEquals("50", redacted.details.last().score)
        assertEquals(snapshot().points, redacted.points)
    }
}
