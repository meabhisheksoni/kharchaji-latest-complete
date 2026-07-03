package com.example.monday.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.monday.ExpenseCategory
import com.example.monday.TodoViewModel
import com.example.monday.viewmodels.SettingsViewModel
import com.example.monday.ui.components.EnhancedCategorySection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.monday.ui.modern.ModernColors

/**
 * Category visibility toggles + category management (add/edit/delete/reorder).
 */
fun LazyListScope.categoriesSection(
    showPrimary: Boolean,
    showSecondary: Boolean,
    showTertiary: Boolean,
    primaryCategories: List<ExpenseCategory>,
    secondaryCategories: List<ExpenseCategory>,
    tertiaryCategories: List<ExpenseCategory>,
    scope: CoroutineScope,
    viewModel: TodoViewModel,
    settingsViewModel: SettingsViewModel,
    onPrimaryVisibilityChange: (Boolean) -> Unit,
    onSecondaryVisibilityChange: (Boolean) -> Unit,
    onTertiaryVisibilityChange: (Boolean) -> Unit,
    onEditCategory: (type: String, category: ExpenseCategory?) -> Unit,
    onDeleteCategory: (category: ExpenseCategory, type: String) -> Unit
) {
    item {
        Text(
            text = "Categories",
            style = MaterialTheme.typography.titleLarge,
            color = ModernColors.DateText,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }

    // Category Visibility Toggles
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = ModernColors.CardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Category Visibility",
                    style = MaterialTheme.typography.titleMedium,
                    color = ModernColors.DateText,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                CategoryVisibilityToggle(
                    label = "Show Primary Categories (People)",
                    checked = showPrimary,
                    onCheckedChange = {
                        onPrimaryVisibilityChange(it)
                        settingsViewModel.saveCategoryVisibilitySetting("primary", it) 
                    }
                )
                CategoryVisibilityToggle(
                    label = "Show Secondary Categories (Purpose)",
                    checked = showSecondary,
                    onCheckedChange = {
                        onSecondaryVisibilityChange(it)
                        settingsViewModel.saveCategoryVisibilitySetting("secondary", it)
                    }
                )
                CategoryVisibilityToggle(
                    label = "Show Tertiary Categories (Type)",
                    checked = showTertiary,
                    onCheckedChange = {
                        onTertiaryVisibilityChange(it)
                        settingsViewModel.saveCategoryVisibilitySetting("tertiary", it)
                    }
                )
            }
        }
    }

    // Primary Categories
    if (showPrimary) {
        item {
            CategoryManagementSection(
                title = "Primary Categories (People)",
                categories = primaryCategories,
                type = "primary",
                scope = scope,
                viewModel = viewModel,
                onEditCategory = onEditCategory,
                onDeleteCategory = onDeleteCategory
            )
        }
    }

    // Secondary Categories
    if (showSecondary) {
        item {
            CategoryManagementSection(
                title = "Secondary Categories (Purpose)",
                categories = secondaryCategories,
                type = "secondary",
                scope = scope,
                viewModel = viewModel,
                onEditCategory = onEditCategory,
                onDeleteCategory = onDeleteCategory
            )
        }
    }

    // Tertiary Categories
    if (showTertiary) {
        item {
            CategoryManagementSection(
                title = "Tertiary Categories (Type)",
                categories = tertiaryCategories,
                type = "tertiary",
                scope = scope,
                viewModel = viewModel,
                onEditCategory = onEditCategory,
                onDeleteCategory = onDeleteCategory
            )
        }
    }
}

@Composable
private fun CategoryVisibilityToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f), color = ModernColors.DateText)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ModernColors.Eggshell,
                checkedTrackColor = ModernColors.EggnogDark
            )
        )
    }
}

@Composable
private fun CategoryManagementSection(
    title: String,
    categories: List<ExpenseCategory>,
    type: String,
    scope: CoroutineScope,
    viewModel: TodoViewModel,
    onEditCategory: (type: String, category: ExpenseCategory?) -> Unit,
    onDeleteCategory: (category: ExpenseCategory, type: String) -> Unit
) {
    EnhancedCategorySection(
        title = title,
        categories = categories,
        onAddCategory = { onEditCategory(type, null) },
        onEditCategory = { category -> onEditCategory(type, category) },
        onDeleteCategory = { category -> onDeleteCategory(category, type) },
        onMoveCategory = { fromIndex, toIndex ->
            if (fromIndex != toIndex) {
                scope.launch { viewModel.moveCategoryTo(type, fromIndex, toIndex) }
            }
        }
    )
}
