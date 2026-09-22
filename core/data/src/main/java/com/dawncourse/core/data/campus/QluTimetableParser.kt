// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.data.campus

import com.dawncourse.core.domain.model.*
import com.google.gson.JsonParser

object QluTimetableParser {
    fun parse(raw: String): List<Course> {
        if (raw.contains("登录") && !raw.trimStart().startsWith("{")) throw SchoolLoginRequired()
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            ?: throw SchoolDataException("学校课表格式已变化，请使用网页导入或更新适配")
        val list = root.get("kbList")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw SchoolDataException("未取得课表数据，请确认学校登录和学期")
        if (list.size() > 2000) throw SchoolDataException("课表记录过多")
        return list.flatMap { element ->
            val item = element.asJsonObject
            fun value(key: String) = item.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val name = value("kcmc")
            val day = value("xqj").toIntOrNull()
            val sections = numbers(value("jc").ifBlank { value("jcs") }, 30, "节次")
            val weeks = numbers(value("zcd"), 60, "周次")
            if (name.isBlank() || day !in 1..7 || sections.isEmpty() || weeks.isEmpty()) throw SchoolDataException("部分课程时间无法识别，未导入不完整课表")
            // 每个实际周独立表示，准确保留离散周与单双周；显示层只显示当前周。
            val runs = sections.sorted().fold(mutableListOf<MutableList<Int>>()) { acc, n ->
                if (acc.lastOrNull()?.last()?.plus(1) == n) acc.last() += n else acc += mutableListOf(n)
                acc
            }
            weeks.sorted().flatMap { week -> runs.map { run -> Course(name = name, teacher = value("xm"),
                location = value("cdmc"), dayOfWeek = day!!, startSection = run.first(), duration = run.size,
                startWeek = week, endWeek = week) } }
        }.distinct()
    }

    internal fun numbers(raw: String, maximum: Int, label: String): Set<Int> {
        val normalized = raw.replace('，', ',').replace('、', ',').replace('－', '-').replace('—', '-').replace('～', '-').replace('~', '-')
        if (normalized.isBlank()) throw SchoolDataException("学校未提供$label")
        return normalized.split(',').flatMap { part ->
            val odd = part.contains('单'); val even = part.contains('双')
            val text = part.replace(Regex("[周节\\s()（）单双]"), "")
            if (!text.matches(Regex("\\d+(?:-\\d+)?")) || (odd && even)) throw SchoolDataException("无法识别$label：$raw")
            val range = text.split('-').map(String::toInt)
            val start = range.first(); val end = range.last()
            if (start !in 1..maximum || end !in start..maximum) throw SchoolDataException("$label 超出有效范围")
            (start..end).filter { (!odd || it % 2 == 1) && (!even || it % 2 == 0) }
        }.toSet()
    }
}
