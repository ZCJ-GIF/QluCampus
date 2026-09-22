// QluCampus 0.2.1, GPL-3.0. Compact weekly view prioritizes the full classroom address.
package com.dawncourse.feature.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.dawncourse.core.domain.model.Course
import com.dawncourse.core.ui.components.glassSurface
import com.dawncourse.core.ui.theme.LocalAppSettings
import com.dawncourse.core.ui.util.CourseColorUtils
import com.dawncourse.core.ui.util.LocalCoursePalette

internal fun compactLocation(course: Course) = course.location.ifBlank { "地点未提供" }
internal fun compactStatus(course: Course, current: Boolean) = listOfNotNull(
    "非本周".takeUnless { current }, "已调课".takeIf { course.isModified }
).joinToString(" · ")

internal fun compactNameStyle(base: TextStyle, size: Float) = base.copy(fontSize = size.sp, lineHeight = (size + 2).sp, fontWeight = FontWeight.Medium)
internal fun compactInfoStyle(base: TextStyle, size: Float) = base.copy(fontSize = (size - 1).sp, lineHeight = (size + 1).sp)
internal fun compactTextHeight(measurer: TextMeasurer, text: String, style: TextStyle, width: Int, maxLines: Int = Int.MAX_VALUE): Int =
    if (text.isBlank()) 0 else measurer.measure(text, style, constraints = Constraints(maxWidth = width.coerceAtLeast(1)), maxLines = maxLines, overflow = TextOverflow.Ellipsis).size.height

@Composable
internal fun CompactCourseCard(course: Course, current: Boolean, onClick: () -> Unit) {
    val settings = LocalAppSettings.current
    val background = CourseColorUtils.getTimetableColor(course, current, settings.campusAppearance.highContrast, LocalCoursePalette.current)
    val foreground = CourseColorUtils.getBestContentColor(background)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val nameBase = MaterialTheme.typography.bodyMedium
    val infoBase = MaterialTheme.typography.bodySmall
    val location = compactLocation(course)
    val status = compactStatus(course, current)
    val shape = RoundedCornerShape(settings.cardCornerRadius.coerceAtMost(6).dp)
    BoxWithConstraints(Modifier.fillMaxSize().padding(1.dp)
        .glassSurface(shape, background, forceOpaque = settings.campusAppearance.highContrast || !current)
        .clickable(onClick = onClick)) {
        val width = with(density) { (maxWidth - 6.dp).toPx().toInt().coerceAtLeast(1) }
        val height = with(density) { (maxHeight - 8.dp).toPx() }
        val gap = with(density) { 2.dp.toPx() }
        val fitted = remember(course, current, width, height, density.fontScale, nameBase, infoBase) {
            fun primaryHeight(size: Float): Float {
                val n = compactNameStyle(nameBase, size)
                val i = compactInfoStyle(infoBase, size)
                return compactTextHeight(measurer, course.name, n, width, 4) + compactTextHeight(measurer, location, i, width) +
                    compactTextHeight(measurer, status, i, width) + gap * if (status.isBlank()) 1 else 2
            }
            listOf(13f, 12.5f, 12f, 11.5f, 11f, 10.5f, 10f).firstOrNull { primaryHeight(it) <= height } ?: 10f
        }
        val nameStyle = compactNameStyle(nameBase, fitted)
        val infoStyle = compactInfoStyle(infoBase, fitted)
        Column(Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(course.name, style = nameStyle, color = foreground, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text(location, style = infoStyle, color = foreground)
            if (status.isNotBlank()) Text(status, style = infoStyle, color = foreground)
        }
    }
}
