package com.example.monday.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.monday.TodoViewModel
import kotlinx.coroutines.flow.collect
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.text.DecimalFormat

@Composable
fun ExpenseCalendarDialog(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    todoViewModel: TodoViewModel
) {
    var currentYearMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Month navigation header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            currentYearMonth = currentYearMonth.minusMonths(1)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Month",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    Text(
                        text = currentYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(
                        onClick = {
                            currentYearMonth = currentYearMonth.plusMonths(1)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, 
                            contentDescription = "Next Month",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                // Day of week header with borders
                val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (dayLabel in daysOfWeek) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(0.5.dp, Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayLabel,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
                
                // Calendar days grid with borders
                val firstDayOfMonth = currentYearMonth.atDay(1)
                val daysInMonth = currentYearMonth.lengthOfMonth()
                val firstDayOfWeekIndex = (firstDayOfMonth.dayOfWeek.value % 7)
                
                val totalDays = firstDayOfWeekIndex + daysInMonth
                val totalRows = (totalDays + 6) / 7
                
                for (row in 0 until totalRows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(85.dp) // Increased height to match reference image
                    ) {
                        for (column in 0 until 7) {
                            val day = row * 7 + column - firstDayOfWeekIndex + 1
                            if (day in 1..daysInMonth) {
                                val date = currentYearMonth.atDay(day)
                                val isSelected = date == selectedDate
                                
                                // Each cell with border
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight() // Fill the full row height
                                        .border(0.5.dp, Color.LightGray)
                                ) {
                                    MonthDayCell(
                                        date = date,
                                        isSelected = isSelected,
                                        todoViewModel = todoViewModel,
                                        onClick = { onDateSelected(date) }
                                    )
                                }
                            } else {
                                // Empty cell with border
                                Spacer(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight() // Fill the full row height
                                        .border(0.5.dp, Color.LightGray)
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
    todoViewModel: TodoViewModel,
    onClick: () -> Unit
) {
    var dailyTotal by remember(date) { mutableStateOf<Double?>(null) }

    LaunchedEffect(date, todoViewModel) {
        // First try to get a master record for this date specifically
        todoViewModel.getMasterRecordForDate(date).collect { masterRecord ->
            if (masterRecord != null) {
                // If a master record exists, use its total
                dailyTotal = masterRecord.totalSum
            } else {
                // Fall back to the old method if no master record found
                todoViewModel.getCalculationRecordsForDate(date).collect { records ->
                    // Filter for master records first
                    val masterRecords = records.filter { it.isMasterSave }
                    
                    // Use master record if available, otherwise use the highest ID normal record
                    dailyTotal = if (masterRecords.isNotEmpty()) {
                        masterRecords.maxByOrNull { it.timestamp }?.totalSum
                    } else if (records.isNotEmpty()) {
                        records.maxByOrNull { it.id }?.totalSum
                    } else {
                        null
                    }
                }
            }
        }
    }

    val isToday = date == LocalDate.now()
    
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        isToday -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize() // Fill the parent Box that has the border
            .padding(1.dp)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center // Center the content in the cell
    ) {
        // Date aligned to top center with much more top padding
        Text(
            text = date.dayOfMonth.toString(),
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp) // Much more top padding to match reference
        )
        
        // Amount aligned to bottom center with much more bottom padding
        if (dailyTotal != null && dailyTotal!! > 0) {
            val amountText = formatIndianCurrency(dailyTotal!!)
            var textSize by remember { mutableStateOf(7.sp) }
            var readyToDraw by remember { mutableStateOf(false) }
            
            // Better balance between readability and fitting large numbers
            val initialSize = when {
                amountText.length <= 5 -> 13.sp  // Small amounts (₹123)
                amountText.length <= 7 -> 11.sp  // Medium (₹12,345)
                amountText.length <= 9 -> 9.sp   // Large (₹12,34,567)
                amountText.length <= 11 -> 8.sp  // Very large (₹1,23,45,678)
                else -> 7.sp                     // Extremely large (₹99,99,99,999)
            }
            
            textSize = initialSize
            
            Text(
                text = amountText,
                color = Color(0xFFD32F2F),
                fontSize = textSize,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                letterSpacing = (-0.03).sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 0.5.dp)
                    .padding(bottom = 16.dp) // Much more bottom padding to match reference
                    .drawWithContent {
                        if (readyToDraw) drawContent()
                    },
                onTextLayout = { textLayoutResult ->
                    if (textLayoutResult.didOverflowWidth) {
                        // Continue with aggressive reduction
                        textSize = textSize * 0.8f
                    } else {
                        readyToDraw = true
                    }
                }
            )
        }
    }
}

private fun formatIndianCurrency(amount: Double): String {
    // Format large Indian numbers with proper comma placement for lakhs and crores
    val formatter = DecimalFormat("₹#,##,##,##0")
    return formatter.format(amount.toInt()) // Converting to int to avoid decimal places
} 