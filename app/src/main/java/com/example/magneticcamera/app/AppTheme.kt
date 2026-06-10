package com.example.magneticcamera.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MagneticDarkScheme = darkColorScheme(
    primary = Color(0xFF44F2C7),
    onPrimary = Color(0xFF00251D),
    secondary = Color(0xFFFFD447),
    onSecondary = Color(0xFF2B2100),
    tertiary = Color(0xFFFF5B5B),
    background = Color(0xFF080B0D),
    onBackground = Color(0xFFEAF1F4),
    surface = Color(0xFF10161A),
    onSurface = Color(0xFFEAF1F4),
    surfaceVariant = Color(0xFF1A2429),
    onSurfaceVariant = Color(0xFFB8C6CC),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF330000)
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MagneticDarkScheme,
        content = content
    )
}
