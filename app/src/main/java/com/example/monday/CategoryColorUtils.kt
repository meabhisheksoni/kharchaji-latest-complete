package com.example.monday

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

fun generateCategoryColors(categories: List<String>): Map<String, Color> {
    val colors = mutableMapOf<String, Color>()
    categories.forEachIndexed { index, category ->
        colors[category] = generateColor(index)
    }
    return colors
}

private fun generateColor(index: Int): Color {
    val hue = (index * 137.5f) % 360f
    return Color.hsl(hue, 0.7f, 0.5f)
} 