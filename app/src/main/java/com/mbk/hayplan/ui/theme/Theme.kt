package com.mbk.hayplan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HayPlanColors = lightColorScheme(
    primary = Color(0xFF006B60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE8),
    onPrimaryContainer = Color(0xFF123F39),
    secondary = Color(0xFF42647A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8EAF5),
    onSecondaryContainer = Color(0xFF29495D),
    tertiary = Color(0xFF8A6500),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3A6),
    onTertiaryContainer = Color(0xFF2A1D00),
    background = Color(0xFFF5FAF9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7F0ED),
    onSurface = Color(0xFF17201E),
    onSurfaceVariant = Color(0xFF55615E),
    outline = Color(0xFF87938F),
    outlineVariant = Color(0xFFDCE5E2),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun HayPlanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HayPlanColors,
        content = content,
    )
}
