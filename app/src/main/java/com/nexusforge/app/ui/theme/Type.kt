package com.nexusforge.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MonoFamily = FontFamily.Monospace
val UiFamily = FontFamily.SansSerif

val NexusTypography = Typography(
    titleLarge = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.3.sp)
)
