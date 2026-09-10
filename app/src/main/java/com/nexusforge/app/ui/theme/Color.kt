package com.nexusforge.app.ui.theme

import androidx.compose.ui.graphics.Color

// Ported from the Aurora reference design: near-black surfaces, one warm accent used sparingly
// (send button, active states, the "agent is live" signal), borders instead of heavy shadows
// for elevation.
val InkBlack = Color(0xFF09090B)
val PanelDark = Color(0xFF16181D)
val PanelDarkElevated = Color(0xFF1C1F26)
val StrokeSubtle = Color(0xFF26282F)
val TextPrimaryDark = Color(0xFFF4F4F5)
val TextSecondaryDark = Color(0xFFA1A1AA)

val AccentForge = Color(0xFFF97316) // warm orange — "running / live" accent, matches Aurora's --accent
val AccentAmber = Color(0xFFFB923C) // lighter companion accent, matches Aurora's --accent2
val SuccessGreen = Color(0xFF22C55E)
val ErrorRed = Color(0xFFEF4444)

val SurfaceLight = Color(0xFFFAFAFA)
val PanelLight = Color(0xFFFFFFFF)
val PanelLightElevated = Color(0xFFF0F0F2)
val StrokeSubtleLight = Color(0xFFE4E4E7)
val TextPrimaryLight = Color(0xFF18181B)
val TextSecondaryLight = Color(0xFF52525B)

// The dark accent orange is already reasonably strong as a filled-button color on white, but a
// touch darker reads better for contrast/accessibility on light surfaces (mirrors Aurora's
// light-theme --accent: #ea580c vs dark's #f97316).
val AccentForgeOnLight = Color(0xFFEA580C)
val AccentAmberOnLight = Color(0xFFC2410C)
