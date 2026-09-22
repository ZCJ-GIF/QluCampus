package com.dawncourse.core.data.campus

import com.dawncourse.core.domain.model.*
import com.dawncourse.core.domain.repository.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class ClassroomParserTest {
    @Test fun dateMapsWeekAndDoesNotUseCurrentDeviceWeek() {
        val first = LocalDate.of(2026, 9, 7)
        assertEquals(3, QluClassroomParser.week(ClassroomQuery(AcademicTerm(2026, 1), first, first.plusDays(17), 1, 2, "", "")))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidSectionRangeRejected() {
        val first = LocalDate.of(2026, 9, 7)
        QluClassroomParser.week(ClassroomQuery(AcademicTerm(2026, 1), first, first, 3, 2, "", ""))
    }
    @Test fun parserRetainsChineseAndCapacity() {
        val (rows, pages) = QluClassroomParser.page("""{"items":[{"cd_id":"1","cdmc":"文科楼101","xqmc":"校区甲","jxlmc":"文科楼","zws":"60"}],"totalPage":2}""")
        assertEquals(2, pages); assertEquals("文科楼101", rows.single().name); assertEquals("60", rows.single().capacity)
    }
    @Test fun validEmptyPageIsDifferentFromError() { assertTrue(QluClassroomParser.page("""{"items":[],"totalPage":0}""").first.isEmpty()) }
    @Test(expected = SchoolDataException::class) fun htmlIsNeverAnEmptyClassroomList() { QluClassroomParser.page("<html>请登录</html>") }
    @Test(expected = SchoolDataException::class) fun missingIdentityRejected() { QluClassroomParser.page("""{"items":[{"cdmc":"101"}],"totalPage":1}""") }
    @Test(expected = SchoolDataException::class) fun missingPaginationRejected() { QluClassroomParser.page("""{"items":[]}""") }
    @Test(expected = SchoolLoginRequired::class) fun loginPageRejected() { QluClassroomParser.options("<input type='password'>") }
    @Test fun unrecognizedOfficialFormOffersWebOnly() {
        val options = QluClassroomParser.options("<h1>空教室查询</h1><select id='xqh_id'><option value='north'>北校区</option></select>")
        assertFalse(options.nativeQuery); assertEquals("北校区", options.campuses.single().label)
    }
    @Test fun namedFormControlsAreRecognizedWithoutDomIds() {
        val fields = listOf("xnm", "xqm", "jcd", "zcd", "xqj").joinToString("") { "<input name='$it'>" }
        val options = QluClassroomParser.options("<h1>空闲教室查询</h1>$fields<select name='xqh_id'><option value='north'>北校区</option></select>")
        assertTrue(options.nativeQuery)
        assertEquals("北校区", options.campuses.single().label)
    }
    @Test fun DecorativeElementsDoNotCountAsQueryControls() {
        val fields = listOf("xnm", "xqm", "xqh_id", "jcd", "zcd", "xqj").joinToString("") { "<div id='$it'></div>" }
        assertFalse(QluClassroomParser.options("<h1>空教室查询</h1>$fields").nativeQuery)
    }
    // Reproduces the observed QLU form structure, with synthetic room labels and no account data.
    @Test fun schoolSelectableTablesWorkBeforeJavascriptFillsSections() {
        val html = """<h1>查询空闲教室</h1><input id="xnm"><input id="xqm">
            <select id="dm_cx"><option value="2025-12">旧学期</option><option value="2026-3" selected>2026-2027-1</option></select>
            <select id="xqh_id"><option value="4" selected>示例校区</option></select>
            <select id="cdlb_id"><option value="05">多媒体教室</option></select>
            <table><thead id="selectTR_ZC"><tr><th class="selectTH" value="3">3</th></tr></thead></table>
            <table><thead id="selectTR_XQJ"><tr><th class="selectTH" value="2">2</th></tr></thead></table>
            <table><tr id="selectTR_JC"></tr></table>"""
        val result = QluClassroomParser.options(html)
        assertTrue(result.nativeQuery)
        assertEquals("4", result.selectedCampus)
        assertEquals(AcademicTerm(2026, 1), result.term)
        assertEquals("05", result.roomTypes.single().id)
    }
    @Test fun dependentCampusBuildingsAndSectionsAreParsed() {
        val (buildings, sections) = QluClassroomParser.campusDetails("""{"lhList":[{"JXLDM":"B1","JXLMC":"示例北楼"}],"jcList":[{"JCMC":"2"},{"JCMC":"1"},{"JCMC":"11"}]}""")
        assertEquals(ClassroomOption("B1", "示例北楼"), buildings.single())
        assertEquals(listOf(1, 2, 11), sections)
    }
    @Test(expected = SchoolDataException::class) fun emptyCampusSectionsMustNotEnableQuery() {
        QluClassroomParser.campusDetails("""{"lhList":[],"jcList":[]}""")
    }
    @Test(expected = SchoolDataException::class) fun campusLoginHtmlMustNotBecomeEmptyOptions() {
        QluClassroomParser.campusDetails("<html>登录</html>")
    }
    @Test fun queryUsesSchoolBitmasksAndExplicitCategoryAndSort() {
        val first = LocalDate.of(2026, 9, 7)
        val query = ClassroomQuery(AcademicTerm(2026, 1), first, LocalDate.of(2026, 9, 22), 1, 2, "4", "B1", "05")
        val fields = QluClassroomParser.fields(query, 2, 3).toMap()
        assertEquals("4", fields["zcd"])
        assertEquals("2", fields["jcd"])
        assertEquals("2", fields["xqj"])
        assertEquals("3", fields["xqm"])
        assertEquals("05", fields["cdlb_id"])
        assertEquals("B1", fields["lh"])
        assertEquals("cdbh", fields["queryModel.sortName"])
        assertEquals("3", fields["queryModel.currentPage"])
        assertEquals("12", QluClassroomParser.fields(query.copy(term = AcademicTerm(2026, 2)), 1, 1).toMap()["xqm"])
    }
}
