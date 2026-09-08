package com.example.aicoder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp)
)
