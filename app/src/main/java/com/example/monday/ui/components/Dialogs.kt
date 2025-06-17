package com.example.monday.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.monday.CategoryItem
import com.example.monday.ExpenseCategory
import com.example.monday.TodoViewModel
import java.time.format.DateTimeFormatter

@Composable
fun CategorySelectionDialog(
    expenseCategories: List<ExpenseCategory>,
    selectedCategories: Set<ExpenseCategory>,
    onCategoryClick: (ExpenseCategory) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Categories",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    gridItems(expenseCategories) { category ->
                        CategoryItem(
                            category = category,
                            isSelected = selectedCategories.contains(category),
                            onClick = { onCategoryClick(category) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    TextButton(
                        onClick = onConfirm,
                        enabled = selectedCategories.isNotEmpty()
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteAllConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    dateForDisplay: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete All Expenses?") },
        text = { Text("Are you sure you want to delete all expenses for $dateForDisplay? This action cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete All") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ImportConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Data?") },
        text = { Text("This will replace all your current expenses and records with the data from the selected file. This action cannot be undone. Are you sure you want to continue?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
} 