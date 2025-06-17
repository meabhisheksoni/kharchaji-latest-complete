package com.example.monday.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.monday.TodoItem
import com.example.monday.TodoViewModel
import com.example.monday.TodoItemRow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DedicatedExpenseListScreen(
    todoViewModel: TodoViewModel,
    onNavigateBack: () -> Unit,
    onFabClick: () -> Unit,
    onEditItem: (TodoItem) -> Unit
) {
    var currentSelectedDate by remember { mutableStateOf(LocalDate.now()) }
    val expensesForDate by todoViewModel.getExpensesForDate(currentSelectedDate)
        .collectAsState(initial = emptyList())

    var showCalendarDialog by remember { mutableStateOf(false) }
    var calendarDisplayMonth by remember { mutableStateOf(YearMonth.from(currentSelectedDate)) }


    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Expenses for")
                        Text(currentSelectedDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCalendarDialog = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Select Date")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onFabClick) {
                Text("Add For ${currentSelectedDate.format(DateTimeFormatter.ofPattern("dd MMM"))}")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {
            if (expensesForDate.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No expenses for this date.")
                }
            } else {
                Column { // Using Column directly
                    expensesForDate.forEach { item ->
                        TodoItemRow(
                            item = item,
                            onCheckedChange = { _ ->
                                onEditItem(item)
                            },
                            onRemoveClick = {
                                todoViewModel.removeItem(item)
                            },
                            viewModel = todoViewModel
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        if (showCalendarDialog) {
            Dialog(onDismissRequest = { showCalendarDialog = false }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .wrapContentHeight(),
                    shape = MaterialTheme.shapes.large
                ) {
                    CustomCalendarView(
                        currentCalendarMonth = calendarDisplayMonth,
                        selectedDate = currentSelectedDate,
                        todoViewModel = todoViewModel,
                        onDateSelected = { date ->
                            currentSelectedDate = date
                            calendarDisplayMonth = YearMonth.from(date)
                            showCalendarDialog = false
                        },
                        onMonthChanged = { newMonth ->
                            calendarDisplayMonth = newMonth
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
} 