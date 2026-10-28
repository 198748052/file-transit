package com.filetransit.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D5A73),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EAF1),
    onPrimaryContainer = Color(0xFF1E3242),
    secondary = Color(0xFF53606C),
    secondaryContainer = Color(0xFFE2E7EB),
    tertiary = Color(0xFF4A6B66),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EDF0),
    background = Color(0xFFF7F8F9),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C3D9),
    onPrimary = Color(0xFF14293A),
    primaryContainer = Color(0xFF2E4457),
    onPrimaryContainer = Color(0xFFD3E2EF),
    secondary = Color(0xFFB0BEC9),
    secondaryContainer = Color(0xFF333E47),
    tertiary = Color(0xFF9FC4BE),
    background = Color(0xFF101418),
    surface = Color(0xFF171C21),
    surfaceVariant = Color(0xFF3A434B),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FileTransitTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}
