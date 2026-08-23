package com.example.jobtracker.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val WearColorScheme = ColorScheme(
    primary = Color(0xFF64B5F6),
    primaryDim = Color(0xFF42A5F5),
    primaryContainer = Color(0xFF1976D2),
    onPrimary = Color.Black,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF81C784),
    secondaryDim = Color(0xFF66BB6A),
    secondaryContainer = Color(0xFF388E3C),
    onSecondary = Color.Black,
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFFFB74D),
    tertiaryDim = Color(0xFFFFA726),
    tertiaryContainer = Color(0xFFF57C00),
    onTertiary = Color.Black,
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surfaceContainer = Color(0xFF1E1E1E),
    onSurface = Color.White,
    onSurfaceVariant = Color.LightGray,
    outline = Color.Gray,
    outlineVariant = Color.DarkGray,
    error = Color(0xFFE57373),
    onError = Color.Black,
    errorContainer = Color(0xFFD32F2F),
    onErrorContainer = Color.White
)

@Composable
fun JobTrackerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        content = content
    )
}
