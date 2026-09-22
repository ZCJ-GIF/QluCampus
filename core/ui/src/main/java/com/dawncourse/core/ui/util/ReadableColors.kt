// QluCampus 0.2.8, GPL-3.0. Pure sRGB math; no bitmap/Android calls on the draw path.
package com.dawncourse.core.ui.util

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

object ReadableColors {
    fun mix(a: Color, b: Color, fraction: Float): Color {
        val t = fraction.coerceIn(0f, 1f)
        return Color(a.red * (1 - t) + b.red * t, a.green * (1 - t) + b.green * t, a.blue * (1 - t) + b.blue * t)
    }
    fun luminance(color: Color): Double {
        fun linear(value: Float): Double = if (value <= .04045f) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
        return .2126 * linear(color.red) + .7152 * linear(color.green) + .0722 * linear(color.blue)
    }
    fun contrast(a: Color, b: Color): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    fun bestText(background: Color): Color = if (contrast(Color.Black, background) >= contrast(Color.White, background)) Color.Black else Color.White

    /** Black/white are the worst-case image extremes for a fixed black/white foreground.
     * Raising the tint opacity protects text even over mixed bright/dark photo regions.
     */
    fun safeOpacity(tint: Color, text: Color, requested: Float, minimumContrast: Double = 4.5): Float {
        val start = requested.coerceIn(0f, 1f)
        for (step in 0..100) {
            val alpha = (start + step / 100f).coerceAtMost(1f)
            val darkest = mix(Color.Black, tint, alpha)
            val lightest = mix(Color.White, tint, alpha)
            val crossesText = luminance(text) > luminance(darkest) && luminance(text) < luminance(lightest)
            if (!crossesText && contrast(text, darkest) >= minimumContrast && contrast(text, lightest) >= minimumContrast) return alpha
        }
        return 1f
    }
}
