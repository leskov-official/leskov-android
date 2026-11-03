package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LeskovDarkColorScheme = darkColorScheme(
    primary = LeskovPrimary,
    onPrimary = LeskovBackground,
    primaryContainer = LeskovSurfaceSelected,
    onPrimaryContainer = LeskovAccentLight,
    secondary = LeskovAccentLight,
    onSecondary = LeskovBackground,
    secondaryContainer = LeskovSurfaceVariant,
    onSecondaryContainer = LeskovOnSurface,
    tertiary = LeskovPrimaryVariant,
    onTertiary = LeskovOnBackground,
    background = LeskovBackground,
    onBackground = LeskovOnBackground,
    surface = LeskovSurface,
    onSurface = LeskovOnSurface,
    surfaceVariant = LeskovSurfaceVariant,
    onSurfaceVariant = LeskovTextMuted,
    error = LeskovError,
    onError = LeskovOnBackground
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LeskovDarkColorScheme,
        typography = Typography,
        content = content
    )
}
