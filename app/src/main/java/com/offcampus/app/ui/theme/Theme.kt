package com.offcampus.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = LightViolet,
    onPrimary = LightSurface,
    secondary = LightAmber,
    onSecondary = LightOnBackground,
    tertiary = LightGreen,
    error = LightCoral,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightBorder
)

private val DarkColors = darkColorScheme(
    primary = DarkViolet,
    onPrimary = DarkBackground,
    secondary = DarkAmber,
    onSecondary = DarkBackground,
    tertiary = DarkGreen,
    error = DarkCoral,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkBorder
)

/**
 * App-wide theme. Deliberately does NOT use Android 12's dynamic (wallpaper-based) color —
 * OffCampus has its own fixed brand palette from the Phase 0 brief, and dynamic color would
 * make every install look different and drift away from that system.
 */
@Composable
fun OffCampusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OffCampusTypography,
        shapes = OffCampusShapes,
        content = content
    )
}
