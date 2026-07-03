package com.example.monday.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.monday.ExpenseCategory
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyColumnState

@Composable
fun EnhancedCategorySection(
    title: String,
    categories: List<ExpenseCategory>,
    onAddCategory: () -> Unit,
    onEditCategory: (ExpenseCategory) -> Unit,
    onDeleteCategory: (ExpenseCategory) -> Unit,
    onMoveCategory: (fromIndex: Int, toIndex: Int) -> Unit
) {
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val state = rememberReorderableLazyColumnState(lazyListState) { from, to ->
        onMoveCategory(from.index, to.index)
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            
            IconButton(onClick = onAddCategory) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
            }
        }
        
        Card(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp) // Prevents infinite height constraint crash when nested
                    .padding(vertical = 8.dp)
            ) {
                items(items = categories, key = { it.name }) { category ->
                    ReorderableItem(state, key = category.name) { isDragging ->
                        val elevation = if (isDragging) 8.dp else 0.dp
                        val bgColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = bgColor,
                            shadowElevation = elevation
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Drag Handle
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "Reorder",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .padding(4.dp)
                                        .draggableHandle(),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                IconButton(
                                    onClick = { onEditCategory(category) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                IconButton(
                                    onClick = { onDeleteCategory(category) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (categories.isEmpty()) {
                    item {
                        Text(
                            text = "No categories added yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
} 