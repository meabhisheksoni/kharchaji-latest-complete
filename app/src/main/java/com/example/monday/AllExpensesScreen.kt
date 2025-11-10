package com.example.monday

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllExpensesScreen(
    todoViewModel: TodoViewModel,
    onNavigateBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    
    // Get all expenses from master-saved records
    val allExpensesFlow = remember { 
        todoViewModel.getAllMasterSavedExpenseDisplayItems()
    }
    var allExpenses by remember { mutableStateOf<List<ExpenseDisplayItem>>(emptyList()) }
    
    // Collect the flow
    LaunchedEffect(allExpensesFlow) {
        allExpensesFlow.collect { expenses ->
            allExpenses = expenses
        }
    }
    
    // Filter expenses based on search query
    val filteredExpenses = remember(allExpenses, searchQuery) {
        if (searchQuery.isBlank()) {
            allExpenses
        } else {
            allExpenses.filter { expense ->
                expense.description.contains(searchQuery, ignoreCase = true) ||
                expense.quantity?.contains(searchQuery, ignoreCase = true) == true ||
                expense.price.toString().contains(searchQuery)
            }
        }
    }
    
    // Group expenses by date for display
    val groupedExpenses = remember(filteredExpenses) {
        filteredExpenses.groupBy { it.date }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Expenses") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search expenses...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { focusManager.clearFocus() }
                )
            )
            
            // Table header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.LightGray.copy(alpha = 0.3f))
                    .padding(vertical = 8.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = "Description",
                    modifier = Modifier.weight(0.6f),
                    fontSize = 14.sp
                )
                Text(
                    text = "Quantity",
                    modifier = Modifier.weight(0.2f),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    text = "Price",
                    modifier = Modifier.weight(0.2f),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            
            // Expenses list
            if (filteredExpenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No expenses found")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    groupedExpenses.forEach { (date, expenses) ->
                        item {
                            Text(
                                text = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFEEEEEE))
                                    .padding(vertical = 8.dp, horizontal = 8.dp)
                            )
                        }
                        
                        items(expenses) { expense ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 5.dp)
                            ) {
                                // Description column
                                Text(
                                    text = expense.description,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .weight(0.6f),
                                    fontSize = 14.sp
                                )
                                
                                // Quantity column
                                Text(
                                    text = expense.quantity ?: "-",
                                    modifier = Modifier.weight(0.2f),
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                
                                // Price column
                                Text(
                                    text = "₹${expense.price}",
                                    modifier = Modifier.weight(0.2f),
                                    fontSize = 14.sp
                                )
                            }
                            
                            // Add divider between items
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = 0.5.dp,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }
        }
    }
} 