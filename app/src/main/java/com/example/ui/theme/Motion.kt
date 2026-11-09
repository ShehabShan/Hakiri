package com.example.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn

object HikariMotion {
    const val NavDurationMs = 220
    const val ControlsFadeDurationMs = 150

    fun navEnterTransition(): EnterTransition =
        fadeIn(animationSpec = tween(NavDurationMs)) +
            scaleIn(initialScale = 0.98f, animationSpec = tween(NavDurationMs))

    fun navExitTransition(): ExitTransition =
        fadeOut(animationSpec = tween(NavDurationMs))

    val favoriteBounceSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
