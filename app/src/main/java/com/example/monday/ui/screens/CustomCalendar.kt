package com.example.monday.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.monday.TodoViewModel
import com.example.monday.CalculationRecord
import kotlinx.coroutines.flow.collect
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
    todoViewModel: TodoViewModel, // To fetch expenses
    onMonthChanged: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val daysInGrid = remember(currentCalendarMonth) {
        getDaysInMonthGrid(currentCalendarMonth)
    }
    
    // Get master record totals for all days in the month
    var masterRecordTotals by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    
    LaunchedEffect(currentCalendarMonth) {
        try {
            Log.d("CalendarDebug", "Fetching master records for month: $currentCalendarMonth")
            val totals = todoViewModel.getMasterRecordTotalsForMonth(currentCalendarMonth)
            Log.d("CalendarDebug", "Received ${totals.size} totals: ${totals.entries.joinToString { "${it.key}=${it.value}" }}")
            masterRecordTotals = totals
        } catch (e: Exception) {
            Log.e("CalendarDebug", "Error fetching master totals", e)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header: Month Name and Navigation
        CalendarHeader(currentCalendarMonth, onMonthChanged)

        // Days of the Week Header
        DaysOfWeekHeader()

        // Calendar Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            items(daysInGrid) { day ->
                if (day != null) {
                    DayCell(
                        date = day,
                        isSelected = day == selectedDate,
                        masterTotal = masterRecordTotals[day.toString()],
                        onClick = { onDateSelected(day) },
                        modifier = Modifier.aspectRatio(1f) // Make cells square
                    )
                } else {
                    Box(Modifier.aspectRatio(1f)) // Empty cell placeholder
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
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { onMonthChanged(currentMonth.minusMonths(1)) }) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Previous Month")
        }
        Text(
            text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = { onMonthChanged(currentMonth.plusMonths(1)) }) {
            Icon(Icons.Default.ArrowForward, contentDescription = "Next Month")
        }
    }
}

@Composable
fun DaysOfWeekHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        val daysOfWeek = DayOfWeek.values()
        val orderedDays = daysOfWeek.drop(1) + daysOfWeek.first()
        orderedDays.forEach { dayOfWeek ->
            Text(
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    masterTotal: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    // Log detailed debug info for this specific day cell
    val isImportantDate = date.dayOfMonth == 8 && date.monthValue == 6 // June 8
    
    if (isImportantDate) {  
        // For debugging, log detailed info about important dates
        Log.d("CalendarCellFix", "Rendering cell for ${date} with masterTotal: $masterTotal")
    }

    Box(
        modifier = modifier
            .padding(1.dp)
            .clip(MaterialTheme.shapes.small)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                color = textColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 16.sp
            )
            
            // Format and display the master total with direct use of masterTotal
            if (masterTotal != null && masterTotal > 0) {
                val formattedAmount = "₹%.0f".format(masterTotal)
                Text(
                    text = formattedAmount,
                    color = if (isSelected) textColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.secondary,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1
                )
                
                if (isImportantDate) {
                    Log.d("CalendarCellFix", "Displaying amount: $formattedAmount for ${date}")
                }
            }
        }
    }
}

fun getDaysInMonthGrid(yearMonth: YearMonth): List<LocalDate?> {
    val firstDayOfMonth = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
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