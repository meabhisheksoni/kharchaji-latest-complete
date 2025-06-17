package com.example.monday.ui.components

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.monday.TodoItem
import com.example.monday.TodoViewModel

// Helper function to parse categories from expense text
private fun parseCategoryInfo(text: String): Pair<String, List<String>> {
    val parts = text.split("|CATS:")
    val baseText = parts[0]
    val categories = if (parts.size > 1) {
        parts[1].split(",")
    } else {
        emptyList()
    }
    return Pair(baseText, categories)
}

@Composable
fun ExpenseActions(
    masterCheckboxState: Boolean,
    onMasterCheckboxChange: (Boolean) -> Unit,
    onSelectAllClick: () -> Unit,
    isUndoEnabled: Boolean,
    onUndoClick: () -> Unit,
    isDeleteEnabled: Boolean,
    onDeleteSelectedClick: () -> Unit,
    isItemsListEmpty: Boolean,
    onDeleteAllClick: () -> Unit,
    onCategoriesClick: (List<String>, Boolean, Boolean, Boolean) -> Unit = { _, _, _, _ -> },
    selectedExpenses: List<TodoItem> = emptyList(),
    viewModel: TodoViewModel
) {
    var showCategoryDialog by remember { mutableStateOf(false) }
    
    // Get common categories from selected expenses
    val commonCategories = remember(selectedExpenses) {
        if (selectedExpenses.isEmpty()) {
            emptyList()
        } else {
            val allCategories = selectedExpenses.map { item -> 
                val (_, categories) = parseCategoryInfo(item.text)
                categories
            }.flatten()
            
            allCategories.groupBy { it }
                .filter { (_, occurrences) -> occurrences.size == selectedExpenses.size }
                .keys.toList()
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = masterCheckboxState,
                onCheckedChange = onMasterCheckboxChange
            )
            Text(
                text = if (masterCheckboxState && !isItemsListEmpty) "Deselect All" else "Select All",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clickable(onClick = onSelectAllClick)
                    .padding(start = 4.dp, end = 8.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isUndoEnabled) {
                IconButton(onClick = onUndoClick) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo Delete")
                }
            }
            
            // Only show categories button when items are selected (checked)
            if (isDeleteEnabled) {
                if (isUndoEnabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = { showCategoryDialog = true }) {
                    Icon(
                        Icons.Outlined.Category,
                        contentDescription = "Assign Categories",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDeleteSelectedClick) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete Selected Items",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            if (!isItemsListEmpty) {
                if (isUndoEnabled || isDeleteEnabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = onDeleteAllClick) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Delete All Expenses")
                }
            }
        }
    }
    
    if (showCategoryDialog) {
        val context = LocalContext.current
        CategorySelectionPopup(
            onDismiss = { showCategoryDialog = false },
            onCategoriesSelected = { categoryNames, hasPrimary, hasSecondary, hasTertiary ->
                // Apply the categories and also uncheck items in a single update to avoid resetting categories
                // Don't call the separate functions as that causes multiple updates
                if (categoryNames.isNotEmpty()) {
                    Log.d("CategoryDebug", "==== APPLYING CATEGORIES ====")
                    Log.d("CategoryDebug", "Categories selected: $categoryNames")
                    Log.d("CategoryDebug", "Primary: $hasPrimary, Secondary: $hasSecondary, Tertiary: $hasTertiary")
                    Log.d("CategoryDebug", "Items to apply to: ${selectedExpenses.size}")
                    
                selectedExpenses.forEach { item ->
                        val (baseText, _) = parseCategoryInfo(item.text)
                        val categoryCodes = "|CATS:" + categoryNames.joinToString(",")
                        
                        // Update the item with the new categories and flags AND uncheck it in one go
                        Log.d("CategoryDebug", "Applying to item ${item.id}")
                        viewModel.updateItem(item.copy(
                            text = baseText + categoryCodes,
                            categories = categoryNames,
                            hasPrimaryCategory = hasPrimary,
                            hasSecondaryCategory = hasSecondary,
                            hasTertiaryCategory = hasTertiary,
                            isDone = false
                        ))
                    }
                    
                    // Show a toast confirmation
                    /* Toast.makeText(
                        context,
                        "Categories applied to ${selectedExpenses.size} items",
                        Toast.LENGTH_SHORT
                    ).show() */
                    
                    Log.d("CategoryDebug", "==== CATEGORY APPLICATION COMPLETE ====")
                }
                
                // Close the dialog
                showCategoryDialog = false
            },
            initialSelectedCategories = commonCategories,
            viewModel = viewModel
        )
    }
} 