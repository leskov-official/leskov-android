package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

private val LeskovLightColorScheme = lightColorScheme(
    primary = Color(0xFF245BD7), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EAFF), onPrimaryContainer = Color(0xFF194B9C),
    secondary = Color(0xFF096E71), background = Color(0xFFEEF2FA),
    surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE7EEF9),
    onSurface = Color(0xFF101A2E), onBackground = Color(0xFF101A2E),
    onSurfaceVariant = Color(0xFF55658A), outline = Color(0xFFB8C6DE),
    error = Color(0xFFB62949)
)

private val LeskovDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7AA5FF),
    onPrimary = LeskovBackground,
    primaryContainer = Color(0xFF203755),
    onPrimaryContainer = Color(0xFFDCEAFF),
    secondary = Color(0xFF79E3D2),
    onSecondary = LeskovBackground,
    secondaryContainer = LeskovSurfaceVariant,
    onSecondaryContainer = LeskovOnSurface,
    tertiary = LeskovPrimaryVariant,
    onTertiary = LeskovOnBackground,
    background = Color(0xFF060B16),
    onBackground = LeskovOnBackground,
    surface = Color(0xFF0A1325),
    onSurface = Color(0xFFEEF4FF),
    surfaceVariant = Color(0xFF17263C),
    onSurfaceVariant = Color(0xFFAAB8D4),
    outline = Color(0xFF40536E),
    error = LeskovError,
    onError = LeskovOnBackground
)

@Composable
fun MyApplicationTheme(
    light: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (light) LeskovLightColorScheme else LeskovDarkColorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
