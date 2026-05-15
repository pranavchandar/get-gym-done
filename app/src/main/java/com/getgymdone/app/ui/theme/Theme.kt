package com.getgymdone.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode {
    Light, Dark, System;
    companion object {
        fun fromString(s: String) = when (s) {
            "light" -> Light
            "dark"  -> Dark
            else    -> System
        }
    }
    fun toStored(): String = when (this) {
        Light -> "light"; Dark -> "dark"; System -> "system"
    }
}

private val DarkColors = darkColorScheme(
    primary           = AccentLime,
    onPrimary         = AccentLimeFg,
    primaryContainer  = AccentLime,
    onPrimaryContainer = AccentLimeFg,
    secondary         = AccentCoral,
    onSecondary       = AccentLimeFg,
    background        = GymBgDark,
    onBackground      = GymFgDark,
    surface           = GymSurfaceDark,
    onSurface         = GymFgDark,
    surfaceVariant    = GymSurface2Dark,
    onSurfaceVariant  = GymFg2Dark,
    surfaceContainerHighest = GymSurface3Dark,
    outline           = GymLineDark,
    outlineVariant    = GymLineDark,
    error             = AccentCoral,
    onError           = AccentLimeFg,
)

private val LightColors = lightColorScheme(
    primary           = AccentLime,
    onPrimary         = AccentLimeFg,
    primaryContainer  = AccentLime,
    onPrimaryContainer = AccentLimeFg,
    secondary         = AccentCoral,
    onSecondary       = Color.White,
    background        = GymBgLight,
    onBackground      = GymFgLight,
    surface           = GymSurfaceLight,
    onSurface         = GymFgLight,
    surfaceVariant    = GymSurface2Light,
    onSurfaceVariant  = GymFg2Light,
    surfaceContainerHighest = GymSurface3Light,
    outline           = GymLineLight,
    outlineVariant    = GymLineLight,
    error             = AccentCoral,
    onError           = Color.White,
)

@Composable
fun GymDoneTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark  -> true
        ThemeMode.System -> systemDark
    }
    val colors = if (useDark) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            // Light icons on the dark background, dark icons on light.
            controller.isAppearanceLightStatusBars = colors.background.luminance() > 0.5f
            controller.isAppearanceLightNavigationBars = colors.background.luminance() > 0.5f
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography  = GymTypography,
        shapes      = GymShapes,
        content     = content,
    )
}
