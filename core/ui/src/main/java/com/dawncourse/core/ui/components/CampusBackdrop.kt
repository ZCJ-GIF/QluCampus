// QluCampus 0.2.0, GPL-3.0. Cached wallpaper backdrop; foreground content is never blurred.
package com.dawncourse.core.ui.components

import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import android.graphics.BitmapFactory
import android.net.Uri
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
import com.dawncourse.core.ui.theme.LocalAppSettings
import com.dawncourse.core.ui.util.ReadableColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class Backdrop(val image: ImageBitmap? = null, val blurred: ImageBitmap? = null,
    val size: IntSize = IntSize.Zero, val origin: Offset = Offset.Zero)
private val LocalBackdrop = staticCompositionLocalOf { Backdrop() }

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

@Composable
fun CampusBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val settings = LocalAppSettings.current
    val image = wallpaper(settings.wallpaperUri)
    val blurred = wallpaper(settings.blurredWallpaperUri)
    var area by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val surface = MaterialTheme.colorScheme.background
    CompositionLocalProvider(LocalBackdrop provides Backdrop(image, blurred, area, origin)) {
        Box(modifier.onGloballyPositioned { area = it.size; origin = it.positionInRoot() }.drawWithContent {
            drawRect(surface)
            image?.let { paintWallpaper(it, area, Offset.Zero, settings.wallpaperMode, settings.backgroundBrightness) }
            if (image != null) drawRect(surface.copy(alpha = settings.transparency))
            drawContent()
        }, content = content)
    }
}

enum class WallpaperArea { HEADER, NAVIGATION, COURSE, PANEL }

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
    // Bar labels/icons keep the theme's foreground; protect them even over a busy image.
    val minimumOverlay = if (area != WallpaperArea.COURSE) maxOf(
        ReadableColors.safeOpacity(overlay, MaterialTheme.colorScheme.onSurface, .4f),
        ReadableColors.safeOpacity(overlay, MaterialTheme.colorScheme.onSurfaceVariant, .4f),
        ReadableColors.safeOpacity(overlay, MaterialTheme.colorScheme.primary, .4f)
    ) else 0f
    val overlayOpacity = maxOf(opacity ?: appearance.glassOpacity, minimumOverlay).coerceIn(0f, 1f)
    var position by remember { mutableStateOf(Offset.Zero) }
    clip(shape).onGloballyPositioned { position = it.positionInRoot() - backdrop.origin }.drawWithContent {
        val extended = selected && !forceOpaque && backdrop.image != null
        if (extended) (if (appearance.glassEnabled && appearance.glassRadius > 0) backdrop.blurred ?: backdrop.image else backdrop.image)?.let {
            paintWallpaper(it, backdrop.size, position, settings.wallpaperMode, settings.backgroundBrightness)
            drawRect(overlay.copy(alpha = settings.transparency))
        }
        drawRect(overlay.copy(alpha = if (extended) overlayOpacity else 1f))
        drawContent()
    }
}
