package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Dark Palette (Default — Obsidian & Indigo Glow)
val HikariDarkPrimary = Color(0xFF8B9BFF)
val HikariDarkOnPrimary = Color(0xFF101433)
val HikariDarkPrimaryContainer = Color(0xFF232B66)
val HikariDarkOnPrimaryContainer = Color(0xFFDCE1FF)

val HikariDarkSecondary = Color(0xFFF2A5C0)
val HikariDarkOnSecondary = Color(0xFF3E1226)
val HikariDarkSecondaryContainer = Color(0xFF5A223B)
val HikariDarkOnSecondaryContainer = Color(0xFFFFD8E4)

val HikariDarkBackground = Color(0xFF0E0F13)
val HikariDarkSurface = Color(0xFF16171D)
val HikariDarkSurfaceVariant = Color(0xFF1F2129)
val HikariDarkOnBackground = Color(0xFFE6E6EB)
val HikariDarkOnSurface = Color(0xFFE6E6EB)
val HikariDarkOnSurfaceVariant = Color(0xFFA6A8B3)
val HikariDarkError = Color(0xFFFFB4AB)
val HikariDarkOnError = Color(0xFF690005)
val HikariDarkOutline = Color(0xFF343746)
val HikariDarkWatchedSuccess = Color(0xFF7DD8A0)

// Light Palette (Sakura Day & Crisp Indigo)
val HikariLightPrimary = Color(0xFF4353C7)
val HikariLightOnPrimary = Color(0xFFFFFFFF)
val HikariLightPrimaryContainer = Color(0xFFDCE1FF)
val HikariLightOnPrimaryContainer = Color(0xFF0D174B)

val HikariLightSecondary = Color(0xFFB04A72)
val HikariLightOnSecondary = Color(0xFFFFFFFF)
val HikariLightSecondaryContainer = Color(0xFFFFD8E4)
val HikariLightOnSecondaryContainer = Color(0xFF3E001E)

val HikariLightBackground = Color(0xFFFAF9FC)
val HikariLightSurface = Color(0xFFFFFFFF)
val HikariLightSurfaceVariant = Color(0xFFECEAF2)
val HikariLightOnBackground = Color(0xFF1A1B20)
val HikariLightOnSurface = Color(0xFF1A1B20)
val HikariLightOnSurfaceVariant = Color(0xFF49454F)
val HikariLightError = Color(0xFFBA1A1A)
val HikariLightOnError = Color(0xFFFFFFFF)
val HikariLightOutline = Color(0xFFCAC4D0)
val HikariLightWatchedSuccess = Color(0xFF2E7D4F)

// Poster Scrim & Branded Gradient Tokens
val ScrimTransparent = Color(0x00000000)
val ScrimDark60 = Color(0x99000000)
val ScrimDark85 = Color(0xD90E0F13)

@Immutable
data class HikariExtendedColors(
    val watchedSuccess: Color,
    val scrimStart: Color = ScrimTransparent,
    val scrimEnd: Color = ScrimDark60
)

val LocalHikariColors = staticCompositionLocalOf {
    HikariExtendedColors(watchedSuccess = HikariDarkWatchedSuccess)
}
