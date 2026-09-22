package com.dawncourse.core.ui.util

import androidx.compose.ui.graphics.Color
import com.dawncourse.core.domain.model.CampusStyle
import com.dawncourse.core.domain.model.CampusAppearance
import com.dawncourse.core.ui.components.readableCourseSurface
import com.dawncourse.core.ui.components.wallpaperSurfacePolicy
import com.dawncourse.core.ui.components.WallpaperArea
import com.dawncourse.core.ui.theme.campusStyleColors
import com.dawncourse.core.ui.theme.softColorScheme
import org.junit.Assert.*
import org.junit.Test

class ReadableColorsTest {
    @Test fun barsKeepPhotoVisibleIndependentlyOfCourseGlassAndContrast() {
        val value = CampusAppearance(highContrast = true, glassEnabled = true, glassOpacity = 1f)
        listOf(WallpaperArea.HEADER, WallpaperArea.NAVIGATION, WallpaperArea.PANEL).forEach { area ->
            val policy = wallpaperSurfacePolicy(value, area, 1f)
            assertFalse(policy.blur)
            assertEquals(.18f, policy.opacity, .001f)
            assertEquals(0f, wallpaperSurfacePolicy(value.copy(barWallpaperOpacity = 0f), area).opacity, .001f)
            assertTrue(wallpaperSurfacePolicy(value.copy(barWallpaperBlur = true), area).blur)
        }
        val course = wallpaperSurfacePolicy(value.copy(barWallpaperBlur = false, barWallpaperOpacity = 0f), WallpaperArea.COURSE, .85f)
        assertTrue(course.blur)
        assertEquals(.85f, course.opacity, .001f)
    }
    @Test fun borderIsAnIndependentOptIn() {
        listOf(false, true).forEach { adaptive ->
            assertFalse(readableCourseSurface(Color.Blue, Color.White, adaptive, .7f).outlined)
            assertTrue(readableCourseSurface(Color.Blue, Color.White, adaptive, .7f, outlined = true).outlined)
        }
    }
    @Test fun wakeUpWhiteLetteringStaysReadableWhenAdaptiveIsOn() {
        campusStyleColors(CampusStyle.WAKE_UP).courses.forEach { base ->
            val original = readableCourseSurface(base, null, false, .7f, preferWhiteText = true)
            assertEquals(base, original.background)
            assertEquals(Color.White, original.foreground)
            val adaptive = readableCourseSurface(base, Color.White, true, .4f, preferWhiteText = true)
            assertEquals(Color.White, adaptive.foreground)
            (0..20).forEach { step ->
                val pixel = ReadableColors.mix(Color.Black, Color.White, step / 20f)
                assertTrue(ReadableColors.contrast(adaptive.foreground,
                    ReadableColors.mix(pixel, adaptive.background, adaptive.opacity)) >= 4.5)
            }
        }
    }
    @Test fun allPresetsHaveReadableLightAndDarkRoles() {
        CampusStyle.entries.forEach { style ->
            listOf(false, true).forEach { dark ->
                val s = softColorScheme(campusStyleColors(style).seed, dark)
                listOf(s.onSurface to s.surface, s.onSurfaceVariant to s.surface, s.onPrimary to s.primary,
                    s.onPrimaryContainer to s.primaryContainer, s.onSurfaceVariant to s.surfaceContainerHighest).forEach { (text, bg) ->
                    assertTrue("$style dark=$dark ratio=${ReadableColors.contrast(text,bg)}", ReadableColors.contrast(text,bg) >= 4.5)
                }
            }
        }
    }
    @Test fun adaptiveTextRemainsReadableAcrossMixedPhotoPixels() {
        val pictures = listOf(Color.Black, Color.White, Color.Red, Color.Blue, Color(0xFF9CA3AF))
        CampusStyle.entries.forEach { style ->
            (campusStyleColors(style).courses + listOf(Color.Black, Color.White, Color(0xFF777777))).forEach { base ->
                pictures.forEach { seed ->
                    val colors = readableCourseSurface(base, seed, true, .4f)
                    val pixels = pictures + (0..20).map { ReadableColors.mix(Color.Black, Color.White, it / 20f) }
                    pixels.forEach { pixel ->
                        assertTrue("$style ${colors.opacity}", ReadableColors.contrast(colors.foreground,
                            ReadableColors.mix(pixel, colors.background, colors.opacity)) >= 4.5)
                    }
                }
            }
        }
    }
    @Test fun disablingAdaptivePreservesColorAndRequestedOpacity() {
        val base = Color(0xFFB4CFD0)
        val colors = readableCourseSurface(base, Color.Black, false, .4f)
        assertEquals(base, colors.background)
        assertEquals(.4f, colors.opacity, .001f)
        assertFalse(colors.outlined)
    }
    @Test fun noWallpaperRetainsCourseColor() {
        val base = Color(0xFFC0D8EF)
        assertEquals(base, readableCourseSurface(base, null, true, .4f).background)
    }
    @Test fun midGrayTextCannotPassAcrossBlackWhiteExtremesWithoutScrim() {
        val text = Color(.46f, .46f, .46f)
        val alpha = ReadableColors.safeOpacity(Color.White, text, 0f)
        assertTrue(alpha > .8f)
        assertTrue(ReadableColors.contrast(text, ReadableColors.mix(Color.Black, Color.White, alpha)) >= 4.5)
    }
    @Test fun invalidOrMissingSavedStyleFallsBackWithoutResettingSettings() {
        assertEquals(CampusStyle.CLASSIC, CampusStyle.fromStored(null))
        assertEquals(CampusStyle.CLASSIC, CampusStyle.fromStored("obsolete-preset"))
        assertEquals(CampusStyle.SAGE, CampusStyle.fromStored("SAGE"))
    }
    @Test fun wallpaperSeedsProduceReadablePrimaryLabels() {
        listOf(Color.Black, Color.White, Color.Yellow, Color.Red, Color(0xFFDED7D5)).forEach { seed ->
            listOf(false, true).forEach { dark ->
                val s = softColorScheme(seed, dark)
                assertTrue(ReadableColors.contrast(s.primary, s.surface) >= 4.5)
            }
        }
    }
}
