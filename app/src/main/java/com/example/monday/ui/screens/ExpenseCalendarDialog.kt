package com.example.monday.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.monday.TodoViewModel
import com.example.monday.core.utils.SpendTier
import com.example.monday.core.utils.calculateMonthSpendRange
import com.example.monday.core.utils.formatCompactAmount
import com.example.monday.ui.modern.ModernColors
import com.example.monday.viewmodels.StatsViewModel
import kotlinx.coroutines.CancellationException
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun ExpenseCalendarDialog(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    @Suppress("UNUSED_PARAMETER") todoViewModel: TodoViewModel,
    statsViewModel: StatsViewModel
) {
    var currentYearMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var masterTotalsMap by remember(currentYearMonth) { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var monthlyTotal by remember(currentYearMonth) { mutableStateOf(0.0) }
    var showMediumRange by remember { mutableStateOf(false) }

    // Adaptive 3-tier dynamic spending classification
    val spendRangeConfig = remember(masterTotalsMap) {
        calculateMonthSpendRange(masterTotalsMap)
    }

    // Cancellation-aware data fetching
    LaunchedEffect(currentYearMonth) {
        try {
            val totals = statsViewModel.getMasterRecordDailyTotalsForMonth(currentYearMonth)
            masterTotalsMap = totals
            monthlyTotal = totals.values.sum()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ExpenseCalendarDialog", "Error loading daily totals for $currentYearMonth", e)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Scrim Container: Full screen with tap-to-dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .padding(horizontal = 4.dp, vertical = 24.dp), // Negligible 4dp padding: edge-to-edge while showing curves
            contentAlignment = Alignment.Center
        ) {
            // Calendar Card: Consumes touches to prevent dismissing when clicking inside
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = true
                    ) { /* Intentionally empty: consume touch within card */ },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, ModernColors.CardBorder.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 6.dp, vertical = 12.dp)
                ) {
                    // Header: Month Navigation + Deduplicated Monthly Total Spend
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { currentYearMonth = currentYearMonth.minusMonths(1) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, ModernColors.CardBorder.copy(alpha = 0.4f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous Month",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (monthlyTotal > 0) {
                                Text(
                                    text = "Total: ${formatIndianCurrency(monthlyTotal)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ModernColors.EggnogDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { currentYearMonth = currentYearMonth.plusMonths(1) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, ModernColors.CardBorder.copy(alpha = 0.4f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next Month",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = ModernColors.CardBorder.copy(alpha = 0.25f),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    // Weekday Headers (Sunday -> Saturday)
                    val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (dayLabel in daysOfWeek) {
                            Text(
                                text = dayLabel,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = ModernColors.EggnogDark,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Calendar Grid with tight content wrapping
                    val firstDayOfMonth = currentYearMonth.atDay(1)
                    val daysInMonth = currentYearMonth.lengthOfMonth()
                    val firstDayOfWeekIndex = (firstDayOfMonth.dayOfWeek.value % 7)
                    val totalDays = firstDayOfWeekIndex + daysInMonth
                    val totalRows = (totalDays + 6) / 7

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        for (row in 0 until totalRows) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                            ) {
                                for (column in 0 until 7) {
                                    val day = row * 7 + column - firstDayOfWeekIndex + 1
                                    if (day in 1..daysInMonth) {
                                        val date = currentYearMonth.atDay(day)
                                        val isSelected = date == selectedDate
                                        val isToday = date == LocalDate.now()
                                        val total = masterTotalsMap[date.toString()]
                                        val tier = if (total != null && total > 0) {
                                            spendRangeConfig.getTier(total)
                                        } else {
                                            SpendTier.NONE
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .padding(1.dp)
                                        ) {
                                            MonthDayCell(
                                                date = date,
                                                isSelected = isSelected,
                                                isToday = isToday,
                                                dailyTotal = total,
                                                spendTier = tier,
                                                onClick = { onDateSelected(date) }
                                            )
                                        }
                                    } else {
                                        Spacer(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bottom Row: Small Triangle/Triad Toggle Button for Medium Range
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showMediumRange = !showMediumRange }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.Transparent
                        ) {
                            // 3-dot triangle / triad arrangement
                            TriadDotsIndicator(
                                isExpanded = showMediumRange,
                                color = ModernColors.EggnogDark
                            )
                        }

                        // Expandable Moderate Spend Range Pill
                        AnimatedVisibility(
                            visible = showMediumRange,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            val lowFormatted = formatCompactAmount(spendRangeConfig.lowThreshold)
                            val highFormatted = formatCompactAmount(spendRangeConfig.highThreshold)
                            
                            Surface(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                color = Color(0xFFFFA726).copy(alpha = 0.14f),
                                border = BorderStroke(0.5.dp, Color(0xFFFFA726).copy(alpha = 0.40f)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Moderate Spend: $lowFormatted – $highFormatted",
                                    color = Color(0xFFE65100),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    dailyTotal: Double?,
    spendTier: SpendTier,
    onClick: () -> Unit
) {
    // Cell background stays clean and un-flooded
    val cellBackgroundColor = when {
        isToday -> ModernColors.TodayButton.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    // Outline border only for today or subtle card border (no blue tile flood)
    val borderStroke = when {
        isToday -> BorderStroke(1.2.dp, ModernColors.TodayButton)
        else -> BorderStroke(0.5.dp, ModernColors.CardBorder.copy(alpha = 0.20f))
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = cellBackgroundColor,
        border = borderStroke
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 3.dp, horizontal = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Date number: Selected state applies a neat rectangular highlight to the date text ONLY
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(ModernColors.DateSelected)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        lineHeight = 13.sp
                    )
                }
            } else {
                Box(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (isToday) ModernColors.EggnogDark else MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.5.sp,
                        lineHeight = 13.sp
                    )
                }
            }

            // Spending amount badge: strictly preserves Green / Mild Orange / Red tier badge
            if (dailyTotal != null && dailyTotal > 0) {
                val (badgeBg, badgeBorder, badgeText) = when (spendTier) {
                    SpendTier.LOW -> Triple(
                        Color(0xFF43A047).copy(alpha = 0.15f), // Green
                        Color(0xFF43A047).copy(alpha = 0.40f),
                        Color(0xFF2E7D32)
                    )
                    SpendTier.MEDIUM -> Triple(
                        Color(0xFFFFA726).copy(alpha = 0.18f), // Mild Orange
                        Color(0xFFFFA726).copy(alpha = 0.45f),
                        Color(0xFFE65100)
                    )
                    SpendTier.HIGH -> Triple(
                        Color(0xFFE53935).copy(alpha = 0.15f), // Red
                        Color(0xFFE53935).copy(alpha = 0.40f),
                        Color(0xFFD32F2F)
                    )
                    SpendTier.NONE -> Triple(
                        Color.Transparent,
                        Color.Transparent,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(badgeBg)
                        .border(0.5.dp, badgeBorder, RoundedCornerShape(3.dp))
                        .padding(vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatCompactAmount(dailyTotal),
                        color = badgeText,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

/**
 * Tactical 3-dot triangle indicator button:
 * Top: 1 dot
 * Bottom: 2 dots
 */
@Composable
private fun TriadDotsIndicator(
    isExpanded: Boolean,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Top dot
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (isExpanded) Color(0xFFFFA726) else color.copy(alpha = 0.6f))
        )
        // Bottom two dots
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isExpanded) Color(0xFFFFA726) else color.copy(alpha = 0.6f))
            )
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isExpanded) Color(0xFFFFA726) else color.copy(alpha = 0.6f))
            )
        }
    }
}

private fun formatIndianCurrency(amount: Double): String {
    val formatter = DecimalFormat("₹#,##,##0")
    return formatter.format(amount.toInt())
}
