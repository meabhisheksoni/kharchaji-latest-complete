package com.example.monday.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.monday.ExpenseCategory
import com.example.monday.TodoViewModel
import com.example.monday.ui.components.DefaultCategories
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TodoViewModel,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // State for category management
    var showPrimaryCategories by remember { mutableStateOf(true) }
    var showSecondaryCategories by remember { mutableStateOf(true) }
    var showTertiaryCategories by remember { mutableStateOf(true) }
    
    var showEditCategoryDialog by remember { mutableStateOf<Triple<String, ExpenseCategory?, ExpenseCategory?>>(Triple("", null, null)) }
    
    // Get categories from view model's StateFlows
    val primaryCategories by viewModel.primaryCategories.collectAsState()
    val secondaryCategories by viewModel.secondaryCategories.collectAsState()
    val tertiaryCategories by viewModel.tertiaryCategories.collectAsState()
    
    // Load visibility preferences
    LaunchedEffect(Unit) {
        showPrimaryCategories = viewModel.getCategoryVisibilitySetting("primary") ?: true
        showSecondaryCategories = viewModel.getCategoryVisibilitySetting("secondary") ?: true
        showTertiaryCategories = viewModel.getCategoryVisibilitySetting("tertiary") ?: true
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            
            // Category visibility settings
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Category Visibility",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Show Primary Categories (People)",
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = showPrimaryCategories,
                                onCheckedChange = { 
                                    showPrimaryCategories = it
                                    scope.launch {
                                        viewModel.saveCategoryVisibilitySetting("primary", it)
                                    }
                                }
                            )
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Show Secondary Categories (Purpose)",
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = showSecondaryCategories,
                                onCheckedChange = { 
                                    showSecondaryCategories = it
                                    scope.launch {
                                        viewModel.saveCategoryVisibilitySetting("secondary", it)
                                    }
                                }
                            )
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Show Tertiary Categories (Type)",
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = showTertiaryCategories,
                                onCheckedChange = { 
                                    showTertiaryCategories = it
                                    scope.launch {
                                        viewModel.saveCategoryVisibilitySetting("tertiary", it)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            // Primary Categories Management
            if (showPrimaryCategories) {
                item {
                    CategorySection(
                        title = "Primary Categories (People)",
                        categories = primaryCategories,
                        onAddCategory = {
                            showEditCategoryDialog = Triple("primary", null, null)
                        },
                        onEditCategory = { category ->
                            showEditCategoryDialog = Triple("primary", category, null)
                        },
                        onDeleteCategory = { category ->
                            scope.launch {
                                viewModel.deleteCategory(category, "primary")
                            }
                        }
                    )
                }
            }
            
            // Secondary Categories Management
            if (showSecondaryCategories) {
                item {
                    CategorySection(
                        title = "Secondary Categories (Purpose)",
                        categories = secondaryCategories,
                        onAddCategory = {
                            showEditCategoryDialog = Triple("secondary", null, null)
                        },
                        onEditCategory = { category ->
                            showEditCategoryDialog = Triple("secondary", category, null)
                        },
                        onDeleteCategory = { category ->
                            scope.launch {
                                viewModel.deleteCategory(category, "secondary")
                            }
                        }
                    )
                }
            }
            
            // Tertiary Categories Management
            if (showTertiaryCategories) {
                item {
                    CategorySection(
                        title = "Tertiary Categories (Type)",
                        categories = tertiaryCategories,
                        onAddCategory = {
                            showEditCategoryDialog = Triple("tertiary", null, null)
                        },
                        onEditCategory = { category ->
                            showEditCategoryDialog = Triple("tertiary", category, null)
                        },
                        onDeleteCategory = { category ->
                            scope.launch {
                                viewModel.deleteCategory(category, "tertiary")
                            }
                        }
                    )
                }
            }
        }
    }
    
    // Edit Category Dialog
    if (showEditCategoryDialog.first.isNotEmpty()) {
        val (categoryType, categoryToEdit, _) = showEditCategoryDialog
        
        EditCategoryDialog(
            category = categoryToEdit,
            onDismiss = { showEditCategoryDialog = Triple("", null, null) },
            onSave = { name, icon ->
                val newCategory = ExpenseCategory(name, icon)
                val currentCategories = when (categoryType) {
                    "primary" -> primaryCategories
                    "secondary" -> secondaryCategories
                    "tertiary" -> tertiaryCategories
                    else -> emptyList()
                }

                scope.launch {
                    if (categoryToEdit != null) {
                        // This is an edit
                        viewModel.updateCategory(categoryToEdit, newCategory, categoryType)
                    } else {
                        // This is an add
                        val updatedList = currentCategories + newCategory
                        viewModel.saveCategories(categoryType, updatedList)
                    }
                }
                
                showEditCategoryDialog = Triple("", null, null)
            }
        )
    }
}

@Composable
fun CategorySection(
    title: String,
    categories: List<ExpenseCategory>,
    onAddCategory: () -> Unit,
    onEditCategory: (ExpenseCategory) -> Unit,
    onDeleteCategory: (ExpenseCategory) -> Unit
) {
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
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
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
                
                if (categories.isEmpty()) {
                    Text(
                        text = "No categories added yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
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
                    
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null
                    )
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