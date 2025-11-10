package com.example.monday.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.monday.ExpenseCategory
import com.example.monday.TodoViewModel

// Data class for category hierarchy
data class CategoryHierarchy(
    val primaryCategories: List<ExpenseCategory>,
    val secondaryCategories: List<ExpenseCategory>,
    val tertiaryCategories: List<ExpenseCategory>
)

@Composable
fun CategorySelectionPopup(
    onDismiss: () -> Unit,
    onCategoriesSelected: (List<String>, Boolean, Boolean, Boolean) -> Unit,
    initialSelectedCategories: List<String> = emptyList(),
    viewModel: TodoViewModel
) {
    // Fetch categories from ViewModel
    val primaryCategories = viewModel.primaryCategories.collectAsState().value
    val secondaryCategories = viewModel.secondaryCategories.collectAsState().value
    val tertiaryCategories = viewModel.tertiaryCategories.collectAsState().value
    
    // State for selected categories
    var selectedPrimaryCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var selectedSecondaryCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    // Change from Set to single nullable ExpenseCategory
    var selectedTertiaryCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    
    // Get recently selected categories
    val recentPrimaryCategory = viewModel.getRecentlySelectedCategory("primary")
    val recentSecondaryCategory = viewModel.getRecentlySelectedCategory("secondary")
    val recentTertiaryCategory = viewModel.getRecentlySelectedCategory("tertiary")
    
    // Process initial selected categories and recent categories
    LaunchedEffect(initialSelectedCategories, primaryCategories, secondaryCategories, tertiaryCategories) {
        // Clear previous selections
        selectedPrimaryCategory = null
        selectedSecondaryCategory = null
        selectedTertiaryCategory = null

        // First check passed categories
        initialSelectedCategories.forEach { categoryName ->
            // Check in tertiary categories first (most common)
            tertiaryCategories.find { it.name == categoryName }?.let {
                selectedTertiaryCategory = it
            }
            
            // Check in secondary categories
            secondaryCategories.find { it.name == categoryName }?.let {
                selectedSecondaryCategory = it
            }
            
            // Check in primary categories
            primaryCategories.find { it.name == categoryName }?.let {
                selectedPrimaryCategory = it
            }
        }
        
        // If no primary category was selected, try to use the recent one
        if (selectedPrimaryCategory == null && recentPrimaryCategory != null) {
            primaryCategories.find { it.name == recentPrimaryCategory }?.let {
                selectedPrimaryCategory = it
            }
        }
        
        // If no secondary category was selected, try to use the recent one
        if (selectedSecondaryCategory == null && recentSecondaryCategory != null) {
            secondaryCategories.find { it.name == recentSecondaryCategory }?.let {
                selectedSecondaryCategory = it
            }
        }
        
        // If no tertiary category was selected, try to use the recent one
        if (selectedTertiaryCategory == null && recentTertiaryCategory != null) {
            tertiaryCategories.find { it.name == recentTertiaryCategory }?.let {
                selectedTertiaryCategory = it
            }
        }
    }

    // New states for visibility settings
    val showPrimaryCategories by remember(viewModel) {
        mutableStateOf(viewModel.getCategoryVisibilitySetting("primary") ?: true)
    }
    val showSecondaryCategories by remember(viewModel) {
        mutableStateOf(viewModel.getCategoryVisibilitySetting("secondary") ?: true)
    }
    val showTertiaryCategories by remember(viewModel) {
        mutableStateOf(viewModel.getCategoryVisibilitySetting("tertiary") ?: true)
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.6f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Categories",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                // Category columns
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Primary Categories Column
                    if (showPrimaryCategories) {
                        CategoryColumn(
                            title = "Person",
                            categories = primaryCategories,
                            selectedCategory = selectedPrimaryCategory,
                            onCategorySelected = { category ->
                                selectedPrimaryCategory = if (selectedPrimaryCategory == category) null else category
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Divider
                    if (showPrimaryCategories && showSecondaryCategories) {
                        CustomDivider()
                    }
                    
                    // Secondary Categories Column
                    if (showSecondaryCategories) {
                        CategoryColumn(
                            title = "Purpose",
                            categories = secondaryCategories,
                            selectedCategory = selectedSecondaryCategory,
                            onCategorySelected = { category ->
                                selectedSecondaryCategory = if (selectedSecondaryCategory == category) null else category
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Divider
                    if (showSecondaryCategories && showTertiaryCategories) {
                        CustomDivider()
                    }
                    
                    // Tertiary Categories Column (now single-select)
                    if (showTertiaryCategories) {
                        CategoryColumn(
                            title = "Category",
                            categories = tertiaryCategories,
                            selectedCategory = selectedTertiaryCategory,
                            onCategorySelected = { category ->
                                selectedTertiaryCategory = if (selectedTertiaryCategory == category) null else category
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Button(
                        onClick = {
                            val selectedCategories = mutableListOf<String>()
                            val hasPrimary = selectedPrimaryCategory != null
                            val hasSecondary = selectedSecondaryCategory != null
                            val hasTertiary = selectedTertiaryCategory != null
                            
                            // Add primary category if selected
                            selectedPrimaryCategory?.let {
                                selectedCategories.add(it.name)
                                // Save as recently selected
                                viewModel.saveRecentlySelectedCategory("primary", it.name)
                            }
                            
                            // Add secondary category if selected
                            selectedSecondaryCategory?.let {
                                selectedCategories.add(it.name)
                                // Save as recently selected
                                viewModel.saveRecentlySelectedCategory("secondary", it.name)
                            }
                            
                            // Add tertiary category if selected
                            selectedTertiaryCategory?.let {
                                selectedCategories.add(it.name)
                                // Save as recently selected
                                viewModel.saveRecentlySelectedCategory("tertiary", it.name)
                            }
                            
                            // Pass the category flags along with the names
                            onCategoriesSelected(selectedCategories, hasPrimary, hasSecondary, hasTertiary)
                            onDismiss()
                        },
                        enabled = selectedPrimaryCategory != null || 
                                 selectedSecondaryCategory != null || 
                                 selectedTertiaryCategory != null
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
fun CustomDivider() {
    HorizontalDivider(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .padding(horizontal = 1.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun CategoryColumn(
    title: String,
    categories: List<ExpenseCategory>,
    selectedCategory: ExpenseCategory?,
    onCategorySelected: (ExpenseCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            textAlign = TextAlign.Center
        )
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(categories) { category ->
                val isSelected = category == selectedCategory
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 0.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onCategorySelected(category) }
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary 
                              else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(2.dp))
                    
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
} 