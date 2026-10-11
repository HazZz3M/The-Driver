package com.indrive.assistant.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = InDriveNeonGreen,
    onPrimary = DarkBackground,
    primaryContainer = InDriveNeonGreenDark,
    onPrimaryContainer = TextPrimary,
    secondary = AccentCyan,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkCardBorder,
    error = LossRed
)

private val LightColorScheme = lightColorScheme(
    primary = InDriveNeonGreenDark,
    onPrimary = LightSurface,
    primaryContainer = InDriveNeonGreenLight,
    onPrimaryContainer = LightTextPrimary,
    secondary = AccentCyan,
    onSecondary = LightSurface,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightCardBorder,
    error = LossRed
)

@Composable
fun InDriveAssistantTheme(
    themeMode: String = "dark",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true // default dark
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                val bg = if (isDark) DarkBackground else LightBackground
                it.statusBarColor = bg.toArgb()
                it.navigationBarColor = (if (isDark) DarkSurface else LightSurface).toArgb()
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
