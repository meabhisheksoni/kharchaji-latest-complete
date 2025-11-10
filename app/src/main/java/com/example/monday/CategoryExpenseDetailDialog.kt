package com.example.monday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CategoryExpenseDetailDialog(
    onDismiss: () -> Unit,
    items: List<TodoItem>,
    categoryName: String,
    isIntersection: Boolean,
    categories: List<String>,
    color: Color
) {
    // Filter expenses based on selected category or intersection
    val filteredItems = remember(items, categories, isIntersection) {
        if (isIntersection) {
            // For intersection, all selected categories must be present
            items.filter { item ->
                val itemCategories = parseCategoryInfo(item.text).second.toSet()
                categories.all { it in itemCategories }
            }
        } else {
            // For single category
            items.filter { item ->
                val itemCategories = parseCategoryInfo(item.text).second
                categories.first() in itemCategories
            }
        }
    }
    
    val totalAmount = remember(filteredItems) {
        filteredItems.sumOf { parsePrice(it.text) }
    }


    // State to track sorting preference
    var sortByAmount by remember { mutableStateOf(false) }
    
    // Sort items based on user preference
    val sortedItems = remember(filteredItems, sortByAmount) {
        if (sortByAmount) {
            // Sort by amount in decreasing order
            filteredItems.sortedByDescending { parsePrice(it.text) }
        } else {
            // Sort by date in decreasing order (default)
            filteredItems.sortedByDescending { it.timestamp }
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Dialog header with category name and total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = if (isIntersection) {
                            categories.joinToString(" ∩ ")
                        } else {
                            categoryName
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(0.7f)
                    )
                    
                    Text(
                        text = formatCurrency(totalAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.3f)
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Item count and sort toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredItems.size} expense${if (filteredItems.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Sort by amount",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Switch(
                            checked = sortByAmount,
                            onCheckedChange = { sortByAmount = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = color,
                                checkedTrackColor = color.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
                
                // List of expenses
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(sortedItems) { item ->
                        ExpenseItemRow(
                            description = parseCategoryInfo(item.text).first,
                            amount = parsePrice(item.text),
                            date = Date(item.timestamp)
                        )
                    }
                }
                
                // Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseItemRow(
    description: String,
    amount: Double,
    date: Date
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    
    // Extract the description without the price part
    val cleanDescription = remember(description) {
        // Most descriptions end with "- ₹XX.XX", so remove that part
        if (description.contains(" - ₹")) {
            description.substring(0, description.lastIndexOf(" - ₹"))
        } else {
            description
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date
            Text(
                text = dateFormat.format(date),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(60.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Description
            Text(
                text = cleanDescription,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium
            )
            
            // Amount
            Text(
                text = formatCurrency(amount),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatCurrency(amount: Double): String {
    val formatted = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(amount)
    return formatted.replace(".00", "")
} 