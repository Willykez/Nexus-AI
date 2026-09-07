package com.nexusai.agent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The Stitch reference designs specify "Geist" for UI text and "JetBrains Mono" for
 * code/labels. Both are Google Fonts; we fall back to the platform Sans/Monospace
 * families so the project compiles without bundling font assets. Swap these for
 * androidx.compose.ui.text.googlefonts.GoogleFont providers if you want the exact faces.
 */
val GeistFallback = FontFamily.SansSerif
val JetBrainsMonoFallback = FontFamily.Monospace

// Code / monospace text style used by the StreamingCodeViewer and file paths.
val CodeBlockStyle = TextStyle(
    fontFamily = JetBrainsMonoFallback,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp
)

val CodeInlineStyle = TextStyle(
    fontFamily = JetBrainsMonoFallback,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp
)

val LabelXsStyle = TextStyle(
    fontFamily = JetBrainsMonoFallback,
    fontWeight = FontWeight.SemiBold,
    fontSize = 9.sp,
    lineHeight = 12.sp,
    letterSpacing = 0.6.sp
)

val NexusTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = GeistFallback, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GeistFallback, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = GeistFallback, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = GeistFallback, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = GeistFallback, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = GeistFallback, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = GeistFallback, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = GeistFallback, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFallback, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFallback, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.4.sp
    )
)
