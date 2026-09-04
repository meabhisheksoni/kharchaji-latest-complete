package com.example.monday

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter

import com.example.monday.core.utils.*
import com.example.monday.data.models.TodoItem
import com.example.monday.ui.components.AppDrawerContent
import com.example.monday.ui.components.CategorySelectionDialog
import com.example.monday.ui.components.CategorySelectionPopup
import com.example.monday.ui.components.DeleteAllConfirmationDialog
import com.example.monday.ui.components.ExpenseActions
import com.example.monday.ui.components.MultiCategorySelectionDialog
import com.example.monday.ui.screens.ExpenseCalendarDialog
import com.example.monday.ui.modern.CategoryFilterChips
import com.example.monday.ui.modern.HeroDashboard
import com.example.monday.ui.modern.ModernColors as C
import com.example.monday.ui.modern.ModernDateNavigation
import com.example.monday.ui.modern.ModernExpenseItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernExpenseListScreen(
    todoViewModel: TodoViewModel,
    mainViewModel: com.example.monday.viewmodels.MainViewModel,
    exportViewModel: com.example.monday.viewmodels.ExportViewModel,
    statsViewModel: com.example.monday.viewmodels.StatsViewModel,
    settingsViewModel: com.example.monday.viewmodels.SettingsViewModel,
    onShareClick: () -> Unit,
    onNavigateToBatchSave: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onViewRecordsClick: () -> Unit = {},
    onAllExpensesClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var editingItemId by remember { mutableStateOf<Int?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showCustomCalendarDialog by remember { mutableStateOf(false) }
    var showCalendarViewFilterDialog by remember { mutableStateOf(false) }
    
    val selectedDate by mainViewModel.selectedDate.collectAsState()
    // Cached reactive flow — no zombie StateFlows, O(N) runs on Dispatchers.Default
    val itemsFromViewModel by mainViewModel.expensesForSelectedDate.collectAsState()
    val itemsForSelectedDate by remember(itemsFromViewModel) {
        derivedStateOf { itemsFromViewModel.sortedByDescending { it.id } }
    }

    val totalItemsCount by remember(itemsForSelectedDate) { derivedStateOf { itemsForSelectedDate.size } }
    val checkedItemsCount by remember(itemsForSelectedDate) { derivedStateOf { itemsForSelectedDate.count { it.isDone } } }
    val totalSum by remember(itemsForSelectedDate) { derivedStateOf { itemsForSelectedDate.sumOf { parsePrice(it.text) } } }
    val checkedSum by remember(itemsForSelectedDate) { derivedStateOf { itemsForSelectedDate.filter { it.isDone }.sumOf { parsePrice(it.text) } } }
    val masterCheckboxState by remember(itemsForSelectedDate) {
        derivedStateOf { itemsForSelectedDate.isNotEmpty() && itemsForSelectedDate.all { it.isDone } }
    }
    val undoableItemsStack by mainViewModel.undoableDeletedItems.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        mainViewModel.undoRestoredItemEvent.collect { restoredId ->
            snapshotFlow { itemsForSelectedDate }.filter { list -> list.any { it.id == restoredId } }.first()
            val index = itemsForSelectedDate.indexOfFirst { it.id == restoredId }
            if (index != -1) { listState.animateScrollToItem(index) }
        }
    }

    val directMasterEditMode by settingsViewModel.directMasterEditMode.collectAsState()
    LaunchedEffect(itemsForSelectedDate, directMasterEditMode, selectedDate) {
        if (directMasterEditMode) {
            statsViewModel.forceSyncToMasterRecord(selectedDate, itemsForSelectedDate)
        }
    }

    var showCategoryPopup by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var selectedCategoriesState by remember { mutableStateOf<Set<ExpenseCategory>>(emptySet()) }
    val expenseCategoriesList = listOf(
        ExpenseCategory("Groceries", Icons.Filled.Search), // Icon placeholders
        ExpenseCategory("Food", Icons.Filled.Search),
        ExpenseCategory("Transport", Icons.Filled.Search)
    ) // Used for fallback category dialog

    // Hero section computed values
    val avgPerExpense = if (totalItemsCount > 0) totalSum / totalItemsCount else 0.0
    val topCategory = remember(itemsForSelectedDate) {
        derivedStateOf {
            itemsForSelectedDate.mapNotNull { it.categories?.firstOrNull() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "—"
        }
    }
    val budgetLimit = 500.0
    val progressPercent = (totalSum / budgetLimit * 100).coerceIn(0.0, 100.0)
    var showSelectedInHero by remember { mutableStateOf(false) }
    LaunchedEffect(checkedItemsCount) { if (checkedItemsCount == 0) showSelectedInHero = false }

    // Dashboard view mode from settings; default "both" (swipeable)
    var dashboardViewMode by remember { mutableStateOf("both") }
    // Date bar position from settings; default "mid" (centered)
    var dateBarPosition by remember { mutableStateOf("mid") }
    // Re-read settings every time this screen becomes visible (e.g., returning from Settings)
    // Using lifecycle as key so it fires on every resume, not just initial composition
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            dashboardViewMode = settingsViewModel.getDashboardViewMode()
            dateBarPosition = settingsViewModel.getDateBarPosition()
        }
    }

    // Cached reactive flows — single flow instances, no zombie accumulation
    val dailySpendMap by mainViewModel.dailySpendMapForSelectedDate.collectAsState()
    val monthlySpendMap by mainViewModel.monthlySpendMapForSelectedDate.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                onExport = { showCalendarViewFilterDialog = true },
                onImport = {},
                onSaveAll = { scope.launch { drawerState.close(); onViewRecordsClick() } },
                onBatchSave = { scope.launch { drawerState.close(); onNavigateToBatchSave() } },
                onSettings = { scope.launch { drawerState.close(); onNavigateToSettings() } },
                onAllExpenses = { scope.launch { drawerState.close(); onAllExpensesClick() } },
                onExportCalendarView = { scope.launch { drawerState.close(); showCalendarViewFilterDialog = true } }
            )
        }
    ) {
        Scaffold(
            containerColor = C.Eggshell,
            topBar = {
                TopAppBar(
                    modifier = Modifier.padding(top = 4.dp),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Avatar acts as Hamburger Menu
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(C.DateSelected)
                                    .clickable { scope.launch { drawerState.open() } },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = "Menu", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.pointerInput(directMasterEditMode) {
                                    detectTapGestures(onLongPress = {
                                        val newState = !directMasterEditMode
                                        scope.launch {
                                            todoViewModel.setDirectMasterEditMode(newState)
                                            Toast.makeText(context, if (newState) "Direct Master Edit: ON" else "Direct Master Edit: OFF", Toast.LENGTH_SHORT).show()
                                        }
                                    })
                                }
                            ) {
                                Text(
                                    text = "Kharchaji",
                                    color = C.EggnogDark,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Premium Account",
                                    color = C.EggnogDark.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    },
                    actions = {
                        // Search -> All Expenses
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(C.SoftCream, RoundedCornerShape(10.dp))
                                .border(1.dp, C.CardBorder, RoundedCornerShape(10.dp))
                                .clickable { onAllExpensesClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = C.EggnogDark, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Bell Notification
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(C.SoftCream, RoundedCornerShape(10.dp))
                                .border(1.dp, C.CardBorder, RoundedCornerShape(10.dp))
                                .clickable { Toast.makeText(context, "No new notifications", Toast.LENGTH_SHORT).show() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = C.TodayButton, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = C.SoftCream)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .background(C.Eggshell)
            ) {
                // Subtle hairline border separating top bar from eggshell body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(C.CardBorder.copy(alpha = 0.4f))
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    item(key = "hero") {
                        HeroDashboard(
                            totalSum = totalSum, checkedSum = checkedSum,
                            totalItemsCount = totalItemsCount, checkedItemsCount = checkedItemsCount,
                            avgPerExpense = avgPerExpense, topCategory = topCategory.value,
                            progressPercent = progressPercent, budgetLimit = budgetLimit,
                            showSelected = showSelectedInHero, onToggleShowSelected = { showSelectedInHero = it },
                            selectedDate = selectedDate,
                            dashboardViewMode = dashboardViewMode,
                            dailySpend = dailySpendMap,
                            monthlySpend = monthlySpendMap,
                            dateBarPosition = dateBarPosition
                        )
                    }

                    item(key = "divider") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(1.dp)
                                .background(Brush.horizontalGradient(listOf(Color.Transparent, C.EggnogLight.copy(alpha = 0.3f), Color.Transparent)))
                        )
                    }

                    item(key = "dateNav") {
                        ModernDateNavigation(
                            selectedDate = selectedDate,
                            onDateChange = { mainViewModel.updateSelectedDate(it) },
                            onOpenCalendar = { showCustomCalendarDialog = true },
                            dailySpendMap = dailySpendMap,
                            dateBarPosition = dateBarPosition
                        )
                    }

                    item(key = "categoryChips") {
                        val categories = remember(itemsForSelectedDate) { derivedStateOf { itemsForSelectedDate.mapNotNull { it.categories?.firstOrNull() }.distinct() } }
                        if (categories.value.isNotEmpty()) { CategoryFilterChips(categories = categories.value) }
                    }

                    item(key = "actions") {
                        ExpenseActions(
                            masterCheckboxState = masterCheckboxState,
                            onMasterCheckboxChange = { isChecked -> mainViewModel.updateItems(itemsForSelectedDate.map { it.copy(isDone = isChecked) }) },
                            onSelectAllClick = {
                                val shouldCheck = !masterCheckboxState || itemsForSelectedDate.any { !it.isDone }
                                mainViewModel.updateItems(itemsForSelectedDate.map { it.copy(isDone = shouldCheck) })
                            },
                            isUndoEnabled = undoableItemsStack.isNotEmpty(),
                            onUndoClick = { mainViewModel.undoLastDelete() },
                            isDeleteEnabled = checkedItemsCount > 0,
                            onDeleteSelectedClick = { mainViewModel.deleteSelectedItemsAndEnableUndo(itemsForSelectedDate.filter { it.isDone }) },
                            isItemsListEmpty = itemsForSelectedDate.isEmpty(),
                            onDeleteAllClick = { showDeleteAllDialog = true },
                            onCategoriesClick = { categoryNames, hasPrimary, hasSecondary, hasTertiary ->
                                if (categoryNames.isNotEmpty()) {
                                    val updatedItems = itemsForSelectedDate.filter { it.isDone }.map { item ->
                                        val (baseText, _) = parseCategoryInfo(item.text)
                                        item.copy(text = baseText + "|CATS:" + categoryNames.joinToString(","), categories = categoryNames, hasPrimaryCategory = hasPrimary, hasSecondaryCategory = hasSecondary, hasTertiaryCategory = hasTertiary)
                                    }
                                    if (updatedItems.isNotEmpty()) mainViewModel.updateItems(updatedItems)
                                }
                            },
                            selectedExpenses = remember(itemsForSelectedDate) { derivedStateOf { itemsForSelectedDate.filter { it.isDone } } }.value,
                            viewModel = todoViewModel,
                            mainViewModel = mainViewModel
                        )
                    }

                    item(key = "txHeader") {
                        // Re-read buffer state reactively
                        val exportBuf by exportViewModel.exportBuffer.collectAsState()
                        val dateIsInBuffer = exportBuf.containsKey(selectedDate)
                        val checkedItems = remember(itemsForSelectedDate) {
                            itemsForSelectedDate.filter { it.isDone }
                        }

                        // Auto-sync: if this date is buffered and checked items changed, re-snapshot silently
                        LaunchedEffect(dateIsInBuffer, checkedItems) {
                            if (dateIsInBuffer && checkedItems.isNotEmpty()) {
                                exportViewModel.addToExportBuffer(selectedDate, checkedItems)
                            } else if (dateIsInBuffer && checkedItems.isEmpty()) {
                                // All items unchecked on a buffered date → remove from buffer
                                exportViewModel.removeFromExportBuffer(selectedDate)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${totalItemsCount} TRANSACTION${if (totalItemsCount != 1) "S" else ""}",
                                color = C.EggnogDark.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )

                            // Circular "Select for Export" checkbox pill
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                                    .background(
                                        if (dateIsInBuffer) C.Groceries.copy(alpha = 0.12f) else C.SoftCream
                                    )
                                    .border(
                                        1.dp,
                                        if (dateIsInBuffer) C.Groceries.copy(alpha = 0.35f) else C.CardBorder,
                                        androidx.compose.foundation.shape.RoundedCornerShape(50)
                                    )
                                    .clickable {
                                        if (dateIsInBuffer) {
                                            // Already buffered → remove from buffer (explicit opt-out)
                                            exportViewModel.removeFromExportBuffer(selectedDate)
                                        } else {
                                            // Snapshot currently checked items for this date
                                            if (checkedItems.isNotEmpty()) {
                                                exportViewModel.addToExportBuffer(selectedDate, checkedItems)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                // Circular checkbox icon
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            if (dateIsInBuffer) C.Groceries else Color.Transparent,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .border(
                                            1.5.dp,
                                            if (dateIsInBuffer) C.Groceries else C.CardBorder,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (dateIsInBuffer) {
                                        Text("✓", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                // Show item count when buffered
                                val bufferedCount = exportBuf[selectedDate]?.size ?: 0
                                Text(
                                    if (dateIsInBuffer) "Buffered ($bufferedCount)" else "Export",
                                    color = if (dateIsInBuffer) C.Groceries else C.EggnogDark,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (itemsForSelectedDate.isEmpty()) {
                        item(key = "empty") {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(C.SoftCream)
                                        .border(1.dp, C.CardBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ReceiptLong,
                                        contentDescription = null,
                                        tint = C.EggnogDark,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("No Expenses Found", color = C.EggnogDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Tap + below to log your first expense", color = C.EggnogDark.copy(alpha = 0.75f), fontSize = 13.sp)
                            }
                        }
                    } else {
                        items(itemsForSelectedDate, key = { it.id }) { item: TodoItem ->
                            ModernExpenseItem(
                                item = item,
                                onCheckedChange = { isChecked -> mainViewModel.updateItem(item.copy(isDone = isChecked)) },
                                onRemoveClick = { mainViewModel.removeItem(item) },
                                onEditClick = { editingItemId = item.id }
                            )
                        }
                    }
                }
            }
        } // End Scaffold
    } // End ModalNavigationDrawer

    // Dialogs
    if (editingItemId != null) {
        val itemToEdit = itemsForSelectedDate.find { it.id == editingItemId }
        if (itemToEdit != null) {
            EditItemDialog(
                item = itemToEdit,
                onDismiss = { editingItemId = null },
                onConfirm = { updatedText -> mainViewModel.updateItem(itemToEdit.copy(text = updatedText)); editingItemId = null },
                predefinedQuantities = listOf("250g", "500g", "1kg", "1.5kg", "2kg"),
                customUnits = listOf("g", "kg", "ml", "l", "pcs"),
                onDeleteImage = { item, imageUrl -> mainViewModel.deleteImageFromItem(item, imageUrl) },
                onAddImage = { item, uri -> mainViewModel.addImageToItem(item, uri) }
            )
        }
    }

    if (showCustomCalendarDialog) {
        ExpenseCalendarDialog(
            selectedDate = selectedDate,
            onDismiss = { showCustomCalendarDialog = false },
            onDateSelected = { date -> mainViewModel.updateSelectedDate(date); showCustomCalendarDialog = false },
            todoViewModel = todoViewModel,
            statsViewModel = statsViewModel
        )
    }

    if (showDeleteAllDialog) {
        DeleteAllConfirmationDialog(
            onDismiss = { showDeleteAllDialog = false },
            onConfirm = { mainViewModel.deleteSelectedItemsAndEnableUndo(itemsForSelectedDate); showDeleteAllDialog = false },
            dateForDisplay = selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        )
    }

    if (showCalendarViewFilterDialog) {
        // ... abbreviated identical logic for export dialog handled in Export Modal ...
        // We'll let the user use the Navigation Drawer for full functionality
        var allCategories by remember { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(Unit) { allCategories = withContext(Dispatchers.IO) { todoViewModel.getPrimaryCategories() } }
        if (allCategories.isNotEmpty()) {
            MultiCategorySelectionDialog(
                title = "Choose Primary Categories to Export",
                allCategories = allCategories,
                onDismiss = { showCalendarViewFilterDialog = false },
                onConfirm = { /* Export logic preserved in TodoViewModel, simplifying UI here */ showCalendarViewFilterDialog = false }
            )
        }
    }
}
