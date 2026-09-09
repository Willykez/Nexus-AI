package com.nexusforge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NexusDarkScheme = darkColorScheme(
    primary = AccentForge,
    onPrimary = InkBlack,
    secondary = AccentAmber,
    background = InkBlack,
    onBackground = TextPrimaryDark,
    surface = PanelDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = PanelDarkElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = StrokeSubtle,
    error = ErrorRed
)

@Composable
fun NexusForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexusDarkScheme,
        typography = NexusTypography,
        content = content
    )
}
