package com.nexusai.agent.ui.theme

import androidx.compose.ui.graphics.Color

// Pulled directly from the Stitch "nexus_ai_*" reference designs (dark, primary palette).
val NexusPrimary = Color(0xFFA2C9FF)
val NexusOnPrimary = Color(0xFF00315C)
val NexusPrimaryContainer = Color(0xFF58A6FF)
val NexusOnPrimaryContainer = Color(0xFF003A6B)

val NexusSecondary = Color(0xFF67DF70) // emerald "live / streaming" accent
val NexusOnSecondary = Color(0xFF00390D)
val NexusSecondaryContainer = Color(0xFF27A640)
val NexusOnSecondaryContainer = Color(0xFF00320A)
val NexusSecondaryFixedDim = Color(0xFF83FC89)

val NexusTertiary = Color(0xFFD8BAFF) // violet "reasoning / thought" accent
val NexusOnTertiary = Color(0xFF430882)
val NexusTertiaryContainer = Color(0xFFBC8CFF)
val NexusOnTertiaryContainer = Color(0xFF4D198B)

val NexusError = Color(0xFFFFB4AB)
val NexusOnError = Color(0xFF690005)
val NexusErrorContainer = Color(0xFF93000A)
val NexusOnErrorContainer = Color(0xFFFFDAD6)

val NexusBackground = Color(0xFF10141A)
val NexusOnBackground = Color(0xFFDFE2EB)
val NexusSurface = Color(0xFF10141A)
val NexusOnSurface = Color(0xFFDFE2EB)
val NexusSurfaceVariant = Color(0xFF31353C)
val NexusOnSurfaceVariant = Color(0xFFC0C7D4)

val NexusOutline = Color(0xFF8B919D)
val NexusOutlineVariant = Color(0xFF414752)

val NexusSurfaceContainerLowest = Color(0xFF0A0E14)
val NexusSurfaceContainerLow = Color(0xFF181C22)
val NexusSurfaceContainer = Color(0xFF1C2026)
val NexusSurfaceContainerHigh = Color(0xFF262A31)
val NexusSurfaceContainerHighest = Color(0xFF31353C)
val NexusSurfaceBright = Color(0xFF353940)
val NexusSurfaceDim = Color(0xFF10141A)

val NexusInverseSurface = Color(0xFFDFE2EB)
val NexusInverseOnSurface = Color(0xFF2D3137)
val NexusInversePrimary = Color(0xFF0060AA)

// Status-specific accents used by the Live Activity Feed action cards.
val StatusRunning = NexusSecondary
val StatusDone = NexusPrimary
val StatusError = NexusError
