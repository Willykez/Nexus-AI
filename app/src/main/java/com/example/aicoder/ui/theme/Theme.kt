package com.example.aicoder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = NexusBlue,
    onPrimary = Color(0xFF07111B),
    secondary = NexusPurple,
    tertiary = NexusGreen,
    background = NexusDarkBackground,
    onBackground = Color(0xFFE8EDF2),
    surface = NexusDarkSurface,
    onSurface = Color(0xFFE8EDF2),
    surfaceVariant = NexusDarkSurface2,
    onSurfaceVariant = Color(0xFFB5C0CC)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1769AA),
    onPrimary = Color.White,
    secondary = Color(0xFF6947A5),
    tertiary = Color(0xFF087A42),
    background = NexusLightBackground,
    onBackground = Color(0xFF18202A),
    surface = NexusLightSurface,
    onSurface = Color(0xFF18202A),
    surfaceVariant = NexusLightSurface2,
    onSurfaceVariant = Color(0xFF526170)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography,
        content = content
    )
}
