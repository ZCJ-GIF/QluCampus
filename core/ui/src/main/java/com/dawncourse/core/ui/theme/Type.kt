package com.dawncourse.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.dawncourse.core.domain.model.AppFontStyle

/**
 * 字体排版样式定义
 *
 * 定义 Material Design 3 的排版系统 (Typography)。
 * 可以在此全局修改字体大小、字重、行高等。
 */
val Typography = Typography(
    // 大号正文样式，用于普通文本
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        )
    )
)

fun getTypography(style: AppFontStyle): Typography {
    val selectedFamily = when (style) {
        AppFontStyle.SYSTEM, AppFontStyle.BOLD -> FontFamily.Default
        AppFontStyle.SERIF -> FontFamily.Serif
        AppFontStyle.MONOSPACE -> FontFamily.Monospace
    }
    val platformStyle = PlatformTextStyle(includeFontPadding = false)
    val lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )

    val base = Typography(
        bodyLarge = TextStyle(
            fontFamily = selectedFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
            platformStyle = platformStyle,
            lineHeightStyle = lineHeightStyle
        ),
        titleLarge = TextStyle(
            fontFamily = selectedFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
            platformStyle = platformStyle,
            lineHeightStyle = lineHeightStyle
        ),
        labelSmall = TextStyle(
            fontFamily = selectedFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
            platformStyle = platformStyle,
            lineHeightStyle = lineHeightStyle
        )
    )
    // Cover every Material role, including course bodyMedium/bodySmall and navigation
    // labels. Previously only three roles used the selected family.
    fun TextStyle.applyFont() = copy(fontFamily = selectedFamily,
        fontWeight = if (style == AppFontStyle.BOLD) FontWeight.Bold else fontWeight,
        platformStyle = platformStyle, lineHeightStyle = lineHeightStyle)
    return base.copy(
        displayLarge = base.displayLarge.applyFont(), displayMedium = base.displayMedium.applyFont(), displaySmall = base.displaySmall.applyFont(),
        headlineLarge = base.headlineLarge.applyFont(), headlineMedium = base.headlineMedium.applyFont(), headlineSmall = base.headlineSmall.applyFont(),
        titleLarge = base.titleLarge.applyFont(), titleMedium = base.titleMedium.applyFont(), titleSmall = base.titleSmall.applyFont(),
        bodyLarge = base.bodyLarge.applyFont(), bodyMedium = base.bodyMedium.applyFont(), bodySmall = base.bodySmall.applyFont(),
        labelLarge = base.labelLarge.applyFont(), labelMedium = base.labelMedium.applyFont(), labelSmall = base.labelSmall.applyFont())
}
