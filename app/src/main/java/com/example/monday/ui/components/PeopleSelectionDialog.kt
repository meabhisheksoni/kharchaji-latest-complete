package com.example.monday.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.monday.ExpenseCategory
import com.example.monday.TodoViewModel

/**
 * Dialog for selecting people (primary categories) before sharing expenses
 */
@Composable
fun PeopleSelectionDialog(
    viewModel: TodoViewModel,
    title: String = "Select People to Share",
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    // Get primary categories from ViewModel
    val primaryCategories by viewModel.primaryCategories.collectAsState()
    
    // State for tracking selected categories
    var selectedCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // State for "Select All" checkbox
    val allSelected = primaryCategories.isNotEmpty() && 
                     selectedCategories.size == primaryCategories.size
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Select All option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedCategories = if (allSelected) {
                                emptyList()
                            } else {
                                primaryCategories.map { it.name }
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            selectedCategories = if (checked) {
                                primaryCategories.map { it.name }
                            } else {
                                emptyList()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Select All",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // List of people (primary categories)
                if (primaryCategories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No people categories found. Add some in Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        items(primaryCategories) { category ->
                            val isSelected = selectedCategories.contains(category.name)
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCategories = if (isSelected) {
                                            selectedCategories - category.name
                                        } else {
                                            selectedCategories + category.name
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedCategories = if (checked) {
                                            selectedCategories + category.name
                                        } else {
                                            selectedCategories - category.name
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                // Show category icon or default person icon
                                val icon = category.icon ?: Icons.Default.Person
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                
                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = { onConfirm(selectedCategories) },
                        enabled = primaryCategories.isNotEmpty()
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
} 