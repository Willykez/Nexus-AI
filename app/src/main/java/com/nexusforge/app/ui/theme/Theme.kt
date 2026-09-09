package com.nexusforge.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nexusforge.app.data.ThemeMode

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

private val NexusLightScheme = lightColorScheme(
    primary = AccentForgeOnLight,
    onPrimary = Color.White,
    secondary = AccentAmberOnLight,
    background = SurfaceLight,
    onBackground = TextPrimaryLight,
    surface = PanelLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = PanelLightElevated,
    onSurfaceVariant = TextSecondaryLight,
    outline = StrokeSubtleLight,
    error = ErrorRed
)

@Composable
fun NexusForgeTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) NexusDarkScheme else NexusLightScheme,
        typography = NexusTypography,
        content = content
    )
}
