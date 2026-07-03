package com.example.monday.ui.modern

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.monday.R
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import com.example.monday.core.utils.formatIndianCurrency
import com.example.monday.ui.modern.ModernColors as C

/**
 * Hero dashboard showing total spend, budget progress, and stat cards / daily spend graph.
 */
@Composable
fun HeroDashboard(
    totalSum: Double, checkedSum: Double,
    totalItemsCount: Int, checkedItemsCount: Int,
    avgPerExpense: Double, topCategory: String,
    progressPercent: Double, budgetLimit: Double,
    showSelected: Boolean,
    onToggleShowSelected: (Boolean) -> Unit,
    selectedDate: LocalDate,
    dashboardViewMode: String,
    dailySpend: Map<LocalDate, Double>,
    monthlySpend: Map<LocalDate, Double>,
    dateBarPosition: String
) {
    val displayValue = if (showSelected && checkedItemsCount > 0) checkedSum else totalSum
    val displayLabel = if (showSelected && checkedItemsCount > 0) "Selected Spend" else "Total Spend"
    val isToday = selectedDate == LocalDate.now()

    val satoshiFontFamily = remember {
        FontFamily(Font(R.font.satoshi_bold, FontWeight.Bold))
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        // AI Insight Chip
        Box(
            modifier = Modifier
                .background(C.ChipBg, RoundedCornerShape(50))
                .border(1.dp, C.ChipBorder, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "✨ ${if (isToday) "Today's Overview" else "Past Overview"} · Top: $topCategory",
                color = C.ChipText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.4.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Giant spend amount — odometer rolling animation
        RollingNumber(
            targetValue = displayValue.toInt(),
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = satoshiFontFamily,
            color = C.EggnogDark,
            letterSpacing = (-1.5).sp
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Label row + comparison badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = displayLabel, color = C.EggnogDark, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(C.Groceries.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .border(1.dp, C.Groceries.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(text = "↓ vs ₹524 avg", color = C.Groceries, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Daily Budget progress bar
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Daily Budget", color = C.EggnogDark.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "₹${formatIndianCurrency(totalSum.toInt())} / ₹${formatIndianCurrency(budgetLimit.toInt())}",
                color = if (progressPercent > 80) C.Transport else C.EggnogDark,
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Animated progress bar
        val animatedProgress by animateFloatAsState(
            targetValue = (progressPercent / 100.0).toFloat().coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            label = "progress"
        )
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(C.SoftCream, RoundedCornerShape(50))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(if (progressPercent > 80) C.Transport else C.Groceries, RoundedCornerShape(50))
            )
        }

        // Swipeable Stats / Graph section
        Spacer(modifier = Modifier.height(16.dp))

        when (dashboardViewMode) {
            "cards" -> {
                StatCardsRow(totalItemsCount, avgPerExpense, topCategory)
            }
            "graph" -> {
                // Standalone graph mode — full labels, normal padding (DO NOT CHANGE THIS VIEW)
                DailySpendChart(dailySpend, selectedDate, dateBarPosition = dateBarPosition, isCompact = false)
            }
            "monthly" -> {
                // Standalone monthly cumulative mode — full labels, normal padding
                MonthlyCumulativeChart(monthlySpend, selectedDate, isCompact = false)
            }
            else -> {
                // "both" — HorizontalPager with 3 pages (cards, 7-day graph, monthly graph), height synced from page 0
                val density = LocalDensity.current
                var measuredHeightPx by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
                val pagerState = rememberPagerState(pageCount = { 3 })

                // Calculate height: use measured height from stat cards, fallback to 80.dp
                val pagerHeight = if (measuredHeightPx > 0) {
                    with(density) { measuredHeightPx.toDp() }
                } else {
                    80.dp // Safe fallback to prevent collapsed pager on first frame
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp) // Guard: never collapse below this
                        .then(if (measuredHeightPx > 0) Modifier.height(pagerHeight) else Modifier),
                    pageSpacing = 8.dp
                ) { page ->
                    when (page) {
                        0 -> Box(
                            modifier = Modifier.onSizeChanged { size ->
                                // Capture stat cards height to sync graph page
                                if (size.height > 0 && size.height != measuredHeightPx) {
                                    measuredHeightPx = size.height
                                }
                            }
                        ) {
                            StatCardsRow(totalItemsCount, avgPerExpense, topCategory)
                        }
                        1 -> Box(
                            modifier = Modifier.fillMaxSize() // Fill to matched height
                        ) {
                            // Compact: no day labels, tighter padding — aligned with date strip
                            DailySpendChart(dailySpend, selectedDate, dateBarPosition = dateBarPosition, isCompact = true)
                        }
                        2 -> Box(
                            modifier = Modifier.fillMaxSize() // Fill to matched height
                        ) {
                            // Compact monthly cumulative chart — aligned with date strip
                            MonthlyCumulativeChart(monthlySpend, selectedDate, isCompact = true)
                        }
                    }
                }

                // Page indicator dots
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(3) { index ->
                        val isActive = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isActive) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) C.DateSelected
                                    else C.EggnogLight.copy(alpha = 0.5f)
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * The 3 stat cards in a row — extracted for reuse in pager and standalone mode.
 */
@Composable
private fun StatCardsRow(totalItemsCount: Int, avgPerExpense: Double, topCategory: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(Modifier.weight(1f), "📊", totalItemsCount.toString(), "Transactions", C.Utilities)
        StatCard(Modifier.weight(1f), "📈", "₹${formatIndianCurrency(avgPerExpense.toInt())}", "Avg / Item", C.Transport)
        StatCard(Modifier.weight(1f), "🏷️", topCategory.take(7), "Top Category", C.DateText)
    }
}

/**
 * Daily spend vertical bar chart — renders bars for the last 7 days of spend.
 * Replaces the old category-based MiniSpendingChart.
 * Division-by-zero is guarded with maxOf(1.0).
 * Empty state shows a safe placeholder instead of crashing.
 */
@Composable
private fun DailySpendChart(
    dailySpend: Map<LocalDate, Double>,
    selectedDate: LocalDate,
    dateBarPosition: String = "mid",
    isCompact: Boolean = false
) {
    // Sync 7-day window with dateBarPosition so graph bars align with date strip
    val last7Days = remember(selectedDate, dateBarPosition) {
        when (dateBarPosition) {
            "start" -> (0..6).map { selectedDate.plusDays(it.toLong()) }   // selected = first bar
            "end"   -> (-6..0).map { selectedDate.plusDays(it.toLong()) }  // selected = last bar
            else    -> (-3..3).map { selectedDate.plusDays(it.toLong()) }  // centered (default)
        }
    }

    val total7DaySpend = remember(selectedDate, dailySpend) {
        val last7DaysFromSelected = (0..6).map { selectedDate.minusDays(it.toLong()) }
        last7DaysFromSelected.sumOf { dailySpend[it] ?: 0.0 }
    }

    Box(
        modifier = Modifier
            .then(
                // Compact: fill pager height so card matches stat cards height
                if (isCompact) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
            )
            .background(C.SoftCream, RoundedCornerShape(12.dp))
            .border(1.dp, C.CardBorder, RoundedCornerShape(12.dp))
            // Compact: tiny top padding to eliminate vacant space above title
            .then(
                if (isCompact) Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp)
                else Modifier.padding(14.dp)
            )
    ) {
        val hasAnyData = last7Days.any { (dailySpend[it] ?: 0.0) > 0.0 }

        if (!hasAnyData) {
            // Empty state — no spend data at all
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📊", fontSize = 24.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "No spend data yet",
                    color = C.EggnogDark.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Column(modifier = if (isCompact) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = if (isCompact) 4.dp else 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Last 7-days spending",
                        color = C.EggnogDark.copy(alpha = 0.7f),
                        fontSize = if (isCompact) 11.sp else 12.sp,
                        fontWeight = if (isCompact) FontWeight.Bold else FontWeight.SemiBold,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        letterSpacing = 0.4.sp
                    )

                    if (hasAnyData || total7DaySpend > 0) {
                        RollingNumber(
                            targetValue = total7DaySpend.toInt(),
                            color = C.EggnogDark,
                            fontSize = if (isCompact) 10.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp
                        )
                    }
                }
                // Compact: minimal gap before bars; normal: breathing room
                Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 10.dp))

                // Division-by-zero kill-switch: never let maxValue be 0
                val maxValue = last7Days.maxOfOrNull { dailySpend[it] ?: 0.0 }
                    ?.takeIf { it > 0.0 } ?: 1.0

                // Bar chart - vertical bars
                // Compact: bars expand to fill all remaining space via weight(1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isCompact) Modifier.weight(1f) else Modifier.height(50.dp)),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    last7Days.forEachIndexed { index, day ->
                        val spend = dailySpend[day] ?: 0.0
                        val barFraction = (spend / maxValue).toFloat().coerceIn(0f, 1f)
                        val isSelected = day == selectedDate

                        val animatedFraction by animateFloatAsState(
                            targetValue = if (spend > 0) barFraction.coerceAtLeast(0.05f) else 0f,
                            animationSpec = tween(
                                durationMillis = 500,
                                delayMillis = index * 60,
                                easing = FastOutSlowInEasing
                            ),
                            label = "bar_$day"
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Bar
                            if (animatedFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .width(14.dp)
                                        .fillMaxHeight(animatedFraction)
                                        .background(
                                            if (isSelected) C.DateSelected
                                            else C.Groceries.copy(alpha = 0.6f),
                                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                        )
                                )
                            } else {
                                // Empty placeholder — flat line for zero-spend day
                                Box(
                                    modifier = Modifier
                                        .width(14.dp)
                                        .height(2.dp)
                                        .background(
                                            C.EggnogLight.copy(alpha = 0.3f),
                                            RoundedCornerShape(1.dp)
                                        )
                                )
                            }
                        }
                    }
                }

                // Day labels below bars — hidden in compact (pager "both") mode
                if (!isCompact) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        last7Days.forEach { day ->
                            val isSelected = day == selectedDate
                            Text(
                                text = day.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).take(1),
                                color = if (isSelected) C.DateSelected else C.EggnogDark.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ToggleTab(text: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) C.EggnogLight else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text, color = C.EggnogDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.graphicsLayer { alpha = if (isActive) 1f else 0.6f }
        )
    }
}

@Composable
private fun StatCard(modifier: Modifier, icon: String, value: String, label: String, valueColor: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = modifier
            .background(C.SoftCream, RoundedCornerShape(12.dp))
            .border(1.dp, C.CardBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, color = C.EggnogDark.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}
