// QluCampus 0.2.0, GPL-3.0. QLU authenticated integration is pending; reject unrecognized responses.
package com.dawncourse.core.data.campus

import com.dawncourse.core.domain.model.*
import com.dawncourse.core.domain.repository.*
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

object QluClassroomParser {
    fun options(html: String): ClassroomOptions {
        val doc = Jsoup.parse(html)
        if (doc.select("input[type=password]").isNotEmpty()) throw SchoolLoginRequired()
        if (!doc.text().contains("空闲教室") && !doc.text().contains("空教室")) throw SchoolDataException("尚未识别到学校空教室查询页面，可能未开放此功能；学校联调待验证")
        fun opts(id: String) = doc.select("select[id='$id'] option, select[name='$id'] option")
            .filter { it.attr("value").isNotBlank() }.map { ClassroomOption(it.attr("value"), it.text()) }.distinctBy { it.id }
        // 表单控件可能只有 name（例如复选框组），不能仅检查 DOM id。
        fun hasControl(id: String) = doc.select("input[id='$id'],select[id='$id'],input[name='$id'],select[name='$id']").isNotEmpty()
        val legacy = listOf("xnm", "xqm", "xqh_id", "jcd", "zcd", "xqj").all {
            doc.select("input[id='$it'],select[id='$it'],input[name='$it'],select[name='$it']").isNotEmpty()
        }
        // 2026-09 学校实页：周次/星期是带 value 的 th，节次行由 cxXqjc 动态填充。
        val tableForm = listOf("xnm", "xqm", "xqh_id", "dm_cx").all(::hasControl) &&
            doc.select("#selectTR_ZC th.selectTH[value]").isNotEmpty() &&
            doc.select("#selectTR_XQJ th.selectTH[value]").isNotEmpty() && doc.select("tr#selectTR_JC").isNotEmpty()
        val recognized = legacy || tableForm
        fun selected(id: String) = doc.selectFirst("select#$id option[selected]")?.attr("value")
            ?: doc.selectFirst("select#$id option")?.attr("value").orEmpty()
        val termParts = selected("dm_cx").split("-")
        val term = termParts.getOrNull(0)?.toIntOrNull()?.let { year ->
            when (termParts.getOrNull(1)) { "3" -> AcademicTerm(year, 1); "12" -> AcademicTerm(year, 2); else -> null }
        }
        return ClassroomOptions(QluSchoolApi.ROOT + QluClassroomRepository.PAGE, opts("xqh_id"), opts("lh"), recognized,
            if (recognized) "按所选课表首周日期换算教学周；查询结果以学校安排和实际占用为准。" else "当前学校页面尚未适配应用内筛选，并非没有空教室。请在应用内的学校官方页面选择条件查询。",
            selectedCampus = selected("xqh_id"), roomTypes = opts("cdlb_id"), term = term)
    }
    fun campusDetails(json: String): Pair<List<ClassroomOption>, List<Int>> {
        try {
            val root = JsonParser.parseString(json).asJsonObject
            val buildings = root.getAsJsonArray("lhList").map { it.asJsonObject }.map {
                ClassroomOption(it.get("JXLDM").asString, it.get("JXLMC").asString)
            }.also { require(it.all { b -> b.id.isNotBlank() && b.label.isNotBlank() }) }.distinctBy { it.id }
            val sections = root.getAsJsonArray("jcList").map { it.asJsonObject.get("JCMC").asString.toInt() }.distinct().sorted()
            require(sections.isNotEmpty() && sections.all { it in 1..16 })
            return buildings to sections
        } catch (_: Exception) { throw SchoolDataException("未能读取该校区的教学楼和节次，请重新加载学校选项") }
    }
    fun fields(query: ClassroomQuery, section: Int, page: Int): List<Pair<String, String>> {
        val week = week(query)
        require(section in query.startSection..query.endSection && page in 1..50)
        return listOf("xnm" to query.term.year.toString(), "xqm" to query.term.schoolTermCode,
            "xqh_id" to query.campus, "lh" to query.building, "cdlb_id" to query.roomType,
            "cdejlb_id" to "", "qszws" to "", "jszws" to "", "cdmc" to "", "cdjylx" to "",
            "zcd" to (1L shl (week - 1)).toString(), "xqj" to query.date.dayOfWeek.value.toString(),
            "jcd" to (1L shl (section - 1)).toString(), "jyfs" to "0",
            "queryModel.currentPage" to page.toString(), "queryModel.showCount" to "100",
            "queryModel.sortName" to "cdbh", "queryModel.sortOrder" to "asc")
    }
    fun page(json: String): Pair<List<AvailableClassroom>, Int> {
        val root = try { JsonParser.parseString(json).asJsonObject } catch (_: Exception) { throw SchoolDataException("学校返回的不是教室数据，原查询结果已保留") }
        val items = root.get("items")?.takeIf { it.isJsonArray }?.asJsonArray ?: throw SchoolDataException("学校教室数据格式已变化")
        val pages = root.get("totalPage")?.asString?.toIntOrNull() ?: throw SchoolDataException("学校未提供分页状态，无法确认完整教室列表")
        if (pages !in 0..50) throw SchoolDataException("学校教室数据分页超出支持范围")
        val rows = items.map { element ->
            val row = element.asJsonObject
            fun get(key: String) = row.get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            val id = get("cd_id"); val name = get("cdmc")
            if (id.isBlank() || name.isBlank()) throw SchoolDataException("教室记录缺少标识，无法判断连续节次是否空闲")
            AvailableClassroom(id, name, get("xqmc"), get("jxlmc"), get("zws"))
        }
        return rows to pages
    }
    fun week(query: ClassroomQuery): Int {
        require(query.firstMonday.dayOfWeek == java.time.DayOfWeek.MONDAY) { "首周日期必须是周一" }
        val days = ChronoUnit.DAYS.between(query.firstMonday, query.date)
        require(days >= 0 && days < 53 * 7) { "日期超出课表学期范围" }
        require(query.startSection in 1..16 && query.endSection in query.startSection..16) { "请选择连续有效节次" }
        return (days / 7).toInt() + 1
    }
}

