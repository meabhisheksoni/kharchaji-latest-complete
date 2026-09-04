package com.example.monday.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.monday.TodoViewModel
import com.example.monday.core.utils.SpendTier
import com.example.monday.core.utils.calculateMonthSpendRange
import com.example.monday.core.utils.formatCompactAmount
import com.example.monday.ui.modern.ModernColors
import com.example.monday.viewmodels.StatsViewModel
import kotlinx.coroutines.CancellationException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CustomCalendarView(
    currentCalendarMonth: YearMonth,
    onDateSelected: (LocalDate) -> Unit,
    selectedDate: LocalDate,
    @Suppress("UNUSED_PARAMETER") todoViewModel: TodoViewModel,
    statsViewModel: StatsViewModel,
    onMonthChanged: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val daysInGrid = remember(currentCalendarMonth) {
        getDaysInMonthGrid(currentCalendarMonth)
    }

    var masterRecordTotals by remember(currentCalendarMonth) {
        mutableStateOf<Map<String, Double>>(emptyMap())
    }

    // Adaptive 3-tier dynamic spending classification based on month's average/distribution
    val spendRangeConfig = remember(masterRecordTotals) {
        calculateMonthSpendRange(masterRecordTotals)
    }

    // Defensive, cancellation-aware data fetching
    LaunchedEffect(currentCalendarMonth) {
        try {
            val totals = statsViewModel.getMasterRecordDailyTotalsForMonth(currentCalendarMonth)
            masterRecordTotals = totals
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CustomCalendarView", "Error fetching master totals for $currentCalendarMonth", e)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp) // Aligned with 16dp Guide Rail
    ) {
        // Month Navigation Header
        CalendarHeader(
            currentMonth = currentCalendarMonth,
            onMonthChanged = onMonthChanged
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Days of the Week Header (Sunday -> Saturday)
        DaysOfWeekHeader()

        Spacer(modifier = Modifier.height(6.dp))

        // Bounded LazyVerticalGrid (prevents infinite height crash in scrollable screens)
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp),
            userScrollEnabled = false, // Avoids nested scroll gesture conflicts
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(daysInGrid) { day ->
                if (day != null) {
                    val total = masterRecordTotals[day.toString()]
                    val tier = if (total != null && total > 0) spendRangeConfig.getTier(total) else SpendTier.NONE
                    DayCell(
                        date = day,
                        isSelected = day == selectedDate,
                        isToday = day == LocalDate.now(),
                        masterTotal = total,
                        spendTier = tier,
                        onClick = { onDateSelected(day) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp) // Accessible 48dp Mobile Touch Target
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarHeader(
    currentMonth: YearMonth,
    onMonthChanged: (YearMonth) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Tactile 44×44dp Previous Button with 10dp rounded corners
        Surface(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onMonthChanged(currentMonth.minusMonths(1)) },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, ModernColors.CardBorder.copy(alpha = 0.5f))
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

        Text(
            text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Tactile 44×44dp Next Button with 10dp rounded corners
        Surface(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onMonthChanged(currentMonth.plusMonths(1)) },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, ModernColors.CardBorder.copy(alpha = 0.5f))
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
}

@Composable
fun DaysOfWeekHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Explicit Sunday -> Saturday order (matches % 7 grid alignment)
        val orderedDays = listOf(
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY
        )

        orderedDays.forEach { dayOfWeek ->
            Text(
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2).uppercase(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = ModernColors.EggnogDark
            )
        }
    }
}

@Composable
fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    masterTotal: Double?,
    spendTier: SpendTier = SpendTier.NONE,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isToday -> ModernColors.TodayButton.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderStroke = when {
        isToday -> BorderStroke(1.2.dp, ModernColors.TodayButton)
        else -> BorderStroke(0.5.dp, ModernColors.CardBorder.copy(alpha = 0.25f))
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        border = borderStroke
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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

            if (masterTotal != null && masterTotal > 0) {
                // Rectangular highlighted badge with 3-tier color distinction
                val (badgeBg, badgeBorder, badgeText) = when (spendTier) {
                    SpendTier.LOW -> Triple(
                        Color(0xFF43A047).copy(alpha = 0.15f), // Green tint
                        Color(0xFF43A047).copy(alpha = 0.40f),
                        Color(0xFF2E7D32)
                    )
                    SpendTier.MEDIUM -> Triple(
                        Color(0xFFFFA726).copy(alpha = 0.18f), // Mild Orange tint
                        Color(0xFFFFA726).copy(alpha = 0.45f),
                        Color(0xFFE65100)
                    )
                    SpendTier.HIGH -> Triple(
                        Color(0xFFE53935).copy(alpha = 0.15f), // Red tint
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
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(badgeBg)
                        .border(0.5.dp, badgeBorder, RoundedCornerShape(3.dp))
                        .padding(vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatCompactAmount(masterTotal),
                        color = badgeText,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

fun getDaysInMonthGrid(yearMonth: YearMonth): List<LocalDate?> {
    val firstDayOfMonth = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    // Sunday = 0, Monday = 1, ..., Saturday = 6
    val firstDayOfWeekIndex = (firstDayOfMonth.dayOfWeek.value % 7)

    val daysList = mutableListOf<LocalDate?>()
    repeat(firstDayOfWeekIndex) {
        daysList.add(null)
    }
    for (day in 1..daysInMonth) {
        daysList.add(yearMonth.atDay(day))
    }
    return daysList
}
