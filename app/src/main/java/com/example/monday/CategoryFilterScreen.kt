package com.example.monday

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.map
import com.example.monday.intelligentlyCategorize
import com.example.monday.generateCategoryColors
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.interaction.MutableInteractionSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterScreen(
    todoViewModel: TodoViewModel,
    onNavigateBack: () -> Unit,
    onApplyFilters: (List<String>) -> Unit,
    initialSelectedCategories: List<String>
) {
    // Get all categories from TodoViewModel instead of TodoItems
    val primaryCategories = todoViewModel.primaryCategories.collectAsState().value.map { it.name }
    val secondaryCategories = todoViewModel.secondaryCategories.collectAsState().value.map { it.name }
    val tertiaryCategories = todoViewModel.tertiaryCategories.collectAsState().value.map { it.name }
    
    // Also get categories from TodoItems to ensure we include any categories that might be in use
    // but not in the settings (for backward compatibility)
    val itemCategories by todoViewModel.todoItems.map { items ->
        items.flatMap { parseCategoryInfo(it.text).second }.toSet()
    }.collectAsState(initial = emptySet())
    
    // Combine both sources of categories
    val allCategories = remember(primaryCategories, secondaryCategories, tertiaryCategories, itemCategories) {
        val merged = primaryCategories + secondaryCategories + tertiaryCategories + itemCategories
        val seen = mutableSetOf<String>()
        merged.map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { seen.add(it.lowercase()) }
            .toSet()
    }

    val categoryColors by remember(allCategories) {
        mutableStateOf(generateCategoryColors(allCategories.toList()))
    }

    // Use all available categories for filtering
    val primary = remember(allCategories, primaryCategories, itemCategories) {
        // Include categories from ViewModel's primary list
        val fromViewModel = primaryCategories.toSet()
        
        // Include categories from items that match primary category patterns but aren't in any ViewModel list
        val fromItems = itemCategories.filter { category ->
            val categoryType = getCategoryType(category)
            categoryType == CategoryType.PRIMARY && 
            !primaryCategories.contains(category) && 
            !secondaryCategories.contains(category) && 
            !tertiaryCategories.contains(category)
        }
        
        (fromViewModel + fromItems).distinctBy { it.lowercase() }.sorted()
    }
    
    val secondary = remember(allCategories, secondaryCategories, itemCategories) {
        // Include categories from ViewModel's secondary list
        val fromViewModel = secondaryCategories.toSet()
        
        // Include categories from items that match secondary category patterns but aren't in any ViewModel list
        val fromItems = itemCategories.filter { category ->
            val categoryType = getCategoryType(category)
            categoryType == CategoryType.SECONDARY && 
            !primaryCategories.contains(category) && 
            !secondaryCategories.contains(category) && 
            !tertiaryCategories.contains(category)
        }
        
        (fromViewModel + fromItems).distinctBy { it.lowercase() }.sorted()
    }
    
    val tertiary = remember(allCategories, tertiaryCategories, itemCategories) {
        // Include categories from ViewModel's tertiary list
        val fromViewModel = tertiaryCategories.toSet()
        
        // Include categories from items that aren't in any ViewModel list and don't match primary/secondary patterns
        val fromItems = itemCategories.filter { category ->
            val categoryType = getCategoryType(category)
            categoryType == CategoryType.TERTIARY && 
            !primaryCategories.contains(category) && 
            !secondaryCategories.contains(category) && 
            !tertiaryCategories.contains(category)
        }
        
        (fromViewModel + fromItems).distinctBy { it.lowercase() }.sorted()
    }

    var selectedCategories by remember { mutableStateOf(initialSelectedCategories.toSet()) }
    val toggleCategory = { category: String ->
        selectedCategories = if (selectedCategories.contains(category)) {
            selectedCategories - category
        } else {
            selectedCategories + category
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filter by Category") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CategoryColumn(modifier = Modifier.weight(1f), title = "Primary", categories = primary, selectedCategories = selectedCategories, onCategoryToggle = toggleCategory, categoryColors = categoryColors)
                CategoryColumn(modifier = Modifier.weight(1f), title = "Secondary", categories = secondary, selectedCategories = selectedCategories, onCategoryToggle = toggleCategory, categoryColors = categoryColors)
                CategoryColumn(
                    modifier = Modifier.weight(1f),
                    title = "Tertiary",
                    categories = tertiary,
                    selectedCategories = selectedCategories,
                    onCategoryToggle = toggleCategory,
                    categoryColors = categoryColors,
                    masterControlList = tertiary
                ) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { toggleCategory("Uncategorized") }
                    ) {
                        Checkbox(
                            checked = selectedCategories.contains("Uncategorized"),
                            onCheckedChange = { _ -> toggleCategory("Uncategorized") }
                        )
                        Text(
                            text = "Uncategorized",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp),
                            color = Color.Gray
                        )
                    }
                }
            }
            Button(
                onClick = { onApplyFilters(selectedCategories.toList()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Apply")
            }
        }
    }
}

@Composable
fun CategoryColumn(
    modifier: Modifier = Modifier,
    title: String,
    categories: List<String>,
    selectedCategories: Set<String>,
    onCategoryToggle: (String) -> Unit,
    categoryColors: Map<String, Color>,
    masterControlList: List<String> = categories,
    footer: @Composable () -> Unit = {}
) {
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        
        // Master checkbox row
        val allSelected = masterControlList.all { it in selectedCategories }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // If all selected, deselect all. If any or none selected, select all
                    masterControlList.forEach { category ->
                        if (allSelected) {
                            if (selectedCategories.contains(category)) onCategoryToggle(category)
                        } else {
                            if (!selectedCategories.contains(category)) onCategoryToggle(category)
                        }
                    }
                }
        ) {
            Checkbox(
                checked = allSelected,
                onCheckedChange = { checked ->
                    masterControlList.forEach { category ->
                        if (checked && !selectedCategories.contains(category)) {
                            onCategoryToggle(category)
                        } else if (!checked && selectedCategories.contains(category)) {
                            onCategoryToggle(category)
                        }
                    }
                },
                interactionSource = remember { MutableInteractionSource() }
            )
            Text(
                text = "Select All",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        
        Divider(modifier = Modifier.padding(vertical = 4.dp))
        
        LazyColumn {
            items(categories) { category ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onCategoryToggle(category) }
                ) {
                    Checkbox(
                        checked = selectedCategories.contains(category),
                        onCheckedChange = { _ -> onCategoryToggle(category) }
                    )
                    Text(
                        text = category,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp),
                        color = categoryColors[category] ?: Color.Unspecified
                    )
                }
            }
        }

        footer()
    }
}

/*
fun intelligentlyCategorize(categories: Set<String>): Triple<List<String>, List<String>, List<String>> {
    val primaryKeywords = setOf("abhishek", "kharcha", "papa", "priya")
    val secondaryKeywords = setOf("education", "home", "travel")
    
    val primary = mutableListOf<String>()
    val secondary = mutableListOf<String>()
    val tertiary = mutableListOf<String>()

    categories.forEach { category ->
        val lowerCategory = category.lowercase()
        when {
            primaryKeywords.any { lowerCategory.contains(it) } -> primary.add(category)
            secondaryKeywords.any { lowerCategory.contains(it) } -> secondary.add(category)
            else -> tertiary.add(category)
        }
    }
    return Triple(primary.sorted(), secondary.sorted(), tertiary.sorted())
} 
*/ 