package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val HikariShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),      // Posters: 10 dp
    medium = RoundedCornerShape(12.dp),     // Cards: 12 dp
    large = RoundedCornerShape(20.dp),      // Bottom sheets / dialogs: 20 dp
    extraLarge = RoundedCornerShape(50)     // Pills / chips / buttons
)

val PosterCornerShape = RoundedCornerShape(10.dp)
val CardCornerShape = RoundedCornerShape(12.dp)
val PillCornerShape = RoundedCornerShape(percent = 50)
val SheetTopCornerShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
