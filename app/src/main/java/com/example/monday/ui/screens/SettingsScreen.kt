package com.example.monday.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.monday.ExpenseCategory
import com.example.monday.TodoViewModel
import com.example.monday.ui.components.DefaultCategories
import com.example.monday.ui.components.DeleteCategoryConfirmDialog
import com.example.monday.ui.components.EnhancedCategorySection
import kotlinx.coroutines.launch
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.monday.core.utils.*
import com.example.monday.data.models.TodoItem
import com.example.monday.ui.overlay.OverlayHelper
import com.example.monday.ui.screens.settings.*
import com.example.monday.ui.modern.ModernColors
import com.example.monday.viewmodels.SettingsViewModel
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Settings screen — modularized into 5 categories using a square grid.
 */
enum class SettingsCategory(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Outlined.Dashboard),
    BACKUP("Backup & Restore", Icons.Outlined.SettingsBackupRestore),
    QUICK_ACCESS("Quick Access", Icons.Outlined.Bolt),
    PAYMENT_MONITOR("Payment Monitor", Icons.Outlined.NotificationsActive),
    CATEGORIES("Categories", Icons.Outlined.Category)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TodoViewModel, mainViewModel: com.example.monday.viewmodels.MainViewModel,
    statsViewModel: com.example.monday.viewmodels.StatsViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ── State ────────────────────────────────────────────────────────
    var currentCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    
    var showPrimaryCategories by remember { mutableStateOf(true) }
    var showSecondaryCategories by remember { mutableStateOf(true) }
    var showTertiaryCategories by remember { mutableStateOf(true) }
    var enablePaymentMonitor by remember { mutableStateOf(true) }
    var enablePaymentVibration by remember { mutableStateOf(true) }
    var enableAutoMasterSave by remember { mutableStateOf(false) }
    var dashboardViewMode by remember { mutableStateOf("both") }
    var dateBarPosition by remember { mutableStateOf("mid") }

    BackHandler(enabled = currentCategory != null) {
        currentCategory = null
    }

    var showEditCategoryDialog by remember { mutableStateOf<Triple<String, ExpenseCategory?, ExpenseCategory?>>(Triple("", null, null)) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Pair<ExpenseCategory?, String>?>(null) }

    val primaryCategories by viewModel.primaryCategories.collectAsState()
    val secondaryCategories by viewModel.secondaryCategories.collectAsState()
    val tertiaryCategories by viewModel.tertiaryCategories.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lastCategoryAction by viewModel.lastCategoryAction.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) { importBackup(context, uri, viewModel, mainViewModel, statsViewModel) }
    }

    // ── Load Preferences ─────────────────────────────────────────────
    LaunchedEffect(Unit) {
        showPrimaryCategories = settingsViewModel.getCategoryVisibilitySetting("primary") ?: true
        showSecondaryCategories = settingsViewModel.getCategoryVisibilitySetting("secondary") ?: true
        showTertiaryCategories = settingsViewModel.getCategoryVisibilitySetting("tertiary") ?: true
        enablePaymentMonitor = settingsViewModel.getPaymentMonitorSetting("enable_monitor") ?: true
        enablePaymentVibration = settingsViewModel.getPaymentMonitorSetting("enable_vibration") ?: true
        enableAutoMasterSave = settingsViewModel.getPaymentMonitorSetting("auto_master_save") ?: false
        dashboardViewMode = settingsViewModel.getDashboardViewMode()
        dateBarPosition = settingsViewModel.getDateBarPosition()
    }

    // ── Scaffold ─────────────────────────────────────────────────────
    Scaffold(
        containerColor = ModernColors.Eggshell,
        topBar = {
            TopAppBar(
                title = { Text(currentCategory?.title ?: "Settings", color = ModernColors.DateText) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ModernColors.Eggshell,
                    navigationIconContentColor = ModernColors.DateText,
                    actionIconContentColor = ModernColors.DateText
                ),
                navigationIcon = {
                    IconButton(onClick = { 
                        if (currentCategory != null) currentCategory = null 
                        else onNavigateBack() 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (lastCategoryAction != null) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    viewModel.undoLastCategoryAction()
                                    snackbarHostState.showSnackbar("Action undone")
                                }
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo Last Category Change",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (currentCategory == null) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = paddingValues,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(SettingsCategory.values()) { category ->
                    Card(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { currentCategory = category },
                        colors = CardDefaults.cardColors(containerColor = ModernColors.CardBg)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = category.title,
                                modifier = Modifier.size(48.dp),
                                tint = ModernColors.EggnogDark
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ModernColors.DateText
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                when (currentCategory) {
                    SettingsCategory.DASHBOARD -> {
                        dashboardSettingsSection(
                            dashboardViewMode = dashboardViewMode,
                            dateBarPosition = dateBarPosition,
                            scope = scope,
                            settingsViewModel = settingsViewModel,
                            snackbarHostState = snackbarHostState,
                            onDashboardModeChange = { dashboardViewMode = it },
                            onDateBarPositionChange = { dateBarPosition = it }
                        )
                    }
                    SettingsCategory.BACKUP -> {
                        backupRestoreSection(
                            enableAutoMasterSave = enableAutoMasterSave,
                            context = context,
                            scope = scope,
                            viewModel = viewModel, mainViewModel = mainViewModel, statsViewModel = statsViewModel,
                            settingsViewModel = settingsViewModel,
                            snackbarHostState = snackbarHostState,
                            onAutoMasterSaveChange = { enableAutoMasterSave = it },
                            onImportClick = { filePickerLauncher.launch("*/*") }
                        )
                    }
                    SettingsCategory.QUICK_ACCESS -> {
                        quickAccessSection(
                            context = context,
                            scope = scope,
                            snackbarHostState = snackbarHostState
                        )
                    }
                    SettingsCategory.PAYMENT_MONITOR -> {
                        paymentMonitorSection(
                            context = context,
                            enablePaymentMonitor = enablePaymentMonitor,
                            enablePaymentVibration = enablePaymentVibration,
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                            settingsViewModel = settingsViewModel,
                            onMonitorChange = { enablePaymentMonitor = it },
                            onVibrationChange = { enablePaymentVibration = it }
                        )
                    }
                    SettingsCategory.CATEGORIES -> {
                        categoriesSection(
                            showPrimary = showPrimaryCategories,
                            showSecondary = showSecondaryCategories,
                            showTertiary = showTertiaryCategories,
                            primaryCategories = primaryCategories,
                            secondaryCategories = secondaryCategories,
                            tertiaryCategories = tertiaryCategories,
                            scope = scope,
                            viewModel = viewModel,
                            settingsViewModel = settingsViewModel,
                            onPrimaryVisibilityChange = { showPrimaryCategories = it },
                            onSecondaryVisibilityChange = { showSecondaryCategories = it },
                            onTertiaryVisibilityChange = { showTertiaryCategories = it },
                            onEditCategory = { type, category ->
                                showEditCategoryDialog = Triple(type, category, null)
                            },
                            onDeleteCategory = { category, type ->
                                showDeleteConfirmDialog = Pair(category, type)
                            }
                        )
                    }
                    null -> {}
                }
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────
    if (showEditCategoryDialog.first.isNotEmpty()) {
        val (categoryType, categoryToEdit, _) = showEditCategoryDialog
        EditCategoryDialog(
            category = categoryToEdit,
            onDismiss = { showEditCategoryDialog = Triple("", null, null) },
            onSave = { name, icon ->
                val newCategory = ExpenseCategory(name, icon)
                scope.launch {
                    if (categoryToEdit != null) {
                        viewModel.updateCategory(categoryToEdit, newCategory, categoryType)
                        if (categoryToEdit.name != name) {
                            snackbarHostState.showSnackbar("Category '${categoryToEdit.name}' renamed to '$name' throughout the app")
                        }
                    } else {
                        viewModel.addCategory(newCategory, categoryType)
                        snackbarHostState.showSnackbar("Category '$name' added")
                    }
                }
                showEditCategoryDialog = Triple("", null, null)
            }
        )
    }

    showDeleteConfirmDialog?.let { (category, type) ->
        if (category != null) {
            DeleteCategoryConfirmDialog(
                category = category,
                onDismiss = { showDeleteConfirmDialog = null },
                onConfirmDelete = { removeFromExpenses ->
                    scope.launch {
                        viewModel.deleteCategoryWithOptions(category, type, removeFromExpenses)
                        val message = if (removeFromExpenses) {
                            "Category '${category.name}' removed from all expenses"
                        } else {
                            "Category '${category.name}' removed from category list only"
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                    showDeleteConfirmDialog = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoryDialog(
    category: ExpenseCategory?,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: ImageVector) -> Unit
) {
    val isNewCategory = category == null
    var categoryName by remember { mutableStateOf(category?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(category?.icon ?: Icons.Outlined.Category) }
    var showIconPicker by remember { mutableStateOf(false) }

    val availableIcons = listOf(
        Icons.Outlined.Person to "Person",
        Icons.Outlined.People to "People",
        Icons.Outlined.Home to "Home",
        Icons.Outlined.ShoppingCart to "Shopping Cart",
        Icons.Outlined.Restaurant to "Food",
        Icons.Outlined.DinnerDining to "Dining",
        Icons.Outlined.DirectionsCar to "Transport",
        Icons.Outlined.Flight to "Travel",
        Icons.Outlined.School to "Education",
        Icons.Outlined.Celebration to "Celebration",
        Icons.Outlined.Redeem to "Gift",
        Icons.Outlined.Handyman to "Maintenance",
        Icons.Outlined.Receipt to "Bills",
        Icons.Outlined.Movie to "Entertainment",
        Icons.Outlined.LocalMall to "Shopping",
        Icons.Outlined.Medication to "Health",
        Icons.Outlined.MoreHoriz to "Other"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNewCategory) "Add Category" else "Edit Category") },
        text = {
            Column {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                Text(
                    text = "Category Icon",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showIconPicker = true }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = selectedIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = availableIcons.find { it.first == selectedIcon }?.second ?: "Select Icon",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                }

                if (showIconPicker) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .padding(8.dp)
                        ) {
                            items(availableIcons) { (icon, name) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedIcon = icon
                                            showIconPicker = false
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (icon == selectedIcon)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (icon == selectedIcon)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(categoryName, selectedIcon) },
                enabled = categoryName.isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
