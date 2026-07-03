package com.example.monday.ui.modern

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Animated number counter that rapidly counts up/down to the target value.
 *
 * Uses animateIntAsState so the displayed number smoothly
 * increments/decrements through intermediate values (e.g. 647 → 1853
 * shows 647, 700, 800, ... 1800, 1853). This creates a premium
 * counting animation used in fintech apps.
 *
 * Simple, anti-fragile, zero-risk of invisible rendering.
 */
@Composable
fun RollingNumber(
    targetValue: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 42.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    fontFamily: FontFamily? = null,
    color: Color = ModernColors.EggnogDark,
    letterSpacing: TextUnit = (-1.5).sp,
    prefix: String = "₹"
) {
    // Smoothly animate the integer value from current to target
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "spend_counter"
    )

    Text(
        text = "$prefix${com.example.monday.core.utils.formatIndianCurrency(animatedValue)}",
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        lineHeight = fontSize * 1.05f,
        modifier = modifier
    )
}
