// QluCampus 0.2.0, GPL-3.0.
package com.dawncourse.feature.timetable

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.dawncourse.core.domain.model.Course
import com.dawncourse.core.ui.theme.LocalAppSettings
import kotlin.math.ceil

val LocalCourseRowHeight = staticCompositionLocalOf { 64.dp }
val LocalTimeColumnWidth = staticCompositionLocalOf { 32.dp }
val LocalTimetableSectionCount = staticCompositionLocalOf { 12 }
val LocalWeekHeaderHeight = staticCompositionLocalOf { 56.dp }

@Composable
fun CompleteTimetableLayout(courses: List<Course>, currentWeek: Int, bottomOverlayPadding: Dp = 0.dp, content: @Composable () -> Unit) {
    val settings = LocalAppSettings.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 256)
    val nameStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
    val infoStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp)
    val compact = settings.campusAppearance.fitTimetableToScreen
    val timeStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    val timeWidth = with(density) { maxOf(32.dp, measurer.measure("00:00", timeStyle).size.width.toDp() + 8.dp) }
    val maximumSections = maxOf(settings.maxDailySections, courses.maxOfOrNull { (it.startSection + it.duration - 1).coerceIn(1, 24) } ?: 1)
    val visibleItems = remember(courses, currentWeek, settings.showWeekend, settings.hideNonThisWeek, maximumSections) {
        TimetableLayoutEngine.calculateLayoutItems(courses, currentWeek, maximumSections, settings.hideNonThisWeek, settings.showWeekend)
    }
    // 默认展示至少十节；有晚课时自动包含晚课，避免为未使用的节次压缩地址。
    val sections = if (compact) maxOf(minOf(10, maximumSections), visibleItems.maxOfOrNull { it.safeEndSection } ?: 1) else maximumSections
    val headerHeight = if (compact) (52 * density.fontScale.coerceAtLeast(1f)).dp else (56 * density.fontScale.coerceAtLeast(1f)).dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = remember(courses, currentWeek, compact, sections, settings.showWeekend, settings.hideNonThisWeek, settings.courseItemHeightDp, settings.showCourseIcons, maxWidth, maxHeight, headerHeight, timeWidth, bottomOverlayPadding, density.fontScale, nameStyle, infoStyle) {
            with(density) {
                val dayWidth = (maxWidth - timeWidth).toPx() / if (settings.showWeekend) 7 else 5
                if (compact) {
                    // 首屏保留十节的周表比例；第十一节以后的课程向下滚动查看。
                    // Leave the tenth section label above the bar while allowing the card's lower edge behind it.
                    val viewportRow = ((maxHeight - headerHeight - bottomOverlayPadding / 2).toPx() / minOf(sections, 10)).coerceAtLeast(1f)
                    // 用户放大字号时允许滚动；默认不因单个长标题把第九、十节挤出首屏。
                    return@with kotlin.math.floor(viewportRow * density.fontScale.coerceAtLeast(1f)).toDp()
                }
                var required = settings.courseItemHeightDp.dp.toPx()
                val weeks = maxOf(1, courses.maxOfOrNull { it.endWeek } ?: 1).coerceAtMost(53)
                for (week in 1..weeks) {
                    TimetableLayoutEngine.calculateLayoutItems(courses, week, maximumSections, settings.hideNonThisWeek, settings.showWeekend).forEach { item ->
                        val width = (dayWidth / item.laneCount - 12.dp.toPx()).toInt().coerceAtLeast(1)
                        fun h(text: String, name: Boolean = false): Int = if (text.isBlank()) 0 else measurer.measure(text, if (name) nameStyle else infoStyle, constraints = Constraints(maxWidth = width)).size.height
                        val c = item.course
                        val info = listOf(c.location, c.teacher, if (!item.isCurrentWeek) "非本周" else "", if (c.isModified) "已调课" else "").filter(String::isNotBlank)
                        val pixels = h(c.name, true) + info.sumOf { h(it) } + (20 + info.size * 2 + if (settings.showCourseIcons) 18 else 0).dp.toPx()
                        val span = (item.safeEndSection - item.safeStartSection + 1).coerceAtLeast(1)
                        required = maxOf(required, pixels / span)
                    }
                }
                ceil(required / density.density).toInt().dp
            }
        }
        val palette = remember(courses) { com.dawncourse.core.ui.util.CourseColorUtils.highContrastPalette(courses) }
        CompositionLocalProvider(LocalCourseRowHeight provides height, LocalTimeColumnWidth provides timeWidth,
            LocalTimetableSectionCount provides sections, LocalWeekHeaderHeight provides headerHeight,
            com.dawncourse.core.ui.util.LocalCoursePalette provides palette) { content() }
    }
}
