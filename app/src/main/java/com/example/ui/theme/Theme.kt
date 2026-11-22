package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NeonDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF001F26),
    primaryContainer = NeonCyanContainer,
    onPrimaryContainer = Color(0xFFB5F7FF),
    secondary = NeonMagenta,
    onSecondary = Color(0xFF330019),
    secondaryContainer = NeonMagentaContainer,
    onSecondaryContainer = Color(0xFFFFD8E9),
    tertiary = NeonViolet,
    onTertiary = Color(0xFF1A0942),
    tertiaryContainer = NeonVioletContainer,
    onTertiaryContainer = Color(0xFFE4D9FF),
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    surfaceContainerHighest = Color(0xFF2B314C),
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    error = StatusError,
    onError = Color(0xFF3A000A)
)

val NeonShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NeonFlowTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeonDarkColorScheme,
        typography = NeonTypography,
        shapes = NeonShapes,
        content = content
    )
}
