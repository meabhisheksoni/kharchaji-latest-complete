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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FindReplace
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindAndReplaceScreen(
    todoViewModel: TodoViewModel,
    onNavigateBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    
    // Get all expenses from the database
    val allExpensesFlow = remember { 
        todoViewModel.todoItems.map { items ->
            items.map { item ->
                val (description, quantity, _) = parseItemText(item.text)
                val price = parsePrice(item.text)
                ExtendedExpenseDisplayItem(
                    id = item.id,
                    date = Instant.ofEpochMilli(item.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate(),
                    description = description,
                    quantity = quantity,
                    price = price,
                    originalText = item.text,
                    isDone = item.isDone,
                    categories = item.categories,
                    imageUris = item.imageUris,
                    timestamp = item.timestamp
                )
            }.sortedByDescending { it.date } // Most recent first
        }
    }
    var allExpenses by remember { mutableStateOf<List<ExtendedExpenseDisplayItem>>(emptyList()) }
    
    // Collect the flow
    LaunchedEffect(allExpensesFlow) {
        allExpensesFlow.collect { expenses ->
            allExpenses = expenses
        }
    }
    
    // Filter expenses based on search query
    val filteredExpenses = remember(allExpenses, searchQuery) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            allExpenses.filter { expense ->
                expense.originalText.contains(searchQuery, ignoreCase = true)
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
                title = { Text("Find and Replace") },
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
        },
        snackbarHost = {
            SnackbarHost(
                hostState = remember { SnackbarHostState() }.also { 
                    LaunchedEffect(showSnackbar) {
                        if (showSnackbar) {
                            it.showSnackbar(snackbarMessage)
                            showSnackbar = false
                        }
                    }
                }
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
                placeholder = { Text("Find text...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.clearFocus() }
                )
            )
            
            // Replace bar
            OutlinedTextField(
                value = replaceQuery,
                onValueChange = { replaceQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Replace with...") },
                leadingIcon = { Icon(Icons.Default.FindReplace, contentDescription = "Replace Icon") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                )
            )
            
            // Apply button
            Button(
                onClick = {
                    if (searchQuery.isBlank()) {
                        snackbarMessage = "Please enter text to find"
                        showSnackbar = true
                        return@Button
                    }
                    
                    coroutineScope.launch {
                        val replacedItems = filteredExpenses.map { expense ->
                            // Create a new TodoItem with replaced text
                            val newText = expense.originalText.replace(
                                searchQuery, 
                                replaceQuery, 
                                ignoreCase = true
                            )
                            
                            TodoItem(
                                id = expense.id,
                                text = newText,
                                isDone = expense.isDone,
                                timestamp = expense.timestamp,
                                categories = expense.categories,
                                imageUris = expense.imageUris
                            )
                        }
                        
                        // Update all items in the database
                        replacedItems.forEach { item ->
                            todoViewModel.updateItem(item)
                        }
                        
                        snackbarMessage = "Replaced ${replacedItems.size} occurrences"
                        showSnackbar = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Apply Replacement")
            }
            
            // Table header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.LightGray.copy(alpha = 0.3f))
                    .padding(vertical = 8.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = "Description",
                    modifier = Modifier.weight(0.7f),
                    fontSize = 14.sp
                )
                Text(
                    text = "Price",
                    modifier = Modifier.weight(0.3f),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            
            // Expenses list
            if (filteredExpenses.isEmpty() && searchQuery.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No matching expenses found")
                }
            } else if (searchQuery.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Enter text to find expenses")
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
                                // Description column - Simplified to remove CATS: section
                                val displayText = expense.originalText.split("|CATS:").firstOrNull() ?: expense.originalText
                                
                                // Extract just the description part without the price
                                val descriptionOnly = if (displayText.contains(" - ₹")) {
                                    displayText.split(" - ₹").firstOrNull() ?: displayText
                                } else {
                                    displayText
                                }
                                
                                Text(
                                    text = descriptionOnly,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .weight(0.7f),
                                    fontSize = 14.sp
                                )
                                
                                // Price column
                                Text(
                                    text = "₹${expense.price}",
                                    modifier = Modifier.weight(0.3f),
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