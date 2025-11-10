package com.example.monday

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import com.example.monday.ui.screens.ExpenseCalendarDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.monday.ui.theme.KharchajiTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.monday.formatForDisplay
import com.example.monday.toLocalDate
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Kizitonwose Calendar imports
import com.kizitonwose.calendar.compose.VerticalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import kotlinx.coroutines.flow.firstOrNull

// Project specific imports
import com.example.monday.TodoViewModel
import com.example.monday.TodoItem
import com.example.monday.CalculationRecord
import com.example.monday.RecordItem
import com.example.monday.parsePrice
import com.example.monday.parseItemText
import com.example.monday.LocalTodoItemRow
import com.example.monday.EditItemDialog
import com.example.monday.todoItemToRecordItem
import com.example.monday.recordItemToTodoItemText
import com.example.monday.ExpenseCategory
import com.example.monday.CategoryItem
import com.example.monday.parseCategoryInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import com.example.monday.ui.components.AppDrawerContent
import com.example.monday.ui.components.DateSummary
import com.example.monday.ui.components.ExpenseActions
import com.example.monday.ui.components.CategorySelectionDialog
import com.example.monday.ui.components.DeleteAllConfirmationDialog
import com.example.monday.ui.components.CategorySelectionPopup
import androidx.compose.runtime.derivedStateOf
import com.example.monday.ui.components.MultiCategorySelectionDialog
import com.example.monday.ShareUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DedicatedExpenseListScreen(
    todoViewModel: TodoViewModel,
    onShareClick: () -> Unit,
    onNavigateToBatchSave: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onViewRecordsClick: () -> Unit = {},
    onAllExpensesClick: () -> Unit = {}
) {
    var editingItemId by remember { mutableStateOf<Int?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Date Picker State - now from ViewModel
    val selectedDate by todoViewModel.selectedDate.collectAsState()
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

    // State for custom calendar dialog visibility
    var showCustomCalendarDialog by remember { mutableStateOf(false) }
    
    // State for category filter dialog for calendar view export
    var showCalendarViewFilterDialog by remember { mutableStateOf(false) }
    
    // Drawer state for the navigation drawer
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    // Step 2: Filter displayed expenses
    // Observe the selectedDate from ViewModel for filtering
    val itemsFromViewModel by todoViewModel.getExpensesForDate(selectedDate).collectAsState(initial = emptyList())
    val itemsForSelectedDate by remember(itemsFromViewModel) {
        derivedStateOf {
            itemsFromViewModel.sortedBy { it.id }
        }
    }

    // Calculations should now use itemsForSelectedDate
    val totalItemsCount by remember(itemsForSelectedDate) { derivedStateOf { itemsForSelectedDate.size } }
    val checkedItemsCount by remember(itemsForSelectedDate) { derivedStateOf { itemsForSelectedDate.count { it.isDone } } }
    val totalSum by remember(itemsForSelectedDate) { derivedStateOf { itemsForSelectedDate.sumOf { parsePrice(it.text) } } }
    val checkedSum by remember(itemsForSelectedDate) { derivedStateOf { itemsForSelectedDate.filter { it.isDone }.sumOf { parsePrice(it.text) } } }
    val masterCheckboxState by remember(itemsForSelectedDate) {
        derivedStateOf {
            itemsForSelectedDate.isNotEmpty() && itemsForSelectedDate.all { it.isDone }
        }
    }
    val undoableItemsStack by todoViewModel.undoableDeletedItems.collectAsState()

    var showCategoryPopup by remember { mutableStateOf(false) }
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

    // Show category filter dialog for calendar view export
    if (showCalendarViewFilterDialog) {
        var allCategories by remember { mutableStateOf<List<String>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(key1 = Unit) {
            allCategories = withContext(Dispatchers.IO) {
                todoViewModel.getPrimaryCategories()
            }
            isLoading = false
        }

        if (isLoading) {
            // Show loading indicator
            AlertDialog(
                onDismissRequest = { showCalendarViewFilterDialog = false },
                title = { Text("Loading Categories") },
                text = { Text("Please wait...") },
                confirmButton = {}
            )
        } else if (allCategories.isEmpty()) {
            // Show message when no primary categories are available
            AlertDialog(
                onDismissRequest = { showCalendarViewFilterDialog = false },
                title = { Text("No Categories Available") },
                text = { Text("No primary categories found. Please add some categories to your expenses first.") },
                confirmButton = {
                    Button(onClick = { showCalendarViewFilterDialog = false }) {
                        Text("OK")
                    }
                }
            )
        } else {
            // Show category selection dialog when categories are available
            MultiCategorySelectionDialog(
                title = "Choose Primary Categories to Export",
                allCategories = allCategories,
                onDismiss = { showCalendarViewFilterDialog = false },
                onConfirm = { selectedCategories ->
                    scope.launch {
                        val allExpenses = withContext(Dispatchers.IO) {
                            todoViewModel.getAllExpensesForExport()
                        }

                        // Get all unique categories used in any expense
                        val allUniqueCategories = withContext(Dispatchers.IO) {
                            todoViewModel.getAllUniqueCategories()
                        }

                        // Get categories organized by type
                        val regularCategoriesByType = withContext(Dispatchers.IO) {
                            todoViewModel.getAllCategoriesByType()
                        }
                        
                        // Create a modified categoriesByType with selected categories only in primary
                        val categoriesByType = if (selectedCategories.isNotEmpty()) {
                            mapOf(
                                "primary" to selectedCategories,
                                "secondary" to (regularCategoriesByType["secondary"] ?: emptyList()),
                                "tertiary" to (regularCategoriesByType["tertiary"] ?: emptyList()),
                                "other" to (regularCategoriesByType["other"] ?: emptyList())
                            )
                        } else {
                            regularCategoriesByType
                        }

                        // Filter expenses if categories are provided
                        val filteredExpenses = if (selectedCategories.isNotEmpty()) {
                            allExpenses.filter { expense ->
                                expense.categories?.any { category -> selectedCategories.contains(category) } == true
                            }
                        } else {
                            allExpenses
                        }

                        val expenses = filteredExpenses.map { todoItem ->
                            val (description, quantity, price) = parseItemText(todoItem.text)
                            val category = todoItem.categories?.firstOrNull()

                            Expense(
                                description = description,
                                amount = price.toDouble(),
                                quantity = quantity,
                                category = category,
                                timestamp = todoItem.timestamp
                            )
                        }

                        // Generate filter description
                        val filterDescription = if (selectedCategories.isNotEmpty()) {
                            selectedCategories.joinToString(", ")
                        } else {
                            null
                        }

                        // Share the calendar view with all unique categories and categories by type
                        ShareUtils.shareCalendarViewHtml(
                            context = context,
                            expenses = expenses,
                            filterDescription = filterDescription,
                            allCategories = allUniqueCategories,
                            categoriesByType = categoriesByType
                        )
                    }
                    showCalendarViewFilterDialog = false
                }
            )
        }
    }

    // New category popup for hierarchical categories
    if (showCategoryPopup) {
        // Get existing categories from selected items
        val existingCategories = if (selectedCategoriesState.isNotEmpty()) {
            selectedCategoriesState.flatMap { category ->
                val (_, categories) = parseCategoryInfo(category.name)
                categories
            }.distinct()
        } else {
            emptyList()
        }

        CategorySelectionPopup(
            onDismiss = { showCategoryPopup = false },
            initialSelectedCategories = existingCategories,
            onCategoriesSelected = { categoryNames, hasPrimary, hasSecondary, hasTertiary ->
                if (categoryNames.isNotEmpty()) {
                    // We've removed selection mode, so this block is unnecessary
                    if (selectedCategoriesState.isNotEmpty()) {
                        // Clear the selected categories
                        selectedCategoriesState = emptySet()
                    } 
                    // Apply to all checked items
                    else if (itemsForSelectedDate.any { it.isDone }) {
                        itemsForSelectedDate.filter { it.isDone }.forEach { item ->
                            val (baseText, _) = parseCategoryInfo(item.text)
                            val categoryCodes = if (categoryNames.isNotEmpty()) {
                                "|CATS:" + categoryNames.joinToString(",")
                            } else {
                                ""
                            }
                            
                            // Update the item with the new categories and flags
                            todoViewModel.updateItem(item.copy(
                                text = baseText + categoryCodes,
                                categories = categoryNames,
                                hasPrimaryCategory = hasPrimary,
                                hasSecondaryCategory = hasSecondary,
                                hasTertiaryCategory = hasTertiary,
                                isDone = false
                            ))
                        }
                    }
                }
            },
            viewModel = todoViewModel
        )
    }

    // Keep the old dialog for backward compatibility
    if (showCategoryDialog) {
        CategorySelectionDialog(
            expenseCategories = expenseCategoriesList,
            selectedCategories = selectedCategoriesState,
            onCategoryClick = { category ->
                                    selectedCategoriesState = if (selectedCategoriesState.contains(category)) {
                                        selectedCategoriesState - category
                                    } else {
                                        selectedCategoriesState + category
                                    }
            },
            onDismiss = { showCategoryDialog = false },
            onConfirm = {
                                val categoryNames = selectedCategoriesState.map { it.name }
                                if (categoryNames.isNotEmpty()) {
                                    itemsForSelectedDate.forEach { item ->
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
                                selectedCategoriesState = emptySet()
            }
        )
    }

    // State for confirmation dialog
    var confirmationDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmAction by remember { mutableStateOf({}) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                onExport = { showCalendarViewFilterDialog = true },
                onImport = { /* Not implemented yet */ },
                onSaveAll = { 
                    scope.launch {
                        drawerState.close()
                        onViewRecordsClick()
                    }
                },
                onBatchSave = {
                    scope.launch {
                        drawerState.close()
                        onNavigateToBatchSave()
                    }
                },
                onSettings = {
                    scope.launch {
                        drawerState.close()
                        onNavigateToSettings()
                    }
                },
                onAllExpenses = {
                    scope.launch {
                        drawerState.close()
                        onAllExpensesClick()
                    }
                },
                onExportCalendarView = {
                    scope.launch {
                        drawerState.close()
                        showCalendarViewFilterDialog = true
                    }
                }
            )
        }
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                        Text("My Expenses")
                },
                navigationIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            }) {
                                Icon(
                                    Icons.Default.Menu, 
                                    contentDescription = "Open Menu"
                                )
                    }
                },
                actions = {
                        // Regular Save Button
                        IconButton(onClick = {
                            val recordItems = itemsForSelectedDate.map { todoItemToRecordItem(it) }
                            if (recordItems.isNotEmpty()){
                                // Launch in coroutine scope to call the suspend function
                                scope.launch {
                                    try {
                                        // Create a record with CURRENT timestamp
                                        val newRecord = CalculationRecord(
                                            items = recordItems, // Save ALL items, not just checked ones
                                            totalSum = totalSum,
                                            checkedItemsCount = checkedItemsCount,
                                            checkedItemsSum = checkedSum,
                                            recordDate = selectedDate.toEpochMilli(),
                                            timestamp = System.currentTimeMillis() // Explicit timestamp
                                        )
                                        
                                        // Use our simplified duplicate detection
                                        todoViewModel.insertCalculationRecordIfNotDuplicate(newRecord)
                                    } catch (e: Exception) {
                                        Log.e("SaveRecord", "Error saving record", e)
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Save, contentDescription = "Save Current List as Record")
                        }
                    
                    // Calculation Records Button
                    IconButton(onClick = {
                        val currentSelectedDateMillis = selectedDate.toEpochMilli()
                        // Optional: ensure there's at least an empty record
                        todoViewModel.insertEmptyCalculationRecordIfNeeded(selectedDate)
                        onViewRecordsClick()
                    }) {
                        Icon(Icons.Filled.Analytics, contentDescription = "View Calculation Records")
                    }
                        
                        // Master Save Button
                        IconButton(
                            onClick = {
                                if (itemsForSelectedDate.isNotEmpty()) {
                                    scope.launch {
                                        try {
                                            todoViewModel.saveToMasterRecord(selectedDate, itemsForSelectedDate)
                                        } catch (e: Exception) {
                                            Log.e("MasterSave", "Error saving to master record", e)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(
                                    color = Color(0xFFFFF9C4),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Filled.Save,
                                contentDescription = "Save to Master Record",
                                tint = Color(0xFFFF9800)
                            )
                        }
                        
                        if (itemsForSelectedDate.any { it.isDone }) {
                            IconButton(onClick = onShareClick) {
                                Icon(Icons.Default.Share, contentDescription = "Share selected expenses")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding(), 
                            start = paddingValues.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                            end = paddingValues.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr))
                .padding(horizontal = 8.dp)
        ) {
                DateSummary(
                    selectedDate = selectedDate,
                    dateFormatter = dateFormatter,
                    onDecrementDate = { todoViewModel.decrementDate() },
                    onIncrementDate = { todoViewModel.incrementDate() },
                    onGoToToday = { todoViewModel.updateSelectedDate(LocalDate.now()) },
                    onDateClick = { showCustomCalendarDialog = true },
                    checkedItemsCount = checkedItemsCount,
                    checkedSum = checkedSum,
                    totalItemsCount = totalItemsCount,
                    totalSum = totalSum
                )

                    ExpenseActions(
                        masterCheckboxState = masterCheckboxState,
                onMasterCheckboxChange = { isChecked ->
                    // If isChecked is true, check all items
                    // If isChecked is false, uncheck all items
                                val updated = itemsForSelectedDate.map { it.copy(isDone = isChecked) }
                                todoViewModel.updateItems(updated)
                                },
                        onSelectAllClick = {
                    val shouldCheck = !masterCheckboxState || itemsForSelectedDate.any { !it.isDone }
                                val updated = itemsForSelectedDate.map { it.copy(isDone = shouldCheck) }
                                todoViewModel.updateItems(updated)
                                },
                        isUndoEnabled = undoableItemsStack.isNotEmpty(),
                onUndoClick = {
                    todoViewModel.undoLastDelete()
                },
                isDeleteEnabled = checkedItemsCount > 0,
                        onDeleteSelectedClick = {
                    todoViewModel.deleteSelectedItemsAndEnableUndo(itemsForSelectedDate.filter { it.isDone })
                        },
                        isItemsListEmpty = itemsForSelectedDate.isEmpty(),
                        onDeleteAllClick = { showDeleteAllDialog = true },
                        onCategoriesClick = { categoryNames, hasPrimary, hasSecondary, hasTertiary ->
                            // Apply categories to checked items
                            if (categoryNames.isNotEmpty()) {
                                val updatedItems = itemsForSelectedDate.filter { it.isDone }.map { item ->
                                    val (baseText, _) = parseCategoryInfo(item.text)
                                    val categoryCodes = if (categoryNames.isNotEmpty()) {
                                        "|CATS:" + categoryNames.joinToString(",")
                                    } else {
                                        ""
                                    }
                                    item.copy(
                                        text = baseText + categoryCodes,
                                        categories = categoryNames,
                                        hasPrimaryCategory = hasPrimary,
                                        hasSecondaryCategory = hasSecondary,
                                        hasTertiaryCategory = hasTertiary
                                    )
                                }
                                if (updatedItems.isNotEmpty()) {
                                    todoViewModel.updateItems(updatedItems)
                                }
                            }
                        },
                        selectedExpenses = remember(itemsForSelectedDate) {
                            derivedStateOf { itemsForSelectedDate.filter { it.isDone } }
                        }.value,
                        viewModel = todoViewModel
                    )

            if (itemsForSelectedDate.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No expenses. Tap the + button to add an item.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                            .padding(bottom = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                        items(itemsForSelectedDate, key = { it.id }) { item: TodoItem ->
                            LocalTodoItemRow(
                                item = item,
                                onCheckedChange = { isChecked ->
                                       todoViewModel.updateItem(item.copy(isDone = isChecked))
                                },
                                onRemoveClick = { 
                                    todoViewModel.removeItem(item)
                                },
                                onEditClick = { 
                                    editingItemId = item.id
                                },
                                viewModel = todoViewModel
                            )
                        }
                }
            }
        }
        }
    }

    if (editingItemId != null) {
        val itemToEdit = itemsForSelectedDate.find { it.id == editingItemId }
        if (itemToEdit != null) {
            EditItemDialog(
                item = itemToEdit,
                onDismiss = { editingItemId = null },
                onConfirm = { updatedText ->
                    todoViewModel.updateItem(itemToEdit.copy(text = updatedText))
                    editingItemId = null
                },
                predefinedQuantities = listOf("250g", "500g", "1kg", "1.5kg", "2kg"),
                customUnits = listOf("g", "kg", "ml", "l", "pcs"),
                onDeleteImage = { item, imageUrl ->
                    todoViewModel.deleteImageFromItem(item, imageUrl)
                },
                onAddImage = { item, uri ->
                    todoViewModel.addImageToItem(item, uri)
                }
            )
        }
    }

    if (showCustomCalendarDialog) {
        ExpenseCalendarDialog(
            selectedDate = selectedDate,
            onDismiss = { showCustomCalendarDialog = false },
            onDateSelected = { date ->
                todoViewModel.updateSelectedDate(date)
                                showCustomCalendarDialog = false
            },
            todoViewModel = todoViewModel
                    )
    }

    if (showDeleteAllDialog) {
        DeleteAllConfirmationDialog(
            onDismiss = { showDeleteAllDialog = false },
            onConfirm = {
                        todoViewModel.deleteSelectedItemsAndEnableUndo(itemsForSelectedDate)
                        showDeleteAllDialog = false
            },
            dateForDisplay = selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        )
    }

    // Function to handle merge action
    val onMergeClick: (CalculationRecord) -> Unit = { record ->
        Log.d("CategoryFix", "Merge button clicked with record ID: ${record.id}")
        // Set up confirmation dialog
        confirmAction = {
            // Log the record items before merging
            record.items.forEachIndexed { index, item ->
                Log.d("CategoryFix", "Merging item #$index: ${item.description}, Categories: ${item.categories}")
            }
            
            todoViewModel.clearAndSetRecordItems(record.items, selectedDate)
            Toast.makeText(context, "Merged record into current date", Toast.LENGTH_SHORT).show()
        }
        confirmationDialogState = Pair("Merge Record", "This will replace all current expenses with the items from this record. Continue?")
    }
    
    // Show confirmation dialog if state is set
    confirmationDialogState?.let { (title, message) ->
        AlertDialog(
            onDismissRequest = { confirmationDialogState = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmAction()
                        confirmationDialogState = null
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                Button(
                    onClick = { confirmationDialogState = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DedicatedExpenseListScreenPreview() { 
    KharchajiTheme {
        // Preview content (likely needs a mock ViewModel) 
    }
}

fun formatIndianCurrency(amount: Int): String {
    val formatter = java.text.DecimalFormat("##,##,##,##0")
    return formatter.format(amount)
            }

 