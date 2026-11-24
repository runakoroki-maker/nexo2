package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NexoDarkColorScheme = darkColorScheme(
    primary = NexoPink,
    onPrimary = Color.White,
    primaryContainer = NexoPinkDark,
    onPrimaryContainer = Color.White,
    secondary = NexoPurple,
    onSecondary = Color.White,
    tertiary = NexoCyan,
    background = NexoDarkBg,
    onBackground = Color.White,
    surface = NexoSurfaceDark,
    onSurface = Color.White,
    surfaceVariant = NexoSurfaceElevated,
    onSurfaceVariant = NexoPinkLight,
    outline = NexoPurpleLight
)

private val NexoLightColorScheme = lightColorScheme(
    primary = NexoPink,
    onPrimary = Color.White,
    primaryContainer = NexoPinkLight,
    onPrimaryContainer = NexoPinkDark,
    secondary = NexoPurple,
    onSecondary = Color.White,
    tertiary = NexoCyan,
    background = NexoWhite,
    onBackground = NexoTextPrimary,
    surface = NexoWhite,
    onSurface = NexoTextPrimary,
    surfaceVariant = NexoBorder,
    onSurfaceVariant = NexoTextSecondary,
    outline = NexoPink
)

@Composable
fun NexoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // NEXO uses its signature hot pink & neon purple theme
    val colorScheme = if (darkTheme) NexoDarkColorScheme else NexoLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
