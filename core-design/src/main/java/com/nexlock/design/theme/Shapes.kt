package com.nexlock.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val NexShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

object NexRadius {
    val chip = RoundedCornerShape(50)
    val card = RoundedCornerShape(24.dp)
    val hero = RoundedCornerShape(32.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
