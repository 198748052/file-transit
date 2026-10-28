package com.filetransit.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6CBD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E7FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF50606E),
    secondaryContainer = Color(0xFFD3E5F5),
    tertiary = Color(0xFF00696E),
    surface = Color(0xFFFBFCFE),
    surfaceVariant = Color(0xFFE7EDF3),
    background = Color(0xFFF6F8FB),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FCBFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFB7C9D9),
    secondaryContainer = Color(0xFF384956),
    tertiary = Color(0xFF80D4D9),
    background = Color(0xFF0F1418),
    surface = Color(0xFF151B20),
    surfaceVariant = Color(0xFF3F484F),
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
    MaterialTheme(colorScheme = colors, content = content)
}
