package com.example.monday.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.monday.ExpenseCategory

@Composable
fun DeleteCategoryConfirmDialog(
    category: ExpenseCategory,
    onDismiss: () -> Unit,
    onConfirmDelete: (removeFromExpenses: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Category") },
        text = {
            Column {
                Text(
                    "Are you sure you want to delete the category '${category.name}'?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Choose how to handle expenses with this category:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Button(
                    onClick = { onConfirmDelete(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove from All Expenses")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onConfirmDelete(false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Keep in Existing Expenses")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
} 