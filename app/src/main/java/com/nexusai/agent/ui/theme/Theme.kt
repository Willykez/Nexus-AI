package com.nexusai.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NexusDarkColorScheme = darkColorScheme(
    primary = NexusPrimary,
    onPrimary = NexusOnPrimary,
    primaryContainer = NexusPrimaryContainer,
    onPrimaryContainer = NexusOnPrimaryContainer,
    secondary = NexusSecondary,
    onSecondary = NexusOnSecondary,
    secondaryContainer = NexusSecondaryContainer,
    onSecondaryContainer = NexusOnSecondaryContainer,
    tertiary = NexusTertiary,
    onTertiary = NexusOnTertiary,
    tertiaryContainer = NexusTertiaryContainer,
    onTertiaryContainer = NexusOnTertiaryContainer,
    error = NexusError,
    onError = NexusOnError,
    errorContainer = NexusErrorContainer,
    onErrorContainer = NexusOnErrorContainer,
    background = NexusBackground,
    onBackground = NexusOnBackground,
    surface = NexusSurface,
    onSurface = NexusOnSurface,
    surfaceVariant = NexusSurfaceVariant,
    onSurfaceVariant = NexusOnSurfaceVariant,
    outline = NexusOutline,
    outlineVariant = NexusOutlineVariant,
    surfaceContainerLowest = NexusSurfaceContainerLowest,
    surfaceContainerLow = NexusSurfaceContainerLow,
    surfaceContainer = NexusSurfaceContainer,
    surfaceContainerHigh = NexusSurfaceContainerHigh,
    surfaceContainerHighest = NexusSurfaceContainerHighest,
    surfaceBright = NexusSurfaceBright,
    surfaceDim = NexusSurfaceDim,
    inverseSurface = NexusInverseSurface,
    inverseOnSurface = NexusInverseOnSurface,
    inversePrimary = NexusInversePrimary
)

// A light fallback derived from the same hues, for completeness — the reference
// designs default to dark mode, which this app always uses regardless of system theme.
private val NexusLightColorScheme = lightColorScheme(
    primary = Color(0xFF0060AA),
    onPrimary = Color.White,
    secondary = Color(0xFF1E8A2E),
    tertiary = Color(0xFF7B4FCF)
)

@Composable
fun NexusAiTheme(
    forceDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = forceDark || isSystemInDarkTheme()
    val colorScheme = if (darkTheme) NexusDarkColorScheme else NexusLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NexusTypography,
        content = content
    )
}
