package com.example.monday.ui.modern

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.monday.core.utils.formatIndianCurrency
import com.example.monday.ui.modern.ModernColors as C
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Monthly cumulative line chart with slow point-by-point progressive animation.
 *
 * Animation approach: animatedDayCount (float) goes from 0 → totalDays.
 * At each integer value, a new day-point "arrives" and the line extends to it.
 * Between integers, the line interpolates smoothly to the next point.
 * This creates a slow "stock ticker" feel where you watch spending build day by day.
 *
 * Anti-fragile:
 *  - Div-by-zero guarded (maxCumulative ≥ 1.0)
 *  - Cap at 31 days max
 *  - Empty month renders placeholder
 *  - Re-animates only on month change, not each date tap
 */
@Composable
fun MonthlyCumulativeChart(
    dailySpend: Map<LocalDate, Double>,
    selectedDate: LocalDate,
    isCompact: Boolean = false
) {
    // Build daily data: day 1 → selectedDate
    val dailyData = remember(selectedDate, dailySpend) {
        val firstDay = selectedDate.withDayOfMonth(1)
        val dayCount = selectedDate.dayOfMonth.coerceIn(1, 31)
        (0 until dayCount).map { offset ->
            val day = firstDay.plusDays(offset.toLong())
            val spend = dailySpend[day] ?: 0.0
            day to spend
        }
    }

    // Calculate total spend strictly up to the selected date (progressive sum)
    val totalMonthSpend = dailyData.sumOf { it.second }
    // Find the single highest day to set the Y-axis height. Coerce to at least 1.0 to prevent div-by-zero.
    val maxDaily = dailyData.maxOfOrNull { it.second }?.takeIf { it > 0.0 } ?: 1.0
    val totalPoints = dailyData.size

    // Track whether the month-level animation has completed at least once
    var monthAnimationDone by remember { mutableStateOf(false) }

    val monthKey = "${selectedDate.year}-${selectedDate.monthValue}"
    val animatedDayCount = remember { Animatable(0f) }
    // Ease-in: slow start → fast finish. Mirrors how spending accumulates.
    val easeIn = remember { CubicBezierEasing(0.42f, 0f, 1f, 1f) }

    // 1. Month change → full restart animation (snap to 0, animate to totalPoints)
    LaunchedEffect(monthKey) {
        monthAnimationDone = false
        animatedDayCount.snapTo(0f) // Reset on month change
        if (totalPoints > 0) {
            val baseDurationMs = 80f
            val speedIncreasePerDayMs = 1f
            val durationPerDayMs = baseDurationMs - (totalPoints - 1) * speedIncreasePerDayMs
            val calculatedDuration = totalPoints * durationPerDayMs
            val finalDuration = calculatedDuration.toInt().coerceIn(300, 2500)

            animatedDayCount.animateTo(
                targetValue = totalPoints.toFloat(),
                animationSpec = tween(
                    durationMillis = finalDuration,
                    easing = easeIn
                )
            )
        }
        monthAnimationDone = true
    }

    // 2. Day change within same month → smooth extension/retraction (no restart from 0)
    //    Only fires after the month animation has run at least once, so it doesn't
    //    race with the month-level animation on initial composition.
    LaunchedEffect(totalPoints) {
        if (monthAnimationDone && animatedDayCount.value > 0f) {
            // Smoothly extend or retract the line to the new day count
            animatedDayCount.animateTo(
                targetValue = totalPoints.toFloat(),
                animationSpec = tween(
                    durationMillis = 200,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            )
        }
    }

    // Month/Year title for standalone mode
    val monthTitle = remember(selectedDate) {
        val month = selectedDate.month.getDisplayName(JavaTextStyle.FULL, Locale.ENGLISH)
        "$month ${selectedDate.year}"
    }

    Box(
        modifier = Modifier
            .then(if (isCompact) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .background(C.SoftCream, RoundedCornerShape(14.dp))
            .border(1.dp, C.CardBorder, RoundedCornerShape(14.dp))
            .then(
                if (isCompact) Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp)
                else Modifier.padding(14.dp)
            )
    ) {
        // Always show the chart — even when spend is ₹0, a flat line at zero
        // is more informative than "No spend this month" (month may have
        // expenses on later days the user hasn't scrolled to yet).
        Column(modifier = if (isCompact) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            // Title row with total badge
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = if (isCompact) 4.dp else 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isCompact) "Monthly Spending" else "$monthTitle Spending",
                    color = C.EggnogDark.copy(alpha = 0.85f),
                    fontSize = if (isCompact) 11.sp else 12.sp,
                    fontWeight = if (isCompact) FontWeight.Bold else FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                )
                Box(
                    modifier = Modifier
                        .background(C.DateUnselected, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    RollingNumber(
                        targetValue = totalMonthSpend.toInt(),
                        color = C.EggnogDark,
                        fontSize = if (isCompact) 10.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 6.dp))

            if (isCompact) {
                // Compact: just the canvas, fills pager height
                ProgressiveLineCanvas(
                    chartData = dailyData,
                    maxYValue = maxDaily,
                    totalDaysInMonth = selectedDate.lengthOfMonth(),
                    animatedDayCount = animatedDayCount.value,
                    showGrid = false,
                    showDayDots = false,
                    leftPadding = 0f,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                // Standalone: Y-axis labels + chart + X-axis labels
                val yAxisWidth = 42.dp

                Row(modifier = Modifier.fillMaxWidth()) {
                    // Y-axis labels (based on the highest SINGLE DAY spend)
                    Column(
                        modifier = Modifier.width(yAxisWidth).height(100.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatAxisLabel(maxDaily), color = C.EggnogDark.copy(alpha = 0.45f), fontSize = 8.sp, fontWeight = FontWeight.Medium, lineHeight = 9.sp)
                        Text(formatAxisLabel(maxDaily / 2.0), color = C.EggnogDark.copy(alpha = 0.35f), fontSize = 8.sp, fontWeight = FontWeight.Medium, lineHeight = 9.sp)
                        Text("₹0", color = C.EggnogDark.copy(alpha = 0.3f), fontSize = 8.sp, fontWeight = FontWeight.Medium, lineHeight = 9.sp)
                    }
                    // Chart canvas with grid + day dots
                    ProgressiveLineCanvas(
                        chartData = dailyData,
                        maxYValue = maxDaily,
                        totalDaysInMonth = selectedDate.lengthOfMonth(),
                        animatedDayCount = animatedDayCount.value,
                        showGrid = true,
                        showDayDots = true,
                        leftPadding = 4f,
                        modifier = Modifier.weight(1f).height(100.dp)
                    )
                }

                // X-axis day labels
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = yAxisWidth),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    buildSmartXLabels(selectedDate.dayOfMonth, selectedDate).forEach { (dayNum, isHighlighted) ->
                        Text(
                            "$dayNum",
                            color = if (isHighlighted) C.DateSelected else C.EggnogDark.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Progressive line canvas: draws cumulative curve point-by-point.
 *
 * [animatedDayCount] is a float from 0 → totalPoints.
 * - Integer part = how many full day-points are visible
 * - Fractional part = interpolation progress toward the NEXT point
 *
 * This creates a smooth "point arrives, line extends, next point arrives" effect.
 */
@Composable
private fun ProgressiveLineCanvas(
    chartData: List<Pair<LocalDate, Double>>,
    maxYValue: Double,
    totalDaysInMonth: Int,
    animatedDayCount: Float,
    showGrid: Boolean,
    showDayDots: Boolean,
    leftPadding: Float,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {
        if (chartData.isEmpty()) return@Canvas

        val w = size.width - leftPadding
        val h = size.height
        val padTop = 6f
        val padBot = 6f
        val drawH = h - padTop - padBot

        // Grid lines
        if (showGrid) {
            val gridColor = C.CardBorder.copy(alpha = 0.4f)
            listOf(0f, 0.5f, 1f).forEach { frac ->
                val y = padTop + drawH * (1f - frac)
                drawLine(gridColor, Offset(leftPadding, y), Offset(leftPadding + w, y), 0.8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
            }
        }

        // All possible pixel positions for each day
        val allPoints = chartData.map { (date, value) ->
            // Use date.dayOfMonth and totalDaysInMonth so the X position is proportionate to the whole month
            val dayIndex = date.dayOfMonth - 1
            // Ensure divisor is at least 1 to avoid division by zero (e.g. 1-day months, unlikely but safe)
            val maxDayIndex = (totalDaysInMonth - 1).coerceAtLeast(1)
            val x = leftPadding + (dayIndex.toFloat() / maxDayIndex) * w
            val y = padTop + drawH * (1f - (value / maxYValue).toFloat())
            Offset(x, y)
        }

        // How many points are currently visible based on animation progress
        val visibleFullPoints = animatedDayCount.toInt().coerceIn(0, allPoints.size)
        val fractional = animatedDayCount - visibleFullPoints // 0..1 progress toward next point

        // Build the visible points list (including interpolated tip)
        val visiblePoints = mutableListOf<Offset>()
        for (i in 0 until visibleFullPoints.coerceAtMost(allPoints.size)) {
            visiblePoints.add(allPoints[i])
        }

        // Interpolate the "in-progress" next point (the moving tip)
        if (visibleFullPoints < allPoints.size && fractional > 0f && visiblePoints.isNotEmpty()) {
            val from = allPoints[visibleFullPoints - 1]
            val to = allPoints[visibleFullPoints]
            val interpX = from.x + (to.x - from.x) * fractional
            val interpY = from.y + (to.y - from.y) * fractional
            visiblePoints.add(Offset(interpX, interpY))
        }

        if (visiblePoints.size < 2) return@Canvas // Need 2+ points for a line

        // Build smooth bezier path through visible points
        val linePath = Path().apply {
            moveTo(visiblePoints.first().x, visiblePoints.first().y)
            for (i in 1 until visiblePoints.size) {
                val prev = visiblePoints[i - 1]
                val curr = visiblePoints[i]
                val cx1 = prev.x + (curr.x - prev.x) / 3f
                val cx2 = prev.x + 2f * (curr.x - prev.x) / 3f
                cubicTo(cx1, prev.y, cx2, curr.y, curr.x, curr.y)
            }
        }

        // Gradient fill under curve
        val lastVisible = visiblePoints.last()
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(lastVisible.x, h)
            lineTo(visiblePoints.first().x, h)
            close()
        }
        drawPath(fillPath, brush = Brush.verticalGradient(
            listOf(C.DateSelected.copy(alpha = 0.22f), C.DateSelected.copy(alpha = 0.02f)),
            startY = 0f, endY = h
        ))

        // Stroke the line
        drawPath(linePath, color = C.DateSelected,
            style = Stroke(width = if (showGrid) 2.8f else 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Day dots: small circles at each fully-arrived day point
        if (showDayDots) {
            for (i in 0 until visibleFullPoints.coerceAtMost(allPoints.size)) {
                val pt = allPoints[i]
                drawCircle(color = C.DateSelected.copy(alpha = 0.3f), radius = 2.5f, center = pt)
            }
        }

        // Moving endpoint dot — the "head" of the progressing line
        drawCircle(color = Color.White, radius = if (showGrid) 6f else 5f, center = lastVisible)
        drawCircle(color = C.DateSelected, radius = if (showGrid) 4f else 3.5f, center = lastVisible)
    }
}

/** Smart X-axis labels: 1, intermediate markers, current day (highlighted). */
private fun buildSmartXLabels(totalDays: Int, selectedDate: LocalDate): List<Pair<Int, Boolean>> {
    if (totalDays <= 1) return listOf(1 to true)
    if (totalDays <= 3) return (1..totalDays).map { it to (it == totalDays) }
    val labels = mutableListOf<Pair<Int, Boolean>>()
    labels.add(1 to false)
    val step = when {
        totalDays <= 10 -> 3
        totalDays <= 20 -> 5
        else -> 7
    }
    var marker = step + 1
    while (marker < totalDays - 1) {
        labels.add(marker to false)
        marker += step
    }
    labels.add(totalDays to true)
    return labels
}

/** Y-axis label formatter: ₹1.2K, ₹50K, ₹1.5L etc. */
private fun formatAxisLabel(value: Double): String = when {
    value >= 100000 -> "₹${String.format("%.1f", value / 100000)}L"
    value >= 1000 -> "₹${String.format("%.0f", value / 1000)}K"
    value > 0 -> "₹${value.toInt()}"
    else -> "₹0"
}
