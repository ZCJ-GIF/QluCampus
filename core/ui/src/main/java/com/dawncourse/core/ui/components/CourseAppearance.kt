// QluCampus 0.2.8, GPL-3.0. One readable course surface policy for both layouts.
package com.dawncourse.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.dawncourse.core.domain.model.CampusStyle
import com.dawncourse.core.domain.model.Course
import com.dawncourse.core.ui.theme.LocalAppSettings
import com.dawncourse.core.ui.theme.LocalWallpaperSeed
import com.dawncourse.core.ui.theme.campusStyleColors
import com.dawncourse.core.ui.util.CourseColorUtils
import com.dawncourse.core.ui.util.LocalCoursePalette
import com.dawncourse.core.ui.util.ReadableColors

data class CourseSurfaceColors(val background: Color, val foreground: Color, val opacity: Float, val outlined: Boolean)

fun readableCourseSurface(base: Color, wallpaper: Color?, adaptive: Boolean, requestedOpacity: Float): CourseSurfaceColors {
    val color = if (adaptive && wallpaper != null) {
        val harmonized = ReadableColors.mix(base, wallpaper, .08f)
        ReadableColors.mix(harmonized, if (ReadableColors.luminance(wallpaper) > .55) Color(0xFF263238) else Color.White, .12f)
    } else base
    val foreground = ReadableColors.bestText(color)
    return CourseSurfaceColors(color, foreground,
        if (adaptive) ReadableColors.safeOpacity(color, foreground, requestedOpacity) else requestedOpacity,
        adaptive)
}

@Composable
fun rememberCourseSurface(course: Course, current: Boolean): CourseSurfaceColors {
    val settings = LocalAppSettings.current
    val appearance = settings.campusAppearance
    val seed = LocalWallpaperSeed.current
    val palette = LocalCoursePalette.current
    val surface = MaterialTheme.colorScheme.surface
    return remember(course, current, appearance, seed, palette, surface) {
        val preserve = appearance.highContrast || !current
        var base = CourseColorUtils.getTimetableColor(course, current, appearance.highContrast, palette)
        if (!preserve && course.color.isBlank() && appearance.style != CampusStyle.CLASSIC) {
            val colors = campusStyleColors(appearance.style).courses
            base = colors[Math.floorMod(CourseColorUtils.key(course).hashCode(), colors.size)]
            if (ReadableColors.luminance(surface) < .2) base = ReadableColors.mix(base, surface, .55f)
        }
        if (preserve) CourseSurfaceColors(base, ReadableColors.bestText(base), 1f, false)
        else readableCourseSurface(base, seed, appearance.adaptiveCourseColors, appearance.glassOpacity)
    }
}
