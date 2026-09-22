// QluCampus modification, 2026-09-21. GPL-3.0.
package com.dawncourse.core.domain.model

/** 默认五大节：每小节 45 分钟，白天中间休息 5 分钟，晚课连上不休息。 */
object QluSectionTimes {
    val defaults: List<SectionTime> = listOf(
        SectionTime("08:30", "09:15"), SectionTime("09:20", "10:05"),
        SectionTime("10:20", "11:05"), SectionTime("11:10", "11:55"),
        SectionTime("14:00", "14:45"), SectionTime("14:50", "15:35"),
        SectionTime("15:50", "16:35"), SectionTime("16:40", "17:25"),
        SectionTime("18:25", "19:10"), SectionTime("19:10", "19:55")
    )

    // 用户未给出第十一节之后的作息，不自动编造时间。
    fun at(section: Int): SectionTime = defaults.getOrNull(section - 1) ?: SectionTime("", "")
}
