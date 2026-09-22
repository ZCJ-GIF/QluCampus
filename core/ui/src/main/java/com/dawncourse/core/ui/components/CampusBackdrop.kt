// QluCampus 0.2.0, GPL-3.0. Cached wallpaper backdrop; foreground content is never blurred.
package com.dawncourse.core.ui.components

import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import com.dawncourse.core.domain.model.WallpaperMode
import com.dawncourse.core.domain.model.CampusAppearance
import com.dawncourse.core.domain.model.CampusStyle
import com.dawncourse.core.ui.theme.LocalAppSettings
import com.dawncourse.core.ui.theme.wakeUpBackground
import com.dawncourse.core.ui.util.ReadableColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal data class Backdrop(val image: ImageBitmap? = null, val blurred: ImageBitmap? = null,
    val size: IntSize = IntSize.Zero, val origin: Offset = Offset.Zero,
    val samples: WallpaperSamples? = null, val blurredSamples: WallpaperSamples? = null)
internal val LocalBackdrop = staticCompositionLocalOf { Backdrop() }

@Composable
private fun wallpaperSamples(image: ImageBitmap?, enabled: Boolean): WallpaperSamples? {
    val samples by produceState<WallpaperSamples?>(null, image, enabled) {
        value = if (image == null || !enabled) null else withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = image.asAndroidBitmap()
                val pixels = IntArray(32 * 32) { index -> bitmap.getPixel(
                    ((index % 32 + .5f) * bitmap.width / 32).toInt().coerceAtMost(bitmap.width - 1),
                    ((index / 32 + .5f) * bitmap.height / 32).toInt().coerceAtMost(bitmap.height - 1)) }
                WallpaperSamples(bitmap.width, bitmap.height, 32, 32, pixels)
            }.getOrNull()
        }
    }
    return samples
}

@Composable
private fun wallpaper(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = null
        value = withContext(Dispatchers.IO) {
            if (uri == null) null else runCatching {
                val request = ImageRequest.Builder(context).data(uri).size(1600).allowHardware(false).build()
                context.imageLoader.execute(request).drawable?.toBitmap()?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

private fun DrawScope.paintWallpaper(image: ImageBitmap, area: IntSize, offset: Offset, mode: WallpaperMode, brightness: Float) {
    if (area.width <= 0 || area.height <= 0) return
    val scale = maxOf(area.width.toFloat() / image.width, area.height.toFloat() / image.height)
    val width = if (mode == WallpaperMode.FILL) area.width else (image.width * scale).roundToInt()
    val height = if (mode == WallpaperMode.FILL) area.height else (image.height * scale).roundToInt()
    drawImage(image, dstOffset = IntOffset(((area.width - width) / 2f - offset.x).roundToInt(), ((area.height - height) / 2f - offset.y).roundToInt()), dstSize = IntSize(width, height))
    drawRect(Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 1f)))
}

private fun DrawScope.paintWakeUp(colors: List<Color>, area: IntSize, offset: Offset = Offset.Zero) {
    drawRect(Brush.verticalGradient(colors, startY = -offset.y, endY = maxOf(1f, area.height.toFloat()) - offset.y))
}

@Composable
fun CampusBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val settings = LocalAppSettings.current
    val image = wallpaper(settings.wallpaperUri)
    val blurred = wallpaper(settings.blurredWallpaperUri)
    val autoText = settings.campusAppearance.textColorMode == com.dawncourse.core.domain.model.CampusTextColorMode.AUTO_BW
    val samples = wallpaperSamples(image, autoText)
    val blurredSamples = wallpaperSamples(blurred, autoText)
    var area by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val surface = MaterialTheme.colorScheme.background
    val wakeUp = if (settings.campusAppearance.style == CampusStyle.WAKE_UP)
        wakeUpBackground(ReadableColors.luminance(surface) < .2) else null
    CompositionLocalProvider(LocalBackdrop provides Backdrop(image, blurred, area, origin, samples, blurredSamples)) {
        Box(modifier.onGloballyPositioned { area = it.size; origin = it.positionInRoot() }.drawWithContent {
            drawRect(surface)
            if (image == null && wakeUp != null) paintWakeUp(wakeUp, area)
            image?.let { paintWallpaper(it, area, Offset.Zero, settings.wallpaperMode, settings.backgroundBrightness) }
            if (image != null) drawRect(surface.copy(alpha = settings.transparency))
            drawContent()
        }, content = content)
    }
}

enum class WallpaperArea { HEADER, NAVIGATION, COURSE, PANEL }

data class WallpaperSurfacePolicy(val blur: Boolean, val opacity: Float)

/** Outside courses, photo visibility is controlled independently from course readability. */
fun wallpaperSurfacePolicy(appearance: CampusAppearance, area: WallpaperArea, courseOpacity: Float? = null): WallpaperSurfacePolicy =
    if (area == WallpaperArea.COURSE) WallpaperSurfacePolicy(appearance.glassEnabled && appearance.glassRadius > 0,
        (courseOpacity ?: appearance.glassOpacity).coerceIn(0f, 1f))
    else WallpaperSurfacePolicy(appearance.barWallpaperBlur && appearance.glassRadius > 0,
        appearance.barWallpaperOpacity.coerceIn(0f, 1f))

fun Modifier.glassSurface(shape: Shape = RectangleShape, tint: Color = Color.Unspecified, forceOpaque: Boolean = false,
    area: WallpaperArea = WallpaperArea.PANEL, opacity: Float? = null): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val settings = LocalAppSettings.current
    val appearance = settings.campusAppearance
    val overlay = if (tint == Color.Unspecified) MaterialTheme.colorScheme.surface else tint
    val selected = when (area) {
        WallpaperArea.HEADER -> appearance.wallpaperOnHeader
        WallpaperArea.NAVIGATION -> appearance.wallpaperOnNavigation
        WallpaperArea.COURSE -> appearance.wallpaperOnCourses
        WallpaperArea.PANEL -> appearance.wallpaperOnPanels
    }
    val policy = wallpaperSurfacePolicy(appearance, area, opacity)
    val wakeUp = if (appearance.style == CampusStyle.WAKE_UP && area != WallpaperArea.COURSE)
        wakeUpBackground(ReadableColors.luminance(MaterialTheme.colorScheme.background) < .2) else null
    var position by remember { mutableStateOf(Offset.Zero) }
    clip(shape).onGloballyPositioned { position = it.positionInRoot() - backdrop.origin }.drawWithContent {
        val extended = selected && !forceOpaque && (backdrop.image != null || wakeUp != null)
        if (extended) {
            val image = if (policy.blur) backdrop.blurred ?: backdrop.image else backdrop.image
            if (image != null) paintWallpaper(image, backdrop.size, position, settings.wallpaperMode, settings.backgroundBrightness)
            else if (wakeUp != null) paintWakeUp(wakeUp, backdrop.size, position)
        }
        // Paint the source once and one user-controlled tint. The root's tint is not
        // applied again here: stacking it with the card tint used to erase the photo.
        drawRect(overlay.copy(alpha = if (extended) policy.opacity else 1f))
        drawContent()
    }
}
