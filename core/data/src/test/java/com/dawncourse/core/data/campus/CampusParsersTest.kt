// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.data.campus

import com.dawncourse.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream

class CampusParsersTest {
    @Test fun excelRoundTripPreservesChineseDecimalsAndFormulaLikeText() {
        val rows = listOf(listOf("课程名称", "成绩", "成绩分项"), listOf("有机化学 & 实验", "89.50", "平时"), listOf("=1+1", "未发布", "总评"))
        val bytes = XlsxTableCodec.write(listOf("成绩明细" to rows, "绩点" to listOf(listOf("课程名称", "绩点"))))
        assertEquals(rows, XlsxTableCodec.read(bytes))
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val e = zip.nextEntry ?: break; names += e.name } }
        assertTrue(names.contains("xl/worksheets/sheet2.xml"))
        assertEquals("89.50", QluGradeParser.details(XlsxTableCodec.read(bytes)).first().score)
        java.io.File("build/reports/qlu-sample.xlsx").apply { parentFile.mkdirs(); writeBytes(bytes) }
    }
    @Test(expected = SchoolLoginRequired::class) fun loginHtmlIsNotAnEmptyWorkbook() {
        XlsxTableCodec.read("<html>请登录 login</html>".toByteArray())
    }
    @Test(expected = SchoolDataException::class) fun corruptFileFails() { XlsxTableCodec.read(byteArrayOf(1,2,3)) }
    @Test(expected = SchoolDataException::class) fun changedHeadersFail() { QluGradeParser.details(listOf(listOf("new-field", "score"))) }
    @Test fun headersWithNoRowsAreAValidUnpublishedSemester() {
        assertTrue(QluGradeParser.details(listOf(listOf("课程名称", "成绩", "成绩分项"))).isEmpty())
    }
    @Test fun missingGpaIsNotConvertedToZero() {
        assertEquals("", QluGradeParser.points(listOf(listOf("课程名称", "绩点", "学分绩点"), listOf("化学", "", ""))).single().point)
    }
    @Test fun disjointWeeksAndParityArePreservedWithoutDuplicates() {
        val row = """{"kcmc":"有机化学","xqj":"2","jc":"1-2节","zcd":"1-8周(单),12周,16-18周(双)","xm":"测试教师","cdmc":"A101"}"""
        val courses = QluTimetableParser.parse("{\"kbList\":[$row,$row]}")
        assertEquals(setOf(1,3,5,7,12,16,18), courses.map { it.startWeek }.toSet())
        assertEquals(7, courses.size)
        assertTrue(courses.all { it.dayOfWeek == 2 && it.duration == 2 })
    }
    @Test(expected = SchoolDataException::class) fun malformedWeeksRejectWholeImport() {
        QluTimetableParser.parse("""{"kbList":[{"kcmc":"化学","xqj":"2","jc":"1-2","zcd":"待定"}]}""")
    }
    @Test(expected = SchoolDataException::class) fun arbitraryJsonIsNotEmptyTimetable() { QluTimetableParser.parse("{}") }
    @Test fun explicitEmptyTimetableIsRecognized() { assertTrue(QluTimetableParser.parse("{\"kbList\":[]}").isEmpty()) }
    @Test fun existingExportFixtureCanBeReadWithoutPrintingPersonalData() {
        // Generated fixture only: no private school records are committed.
        val rows = listOf(listOf("课程名称", "绩点", "绩点", "学分绩点"), listOf("示例课程", "3.5", "3.5", "7.0"))
        assertEquals("3.5", QluGradeParser.points(rows).single().point)
    }
}
