package com.dawncourse.core.ui.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import com.dawncourse.core.domain.model.*
import com.dawncourse.core.ui.components.*
import com.dawncourse.core.ui.theme.getTypography
import org.junit.Assert.*
import org.junit.Test

class CampusTextTest {
    @Test fun automaticModeChoosesBlackOnLightAndWhiteOnDark() {
        assertEquals(Color.Black, resolveCampusText(CampusTextColorMode.AUTO_BW, "#FF0000", Color.White, Color.Red))
        assertEquals(Color.White, resolveCampusText(CampusTextColorMode.AUTO_BW, "#FF0000", Color.Black, Color.Red))
        assertEquals(Color.Red, resolveCampusText(CampusTextColorMode.STYLE, "#FF0000", Color.Black, Color.Red))
    }
    @Test fun customColorIsExactAndInvalidColorHasSafeFallback() {
        assertEquals(Color(0xFF254A70), resolveCampusText(CampusTextColorMode.CUSTOM, "#254a70", Color.White, Color.Black))
        assertEquals(Color(0xFF202124), customTextColor("invalid"))
        listOf("#00000000", "#FFF", "red", "#ZZZZZZ").forEach { assertFalse(CampusTextColorMode.validColor(it)) }
        assertEquals(CampusTextColorMode.STYLE, CampusTextColorMode.fromStored("future"))
    }
    @Test fun topAndBottomSampleDifferentPartsOfTheSameImage() {
        val samples = WallpaperSamples(100, 200, 2, 4, intArrayOf(-1,-1,-1,-1,0xFF000000.toInt(),0xFF000000.toInt(),0xFF000000.toInt(),0xFF000000.toInt()))
        listOf(WallpaperMode.FILL, WallpaperMode.CROP).forEach { mode ->
            val top = sampleWallpaperRegion(samples, IntSize(100,200), Offset.Zero, IntSize(100,50), mode, Color.Gray)
            val bottom = sampleWallpaperRegion(samples, IntSize(100,200), Offset(0f,150f), IntSize(100,50), mode, Color.Gray)
            assertEquals(Color.Black, ReadableColors.bestText(top))
            assertEquals(Color.White, ReadableColors.bestText(bottom))
        }
    }
    @Test fun sampleUsesCenterCropAndCompositesTransparentPixels() {
        val stripe = WallpaperSamples(300,100,3,1,intArrayOf(-1,0xFF000000.toInt(),-1))
        val crop = sampleWallpaperRegion(stripe,IntSize(100,100),Offset.Zero,IntSize(100,100),WallpaperMode.CROP,Color.White)
        assertEquals(Color.Black, crop)
        val empty = WallpaperSamples(100,100,1,1,intArrayOf(0))
        assertEquals(Color.White, sampleWallpaperRegion(empty,IntSize(100,100),Offset.Zero,IntSize(100,100),WallpaperMode.FILL,Color.White))
    }
    @Test fun courseAutoDoesNotRecolorPastelsAndIsIndependentOfCourseAdaptive() {
        val base = Color(0xFFE8B76F)
        val colors = readableCourseSurface(base, null, false, .4f, preferWhiteText = true, textMode = CampusTextColorMode.AUTO_BW)
        assertEquals(base, colors.background)
        assertEquals(Color.Black, colors.foreground)
        listOf(Color.Black,Color.White).forEach {
            assertTrue(ReadableColors.contrast(colors.foreground, ReadableColors.mix(it,colors.background,colors.opacity)) >= 4.5)
        }
        val manual = readableCourseSurface(base,null,false,.4f,preferWhiteText=true,textMode=CampusTextColorMode.CUSTOM,textHex="#254A70")
        assertEquals(base, manual.background)
        assertEquals(Color(0xFF254A70), manual.foreground)
        assertEquals(.4f, manual.opacity, .001f)
    }
    @Test fun allTypographyRolesUseSelectedFamilyAndBoldWeight() {
        AppFontStyle.entries.forEach { style ->
            val t = getTypography(style)
            val expected = when(style) { AppFontStyle.SERIF -> FontFamily.Serif; AppFontStyle.MONOSPACE -> FontFamily.Monospace; else -> FontFamily.Default }
            listOf(t.displayLarge,t.displayMedium,t.displaySmall,t.headlineLarge,t.headlineMedium,t.headlineSmall,
                t.titleLarge,t.titleMedium,t.titleSmall,t.bodyLarge,t.bodyMedium,t.bodySmall,t.labelLarge,t.labelMedium,t.labelSmall).forEach {
                assertEquals(expected, it.fontFamily)
                if (style == AppFontStyle.BOLD) assertEquals(FontWeight.Bold, it.fontWeight)
            }
        }
    }
}
