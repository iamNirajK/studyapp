package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SophisticatedIndigo,
    secondary = SophisticatedSlate,
    tertiary = SophisticatedCyan,
    background = SophisticatedBg,
    surface = SophisticatedSurface,
    surfaceVariant = SophisticatedSecondarySurface,
    onPrimary = SophisticatedWhite,
    onSecondary = SophisticatedSlateLight,
    onBackground = SophisticatedSlateLight,
    onSurface = SophisticatedWhite,
    onSurfaceVariant = SophisticatedSlateLight,
    outline = SophisticatedBorder,
    error = SophisticatedCrimson
)

private val LightColorScheme = lightColorScheme(
    primary = SophisticatedIndigoDark,
    secondary = SophisticatedSlate,
    tertiary = SophisticatedCyan,
    background = SophisticatedBg, // Force sophisticated dark aesthetics as requested!
    surface = SophisticatedSurface,
    surfaceVariant = SophisticatedSecondarySurface,
    onPrimary = SophisticatedWhite,
    onSecondary = SophisticatedSlateLight,
    onBackground = SophisticatedSlateLight,
    onSurface = SophisticatedWhite,
    onSurfaceVariant = SophisticatedSlateLight,
    outline = SophisticatedBorder,
    error = SophisticatedCrimson
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Both map to Sophisticated Dark layout variants to preserve the requested dark vibe perfectly!
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
