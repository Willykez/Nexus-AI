package com.nexusforge.app.ui.theme

import androidx.compose.ui.graphics.Color

// Dark-first palette: a coding tool lives on-screen for long stretches, so it should feel
// calm and low-glare, with one clear accent for "the agent is doing something".
val InkBlack = Color(0xFF0B0E14)
val PanelDark = Color(0xFF141821)
val PanelDarkElevated = Color(0xFF1B2029)
val StrokeSubtle = Color(0xFF262C38)
val TextPrimaryDark = Color(0xFFE9ECF3)
val TextSecondaryDark = Color(0xFF9BA3B4)

val AccentForge = Color(0xFF5EEAD4) // teal — "running / live" accent
val AccentAmber = Color(0xFFF5B860) // "attention" accent for the attached-folder mode
val SuccessGreen = Color(0xFF4ADE80)
val ErrorRed = Color(0xFFF87171)

val SurfaceLight = Color(0xFFF7F8FB)
val PanelLight = Color(0xFFFFFFFF)
val PanelLightElevated = Color(0xFFEDEFF5)
val StrokeSubtleLight = Color(0xFFDBDFE8)
val TextPrimaryLight = Color(0xFF14161C)
val TextSecondaryLight = Color(0xFF5B6172)

// The dark accent teal is too low-contrast as a filled-button color on a white background —
// this is the same hue, darkened, used as `primary` in the light scheme specifically.
val AccentForgeOnLight = Color(0xFF0E8C7C)
val AccentAmberOnLight = Color(0xFFB9791E)
