// QluCampus modification, 2026-09-21: school timetable and grades fork; GPL-3.0, upstream attribution retained.
package com.dawncourse.core.data.campus

import com.dawncourse.core.domain.model.*

/** 修改学校导出字段时只需维护这里；没有已确认结构时拒绝覆盖缓存。 */
object QluGradeParser {
    fun summary(rows: List<List<String>>): List<GradeDetail> {
        val t = Table(rows, setOf("课程名称", "成绩", "学分"))
        return t.data.map { r ->
            val name = t.get(r, "课程名称")
            if (name.isBlank()) throw SchoolDataException("学校总评记录缺少课程名称")
            GradeDetail(name, t.get(r, "课程代码"), t.get(r, "教学班"), t.get(r, "学分"), "总评", t.get(r, "成绩"))
        }
    }
    private class Table(rows: List<List<String>>, required: Set<String>) {
        private val headerIndex = rows.indexOfFirst { it.containsAll(required) }
        private val header = rows.getOrNull(headerIndex) ?: throw SchoolDataException("学校成绩表字段已变化，请更新适配")
        val data = rows.drop(headerIndex + 1)
        fun get(row: List<String>, name: String): String = row.getOrNull(header.indexOf(name)).orEmpty()
    }

    fun details(rows: List<List<String>>): List<GradeDetail> {
        val t = Table(rows, setOf("课程名称", "成绩", "成绩分项"))
        return t.data.map { r ->
            val name = t.get(r, "课程名称")
            if (name.isBlank()) throw SchoolDataException("成绩记录缺少课程名称，原有数据已保留")
            GradeDetail(name, t.get(r, "课程代码"), t.get(r, "教学班"), t.get(r, "学分"),
                t.get(r, "成绩分项"), t.get(r, "成绩"), t.get(r, "开课学院"))
        }
    }

    fun points(rows: List<List<String>>): List<GradePoint> {
        val t = Table(rows, setOf("课程名称", "绩点"))
        return t.data.map { r ->
            val name = t.get(r, "课程名称")
            if (name.isBlank()) throw SchoolDataException("绩点记录缺少课程名称，原有数据已保留")
            GradePoint(name, t.get(r, "课程代码"), t.get(r, "教学班"), t.get(r, "绩点"), t.get(r, "学分绩点"))
        }
    }

    fun export(snapshot: GradeSnapshot): ByteArray = XlsxTableCodec.write(listOf(
        "成绩明细" to (listOf(listOf("课程名称", "学年", "学期", "课程代码", "教学班", "开课学院", "学分", "成绩分项", "成绩")) +
            snapshot.details.map { listOf(it.courseName, snapshot.term.year.toString(), snapshot.term.semester.toString(), it.courseCode, it.className, it.department, it.credits, it.component, it.score) }),
        "绩点" to (listOf(listOf("课程名称", "课程代码", "教学班", "绩点", "学分绩点")) +
            snapshot.points.map { listOf(it.courseName, it.courseCode, it.className, it.point, it.weightedPoint) })
    ))
}