@Singleton
class QluClassroomRepository @Inject constructor(private val api: QluSchoolApi, private val sessions: QluSessionRepository) : ClassroomRepository {
    companion object { const val PAGE = "cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155&layout=default" }
    override val officialUrl = QluSchoolApi.ROOT + PAGE
    override suspend fun options(accountId: String, campus: String, term: AcademicTerm?): ClassroomOptions {
        val ticket = sessions.ticket(accountId)
        val config = QluClassroomParser.options(api.page(PAGE, ticket))
        if (!config.nativeQuery) return config
        val selectedCampus = campus.ifBlank { config.selectedCampus }
        require(config.campuses.any { it.id == selectedCampus }) { "请选择学校提供的校区" }
        val selectedTerm = term ?: config.term ?: throw SchoolDataException("学校未提供学期，请先导入学校课表")
        val encodedCampus = java.net.URLEncoder.encode(selectedCampus, "UTF-8")
        val raw = api.page("cdjy/cdjy_cxXqjc.html?gnmkdm=N2155&xqh_id=$encodedCampus&xnm=${selectedTerm.year}&xqm=${selectedTerm.schoolTermCode}", ticket)
        val (buildings, sections) = QluClassroomParser.campusDetails(raw)
        return config.copy(selectedCampus = selectedCampus, buildings = buildings, sections = sections, term = selectedTerm)
    }
    override suspend fun query(accountId: String, query: ClassroomQuery): ClassroomResult {
        val ticket = sessions.ticket(accountId)
        val config = options(accountId, query.campus, query.term)
        if (!config.nativeQuery) throw SchoolDataException(config.message)
        require(config.campuses.any { it.id == query.campus }) { "请选择学校提供的校区" }
        require(query.building.isBlank() || config.buildings.any { it.id == query.building }) { "请选择学校提供的教学楼" }
        require(query.roomType.isBlank() || config.roomTypes.any { it.id == query.roomType }) { "请选择学校提供的场地类别" }
        require((query.startSection..query.endSection).all { it in config.sections }) { "所选节次不在该校区的上课节次内" }
        QluClassroomParser.week(query)
        var intersection: Map<String, AvailableClassroom>? = null
        for (section in query.startSection..query.endSection) {
            val sectionRooms = linkedMapOf<String, AvailableClassroom>()
            var page = 1
            do {
                val fields = QluClassroomParser.fields(query, section, page)
                val raw = api.post("cdjy/cdjy_cxKxcdlb.html?doType=query&gnmkdm=N2155", PAGE, fields, ticket).toString(Charsets.UTF_8)
                val (rows, pages) = QluClassroomParser.page(raw)
                rows.forEach { sectionRooms[it.id] = it }
                page++
            } while (page <= pages)
            intersection = intersection?.filterKeys { it in sectionRooms } ?: sectionRooms
        }
        sessions.check(ticket)
        return ClassroomResult(query, intersection.orEmpty().values.sortedWith(compareBy({ it.campus }, { it.building }, { it.name })), System.currentTimeMillis())
    }
}
