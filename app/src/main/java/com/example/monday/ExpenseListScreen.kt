package com.example.monday

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items // For LazyVerticalGrid
import androidx.compose.foundation.lazy.items // For LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*

import com.example.monday.TodoViewModel
import com.example.monday.TodoItem
import com.example.monday.ExpenseCategory
import com.example.monday.CategoryItem
import com.example.monday.TodoItemRow
import com.example.monday.AddNewExpenseScreen
import com.example.monday.parsePrice
import com.example.monday.parseCategoryInfo
import com.example.monday.DedicatedExpenseListScreen
import com.example.monday.ui.theme.KharchajiTheme
// import com.example.monday.MainActivity // Keep commented for now, onShareClick should handle context

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    todoViewModel: TodoViewModel, 
    onShareClick: (Context, List<TodoItem>, Double) -> Unit,
    onViewRecordsClick: () -> Unit
) {
    var showListScreen by remember { mutableStateOf(true) }

    val todoItems by todoViewModel.todoItems.collectAsState(initial = emptyList())
    val context = LocalContext.current
    
    var selectedItems by remember { mutableStateOf<Set<TodoItem>>(emptySet()) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var selectedCategoriesState by remember { mutableStateOf<Set<ExpenseCategory>>(emptySet()) }
    
    val expenseCategoriesList = listOf(
        ExpenseCategory("Groceries", Icons.Outlined.ShoppingCart),
        ExpenseCategory("Food", Icons.Outlined.Restaurant),
        ExpenseCategory("Transport", Icons.Outlined.DirectionsCar),
        ExpenseCategory("Bills", Icons.Outlined.Receipt),
        ExpenseCategory("Shopping", Icons.Outlined.LocalMall),
        ExpenseCategory("Health", Icons.Outlined.Medication),
        ExpenseCategory("Education", Icons.Outlined.School),
        ExpenseCategory("Entertainment", Icons.Outlined.Movie),
        ExpenseCategory("Other", Icons.Outlined.MoreHoriz)
    )
    
    if (showCategoryDialog) {
        Dialog(onDismissRequest = { showCategoryDialog = false }) {
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
                        items(expenseCategoriesList) { category ->
                            CategoryItem(
                                category = category,
                                isSelected = selectedCategoriesState.contains(category),
                                onClick = {
                                    selectedCategoriesState = if (selectedCategoriesState.contains(category)) {
                                        selectedCategoriesState - category
                                    } else {
                                        selectedCategoriesState + category
                                    }
                                }
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showCategoryDialog = false }) {
                            Text("Cancel")
                        }
                        
                        TextButton(
                            onClick = { 
                                val categoryNames = selectedCategoriesState.map { it.name }
                                if (categoryNames.isNotEmpty()) {
                                    selectedItems.forEach { item ->
                                        val (baseText, existingCategories) = parseCategoryInfo(item.text)
                                        val allCategories = (existingCategories + categoryNames).distinct()
                                        val categoryCodes = if (allCategories.isNotEmpty()) {
                                            "|CATS:" + allCategories.joinToString(",")
                                        } else {
                                            ""
                                        }
                                        todoViewModel.updateItem(item.copy(text = baseText + categoryCodes))
                                    }
                                }
                                showCategoryDialog = false
                                selectedItems = emptySet()
                            },
                            enabled = selectedCategoriesState.isNotEmpty()
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }

    if (showListScreen) {
        DedicatedExpenseListScreen(
            todoViewModel = todoViewModel,
            onShareClick = { 
                val itemsToShare = todoItems.filter { it.isDone }
                val sumToShare = itemsToShare.sumOf { parsePrice(it.text) }
                onShareClick(context, itemsToShare, sumToShare)
            },
            onViewRecordsClick = onViewRecordsClick
        )
    } else {
        AddNewExpenseScreen(
            onNextClick = { showListScreen = true },
            todoViewModel = todoViewModel
        )
    }
} 