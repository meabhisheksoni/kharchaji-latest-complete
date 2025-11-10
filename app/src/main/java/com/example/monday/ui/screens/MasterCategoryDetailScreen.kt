package com.example.monday.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.monday.ExpenseDisplayItem
import com.example.monday.TodoViewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterCategoryDetailScreen(
    viewModel: TodoViewModel,
    category: String,
    onNavigateBack: () -> Unit
) {
    var expenses by remember { mutableStateOf<List<ExpenseDisplayItem>>(emptyList()) }

    LaunchedEffect(category) {
        viewModel.getMasterExpensesByCategory(category).collectLatest { list ->
            expenses = list
        }
    }

    val grouped = remember(expenses) { expenses.groupBy { it.date } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$category (Master)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (expenses.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No expenses found")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                grouped.toSortedMap(compareByDescending { it }).forEach { (date, itemsForDate) ->
                    item {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 12.dp)
                        )
                    }
                    items(itemsForDate) { e ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = e.description,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = e.quantity ?: "-",
                                modifier = Modifier.width(80.dp),
                                color = Color.Gray
                            )
                            Text(
                                text = "₹${e.price}",
                                modifier = Modifier.width(80.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                        Divider()
                    }
                }
            }
        }
    }
} 