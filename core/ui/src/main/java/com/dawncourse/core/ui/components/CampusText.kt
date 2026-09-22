// QluCampus 0.2.10, GPL-3.0. Text preferences never overwrite course colors.
package com.dawncourse.core.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import com.dawncourse.core.domain.model.CampusTextColorMode
import com.dawncourse.core.domain.model.CampusStyle
import com.dawncourse.core.domain.model.WallpaperMode
import com.dawncourse.core.ui.theme.LocalAppSettings
import com.dawncourse.core.ui.theme.wakeUpBackground
import com.dawncourse.core.ui.util.ReadableColors

fun customTextColor(hex: String): Color = if (CampusTextColorMode.validColor(hex))
    Color(0xFF000000 or hex.substring(1).toLong(16)) else Color(0xFF202124)

fun resolveCampusText(mode: CampusTextColorMode, hex: String, background: Color, original: Color): Color = when (mode) {
    CampusTextColorMode.STYLE -> original
    CampusTextColorMode.AUTO_BW -> ReadableColors.bestText(background)
    CampusTextColorMode.CUSTOM -> customTextColor(hex)
}

internal data class WallpaperSamples(val sourceWidth: Int, val sourceHeight: Int, val columns: Int, val rows: Int, val pixels: IntArray)

/** Same center-crop/fill mapping as the backdrop. Only a cached tiny color grid is read. */
internal fun sampleWallpaperRegion(samples: WallpaperSamples, viewport: IntSize, origin: Offset,
    region: IntSize, mode: WallpaperMode, underneath: Color): Color {
    if (viewport.width <= 0 || viewport.height <= 0) return underneath
    val scale = maxOf(viewport.width.toFloat() / samples.sourceWidth, viewport.height.toFloat() / samples.sourceHeight)
    val width = if (mode == WallpaperMode.FILL) viewport.width.toFloat() else samples.sourceWidth * scale
    val height = if (mode == WallpaperMode.FILL) viewport.height.toFloat() else samples.sourceHeight * scale
    var red = 0f; var green = 0f; var blue = 0f
    for (y in 0..6) for (x in 0..6) {
        val px = origin.x + region.width * (x + .5f) / 7
        val py = origin.y + region.height * (y + .5f) / 7
        val sx = (((px - (viewport.width - width) / 2) / width) * samples.columns).toInt().coerceIn(0, samples.columns - 1)
        val sy = (((py - (viewport.height - height) / 2) / height) * samples.rows).toInt().coerceIn(0, samples.rows - 1)
        val raw = Color(samples.pixels[sy * samples.columns + sx])
        val pixel = ReadableColors.mix(underneath, raw, raw.alpha)
        red += pixel.red; green += pixel.green; blue += pixel.blue
    }
    return Color(red / 49, green / 49, blue / 49)
}

data class CampusTextAppearance(val color: Color, val modifier: Modifier)

/** Attach modifier to the same box that draws glassSurface, so top and bottom
 * bars can choose different text over the same image. No sampling on draw frames. */
@Composable
fun rememberBackdropText(area: WallpaperArea, fallback: Color = MaterialTheme.colorScheme.onSurface,
    tint: Color = Color.Unspecified, forceOpaque: Boolean = false, drawnOpacity: Float? = null): CampusTextAppearance {
    val settings = LocalAppSettings.current
    val appearance = settings.campusAppearance
    val backdrop = LocalBackdrop.current
    val surface = MaterialTheme.colorScheme.surface
    val root = MaterialTheme.colorScheme.background
    val overlay = if (tint == Color.Unspecified) surface else tint
    var position by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val selected = when (area) {
        WallpaperArea.HEADER -> appearance.wallpaperOnHeader
        WallpaperArea.NAVIGATION -> appearance.wallpaperOnNavigation
        WallpaperArea.COURSE -> appearance.wallpaperOnCourses
        WallpaperArea.PANEL -> appearance.wallpaperOnPanels
    }
    val policy = wallpaperSurfacePolicy(appearance, area)
    val color = remember(settings, backdrop, position, size, overlay, root, fallback, forceOpaque, drawnOpacity, area) {
        var background = overlay
        val samples = if (policy.blur) backdrop.blurredSamples ?: backdrop.samples else backdrop.samples
        if (!forceOpaque && selected && samples != null) {
            val image = sampleWallpaperRegion(samples, backdrop.size, position, size, settings.wallpaperMode, root)
            background = ReadableColors.mix(image, Color.Black, 1f - settings.backgroundBrightness)
            background = ReadableColors.mix(background, overlay, drawnOpacity ?: policy.opacity)
        } else if (!forceOpaque && selected && backdrop.image == null && appearance.style == CampusStyle.WAKE_UP && area != WallpaperArea.COURSE) {
            val gradient = wakeUpBackground(ReadableColors.luminance(root) < .2)
            val fraction = ((position.y + size.height / 2f) / backdrop.size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
            background = ReadableColors.mix(ReadableColors.mix(gradient.first(), gradient.last(), fraction), overlay, drawnOpacity ?: policy.opacity)
        }
        resolveCampusText(appearance.textColorMode, appearance.customTextColor, background, fallback)
    }
    val tracking = if (appearance.textColorMode == CampusTextColorMode.AUTO_BW) Modifier.onGloballyPositioned {
        position = it.positionInRoot() - backdrop.origin; size = it.size
    } else Modifier
    return CampusTextAppearance(color, tracking)
}

/** Change neutral text only. Button fills, errors, and grade pass/fail colors keep their meaning. */
@Composable
fun CampusTextColors(color: Color, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    if (LocalAppSettings.current.campusAppearance.textColorMode == CampusTextColorMode.STYLE) content()
    else MaterialTheme(colorScheme = scheme.copy(onSurface = color, onSurfaceVariant = color, onBackground = color)) {
        CompositionLocalProvider(LocalContentColor provides color, content = content)
    }
}
