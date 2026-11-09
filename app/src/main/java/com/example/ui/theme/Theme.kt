package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = HikariDarkPrimary,
    onPrimary = HikariDarkOnPrimary,
    primaryContainer = HikariDarkPrimaryContainer,
    onPrimaryContainer = HikariDarkOnPrimaryContainer,
    secondary = HikariDarkSecondary,
    onSecondary = HikariDarkOnSecondary,
    secondaryContainer = HikariDarkSecondaryContainer,
    onSecondaryContainer = HikariDarkOnSecondaryContainer,
    background = HikariDarkBackground,
    surface = HikariDarkSurface,
    surfaceVariant = HikariDarkSurfaceVariant,
    onBackground = HikariDarkOnBackground,
    onSurface = HikariDarkOnSurface,
    onSurfaceVariant = HikariDarkOnSurfaceVariant,
    error = HikariDarkError,
    onError = HikariDarkOnError,
    outline = HikariDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = HikariLightPrimary,
    onPrimary = HikariLightOnPrimary,
    primaryContainer = HikariLightPrimaryContainer,
    onPrimaryContainer = HikariLightOnPrimaryContainer,
    secondary = HikariLightSecondary,
    onSecondary = HikariLightOnSecondary,
    secondaryContainer = HikariLightSecondaryContainer,
    onSecondaryContainer = HikariLightOnSecondaryContainer,
    background = HikariLightBackground,
    surface = HikariLightSurface,
    surfaceVariant = HikariLightSurfaceVariant,
    onBackground = HikariLightOnBackground,
    onSurface = HikariLightOnSurface,
    onSurfaceVariant = HikariLightOnSurfaceVariant,
    error = HikariLightError,
    onError = HikariLightOnError,
    outline = HikariLightOutline
)

@Composable
fun HikariTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dynamic color is intentionally disabled per Decision D15 for consistent brand identity
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        HikariExtendedColors(watchedSuccess = HikariDarkWatchedSuccess)
    } else {
        HikariExtendedColors(watchedSuccess = HikariLightWatchedSuccess)
    }

    CompositionLocalProvider(LocalHikariColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HikariTypography,
            shapes = HikariShapes,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    HikariTheme(darkTheme = darkTheme, content = content)
}
