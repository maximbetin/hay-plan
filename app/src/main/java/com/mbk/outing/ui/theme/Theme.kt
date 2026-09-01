package com.mbk.outing.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OutingColors = lightColorScheme(
    primary = Color(0xFF006B60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE8),
    onPrimaryContainer = Color(0xFF123F39),
    secondary = Color(0xFF42647A),
    secondaryContainer = Color(0xFFD8EAF5),
    onSecondaryContainer = Color(0xFF29495D),
    background = Color(0xFFF5FAF9),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17201E),
    onSurfaceVariant = Color(0xFF55615E),
    outlineVariant = Color(0xFFDCE5E2),
)

@Composable
fun OutingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OutingColors,
        content = content,
    )
}
