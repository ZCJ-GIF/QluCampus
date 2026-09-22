// QluCampus 0.2.8, GPL-3.0. Soft preset colors, shared by theme and preview.
package com.dawncourse.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.dawncourse.core.domain.model.CampusStyle
import com.dawncourse.core.ui.util.ReadableColors

data class CampusStyleColors(val seed: Color, val courses: List<Color>)

fun campusStyleColors(style: CampusStyle): CampusStyleColors = when (style) {
    CampusStyle.MIST -> CampusStyleColors(Color(0xFF526E87), listOf(0xFFC8DDED, 0xFFD4D9EC, 0xFFCAE0DA, 0xFFEAD6CC, 0xFFDFD1E4, 0xFFE4DEC4))
    CampusStyle.SAGE -> CampusStyleColors(Color(0xFF597568), listOf(0xFFCDDFCF, 0xFFDFDEC5, 0xFFC7DDDF, 0xFFE8D6CE, 0xFFD8D1E1, 0xFFE3DECf))
    CampusStyle.CREAM -> CampusStyleColors(Color(0xFF8A6950), listOf(0xFFEAD6BE, 0xFFE9CECC, 0xFFDADDBF, 0xFFC9DCDE, 0xFFDCD1E3, 0xFFDFD8CA))
    CampusStyle.LILAC -> CampusStyleColors(Color(0xFF78658C), listOf(0xFFDBD0EA, 0xFFE6D0DB, 0xFFC9D9E7, 0xFFCEDFD7, 0xFFE8DBC5, 0xFFDED2CC))
    CampusStyle.CLASSIC -> CampusStyleColors(Color(0xFF6750A4), listOf(0xFFE8DEF8, 0xFFC4E7FF, 0xFFC3EED0, 0xFFFDE2E4, 0xFFFFF4DE, 0xFFD7E8CD))
}.let { it }

private fun CampusStyleColors(seed: Color, values: List<Long>): CampusStyleColors =
    CampusStyleColors(seed, values.map { Color(it) })

/** All text/container roles are paired explicitly, including dark mode. */
fun softColorScheme(seed: Color, dark: Boolean): ColorScheme {
    val mix = ReadableColors::mix
    val surface = mix(seed, if (dark) Color(0xFF111315) else Color(0xFFFFFDF9), if (dark) .88f else .96f)
    val primaryBase = mix(seed, if (dark) Color.White else Color.Black, if (dark) .55f else .22f)
    val primary = (0..20).map { mix(primaryBase, if (dark) Color.White else Color.Black, it / 20f) }
        .first { ReadableColors.contrast(it, surface) >= 4.5 }
    val container = mix(seed, if (dark) Color(0xFF171A1B) else Color.White, if (dark) .65f else .80f)
    val raised = mix(seed, if (dark) Color(0xFF25282B) else Color.White, if (dark) .78f else .90f)
    val foreground = if (dark) Color(0xFFF0F0EC) else Color(0xFF242A2B)
    val secondaryText = if (dark) Color(0xFFD0D4D3) else Color(0xFF4C5659)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = ReadableColors.bestText(primary), primaryContainer = container, onPrimaryContainer = foreground,
        secondary = primary, onSecondary = ReadableColors.bestText(primary), secondaryContainer = raised, onSecondaryContainer = foreground,
        tertiary = primary, onTertiary = ReadableColors.bestText(primary), tertiaryContainer = container, onTertiaryContainer = foreground,
        background = surface, onBackground = foreground, surface = surface, onSurface = foreground,
        surfaceVariant = raised, onSurfaceVariant = secondaryText, surfaceTint = primary,
        surfaceDim = surface, surfaceBright = raised, surfaceContainerLowest = surface,
        surfaceContainerLow = mix(surface, raised, .35f), surfaceContainer = mix(surface, raised, .6f),
        surfaceContainerHigh = raised, surfaceContainerHighest = mix(raised, seed, .08f),
        outline = secondaryText, outlineVariant = mix(surface, secondaryText, .25f)
    )
}
