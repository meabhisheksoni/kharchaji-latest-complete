package com.example.monday.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.monday.TodoItem
import com.example.monday.TodoViewModel
import com.example.monday.formatIndianCurrency
import com.example.monday.parsePrice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun UncategorizedExpensesDialog(
    todoViewModel: TodoViewModel,
    onDismiss: () -> Unit
) {
    var uncategorizedExpenses by remember { mutableStateOf<List<CategoryExpenseItem>>(emptyList()) }
    var lessThanThreeExpenses by remember { mutableStateOf<List<CategoryExpenseItem>>(emptyList()) }
    var moreThanThreeExpenses by remember { mutableStateOf<List<CategoryExpenseItem>>(emptyList()) }
    var exactlyThreeExpenses by remember { mutableStateOf<List<CategoryExpenseItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    // Load expenses data when the dialog opens
    LaunchedEffect(key1 = Unit) {
        // Fetch different category count expenses
        val uncategorizedItems = todoViewModel.getUncategorizedExpenses()
        val lessThanThreeItems = todoViewModel.getExpensesWithLessThanThreeCategories()
        val moreThanThreeItems = todoViewModel.getExpensesWithMoreThanThreeCategories()
        val exactlyThreeItems = todoViewModel.getExpensesWithExactlyThreeCategories()
        
        // Convert to CategoryExpenseItem for display
        uncategorizedExpenses = convertToExpenseItems(uncategorizedItems)
        lessThanThreeExpenses = convertToExpenseItems(lessThanThreeItems)
        moreThanThreeExpenses = convertToExpenseItems(moreThanThreeItems)
        exactlyThreeExpenses = convertToExpenseItems(exactlyThreeItems)
        
        isLoading = false
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        title = { Text("Category Analysis") },
        text = { 
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f)
            ) {
                if (isLoading) {
                    Text("Loading expenses...")
                } else {
                    // Tab row for different category counts
                    TabRow(selectedTabIndex = selectedTabIndex) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("No Categories (${uncategorizedExpenses.size})") }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("<3 Categories (${lessThanThreeExpenses.size})") }
                        )
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            text = { Text(">3 Categories (${moreThanThreeExpenses.size})") }
                        )
                        Tab(
                            selected = selectedTabIndex == 3,
                            onClick = { selectedTabIndex = 3 },
                            text = { Text("=3 Categories (${exactlyThreeExpenses.size})") }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Content based on selected tab
                    when (selectedTabIndex) {
                        0 -> ExpensesList(expenses = uncategorizedExpenses, title = "Uncategorized Expenses")
                        1 -> ExpensesList(expenses = lessThanThreeExpenses, title = "Less Than 3 Categories")
                        2 -> ExpensesList(expenses = moreThanThreeExpenses, title = "More Than 3 Categories")
                        3 -> ExpensesList(expenses = exactlyThreeExpenses, title = "Exactly 3 Categories")
                    }
                }
            }
        },
        confirmButton = { 
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun ExpensesList(expenses: List<CategoryExpenseItem>, title: String) {
    Column {
        if (expenses.isEmpty()) {
            Text("No ${title.lowercase()} found.")
        } else {
            // Display the total count and amount
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total: ${expenses.size} items",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatIndianCurrency(expenses.sumOf { it.price }.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
            // List of expenses
            LazyColumn {
                items(expenses) { expense ->
                    ExpenseItemCard(expense)
                }
            }
        }
    }
}

@Composable
private fun ExpenseItemCard(expense: CategoryExpenseItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = expense.date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Show category count if available
                expense.categoryCount?.let { count ->
                    Text(
                        text = "$count categories",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Description
            Text(
                text = expense.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Price
            Text(
                text = formatIndianCurrency(expense.price.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// Data class to hold expense information
data class CategoryExpenseItem(
    val id: Int,
    val description: String,
    val price: Double,
    val date: LocalDate,
    val categoryCount: Int?
)

// Helper function to convert TodoItems to CategoryExpenseItems
private fun convertToExpenseItems(items: List<TodoItem>): List<CategoryExpenseItem> {
    return items.map { item ->
        val date = Instant.ofEpochMilli(item.timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        
        CategoryExpenseItem(
            id = item.id,
            description = item.text,
            price = parsePrice(item.text),
            date = date,
            categoryCount = item.categories?.size
        )
    }.sortedByDescending { it.date }  // Most recent first
} 