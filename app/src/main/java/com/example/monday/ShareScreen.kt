package com.example.monday

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

// Project specific imports
import com.example.monday.TodoItem
import com.example.monday.shareExpensesList

// ExpensesList composable might be needed if it was a separate component.
// For now, assuming a simple LazyColumn as in the MainActivity modification.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    todoViewModel: TodoViewModel,
    currentSelectedDate: LocalDate,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isSharing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Get items for the selected date and filter by isDone
    val itemsForDate by todoViewModel.getExpensesForDate(currentSelectedDate).collectAsState(initial = emptyList())
    val itemsToShare = itemsForDate.filter { it.isDone }
    val sumToShare = itemsToShare.sumOf { parsePrice(it.text) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share Expenses") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Displaying items and total sum directly here for simplicity.
                // If ExpensesList was a complex, reusable component, it should be separate.
                LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                    items(itemsToShare) { item ->
                        Text("Item: ${item.text} - Done: ${item.isDone}")
                    }
                    item { 
                        Text("Total Sum: $sumToShare", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        isSharing = true
                        try {
                            // Call the top-level function from ShareUtils.kt
                            shareExpensesList(
                                context = context, 
                                itemsToShare = itemsToShare, 
                                sumOfItemsToShare = sumToShare, 
                                expensesDate = currentSelectedDate
                            )
                        } finally {
                            isSharing = false
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                    enabled = !isSharing && itemsToShare.isNotEmpty()
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Text("Share Selected Items")
                        }
                    }
                }

                // New Button for sharing with monthly total
                Button(
                    onClick = {
                        isSharing = true
                        coroutineScope.launch {
                            try {
                                // 1. Initialize cumulative monthly sum
                                var cumulativeMonthlySum = 0.0
                                val currentMonth = currentSelectedDate.month
                                val currentYear = currentSelectedDate.year

                                // 2. Iterate from the 1st of the month to the currentSelectedDate
                                for (dayOfMonth in 1..currentSelectedDate.dayOfMonth) {
                                    val dateForLoop = LocalDate.of(currentYear, currentMonth, dayOfMonth)
                                    val recordsFlow = todoViewModel.getCalculationRecordsForDate(dateForLoop)
                                    val recordsForDay = recordsFlow.first() // Get the list of records for this day

                                    if (recordsForDay.isNotEmpty()) {
                                        // Find the record with the highest ID (latest) for that day
                                        val latestRecordForDay = recordsForDay.maxByOrNull { it.id }
                                        latestRecordForDay?.let {
                                            cumulativeMonthlySum += it.totalSum
                                        }
                                    }
                                }
                                val totalMonthlySum = cumulativeMonthlySum

                                // 3. Format the date parts (variables no longer used for the new text format)
                                // val monthName = currentSelectedDate.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
                                // val year = currentSelectedDate.year

                                // Construct the text message
                                val monthlyTotalText = "expence this month :- ${String.format(Locale.US, "%.2f", totalMonthlySum)}"

                                // 4. Call the updated share function, now passing the text to be included in the image
                                shareExpensesList(
                                    context = context,
                                    itemsToShare = itemsToShare,      // Image still shows selected items for the day
                                    sumOfItemsToShare = sumToShare,   // Sum for the image
                                    expensesDate = currentSelectedDate, // Date for the image title
                                    monthlySummaryText = monthlyTotalText // This text will now be in the image
                                )
                            } catch (e: Exception) {
                                Log.e("ShareScreen", "Error during share with monthly total", e)
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isSharing = false
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                    enabled = !isSharing && itemsToShare.isNotEmpty()
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Text("Share with Final Expense Uptill Date")
                        }
                    }
                }
            }
        }
    }
} 