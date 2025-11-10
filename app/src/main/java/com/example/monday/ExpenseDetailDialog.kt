package com.example.monday

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ExpenseDetailDialog(
    onDismiss: () -> Unit,
    items: List<TodoItem>,
    selectedCategories: List<String>,
    categoryColors: Map<String, Color>,
    titleOverride: String = "Expense Details"
) {
    // Use remember to pre-compute expensive operations
    val activeGroups by remember(selectedCategories) {
        val (primary, secondary, tertiary) = intelligentlyCategorize(selectedCategories.toSet())
        mutableStateOf(listOf(
            "Primary" to primary,
            "Secondary" to secondary,
            "Tertiary" to tertiary
        ).filter { it.second.isNotEmpty() })
    }
    
    // Pre-compute totals and data for the dialog
    val dialogData by remember(items, selectedCategories, activeGroups) {
        mutableStateOf(computeDialogData(items, selectedCategories, activeGroups, categoryColors))
    }
    
    // State to track which category detail dialog to show
    var showCategoryDetail by remember { mutableStateOf(false) }
    var selectedCategoryData by remember { mutableStateOf<DetailRowData?>(null) }
    
    // Show category detail dialog if a category is selected
    if (showCategoryDetail && selectedCategoryData != null) {
        val data = selectedCategoryData!!
        val allCategories = data.primaryCategories + data.secondaryCategories + data.tertiaryCategories
        val categoryName = when {
            data.title.isNotEmpty() -> data.title
            data.isIntersection -> allCategories.joinToString(" ∩ ")
            else -> {
                data.tertiaryCategories.firstOrNull() ?: 
                data.secondaryCategories.firstOrNull() ?: 
                data.primaryCategories.firstOrNull() ?: ""
            }
        }
        
        CategoryExpenseDetailDialog(
            onDismiss = { showCategoryDetail = false },
            items = items,
            categoryName = categoryName,
            isIntersection = data.isIntersection,
            categories = allCategories,
            color = data.color
        )
    }
    
    // Use Dialog instead of AlertDialog for better performance
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = titleOverride,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Use LaunchedEffect to ensure smooth animation when dialog appears
                LaunchedEffect(Unit) {
                    // This empty block ensures the dialog content is rendered in the next frame
                }
                
                // Render pre-computed data
                dialogData.forEach { detailData ->
                    DetailRow(
                        data = detailData,
                        onClick = {
                            selectedCategoryData = detailData
                            showCategoryDetail = true
                        }
                    )
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

// Helper function to pre-compute dialog data
private fun computeDialogData(
    items: List<TodoItem>,
    selectedCategories: List<String>,
    activeGroups: List<Pair<String, List<String>>>,
    categoryColors: Map<String, Color>
): List<DetailRowData> {
    return if (activeGroups.size > 1) {
        // Intersection case
        val comboGroups = activeGroups.map { it.second }
        val combos = getCombinations(comboGroups)
        
        val comboTotals = combos.associateWith { combo ->
            items.sumOf { item ->
                val itemCats = parseCategoryInfo(item.text).second.toSet()
                if (combo.all { it in itemCats }) parsePrice(item.text) else 0.0
            }
        }.filter { it.value > 0.0 }

        if (comboTotals.isEmpty()) {
            listOf(DetailRowData(
                primaryCategories = emptyList(),
                secondaryCategories = emptyList(),
                tertiaryCategories = emptyList(),
                percentage = 0.0,
                amount = 0.0,
                color = Color.Gray,
                isIntersection = false,
                title = "No expenses match the selected intersection."
            ))
        } else {
            val totalIntersectionExpense = comboTotals.values.sum()
            comboTotals.entries.sortedByDescending { it.value }.map { (combo, amount) ->
                val percentage = if (totalIntersectionExpense > 0) (amount / totalIntersectionExpense) * 100 else 0.0
                
                // Organize categories by type for better display
                val categoriesByType = combo.groupBy { category ->
                    getCategoryType(category)
                }
                
                val primaryCategories = categoriesByType[CategoryType.PRIMARY] ?: emptyList()
                val secondaryCategories = categoriesByType[CategoryType.SECONDARY] ?: emptyList()
                val tertiaryCategories = categoriesByType[CategoryType.TERTIARY] ?: emptyList()
                
                // Get the most specific category for color
                val mostSpecificCategory = tertiaryCategories.firstOrNull() 
                    ?: secondaryCategories.firstOrNull() 
                    ?: primaryCategories.firstOrNull() 
                    ?: combo.first()
                
                DetailRowData(
                    primaryCategories = primaryCategories,
                    secondaryCategories = secondaryCategories,
                    tertiaryCategories = tertiaryCategories,
                    percentage = percentage,
                    amount = amount,
                    color = categoryColors[mostSpecificCategory] ?: Color.Unspecified,
                    isIntersection = true
                )
            }
        }
    } else {
        // Union case
        val totalMonthlyExpense = items.sumOf { parsePrice(it.text) }
        val unionTotals = mutableMapOf<String, Double>()
        items.forEach { item ->
            val category = parseCategoryInfo(item.text).second.firstOrNull { it in selectedCategories } ?: return@forEach
            unionTotals[category] = unionTotals.getOrDefault(category, 0.0) + parsePrice(item.text)
        }

        unionTotals.entries.sortedByDescending { it.value }.map { (category, amount) ->
            val percentage = if (totalMonthlyExpense > 0) (amount / totalMonthlyExpense) * 100 else 0.0
            
            val categoryType = getCategoryType(category)
            val primaryCategories = if (categoryType == CategoryType.PRIMARY) listOf(category) else emptyList()
            val secondaryCategories = if (categoryType == CategoryType.SECONDARY) listOf(category) else emptyList()
            val tertiaryCategories = if (categoryType == CategoryType.TERTIARY) listOf(category) else emptyList()
            
            DetailRowData(
                primaryCategories = primaryCategories,
                secondaryCategories = secondaryCategories,
                tertiaryCategories = tertiaryCategories,
                percentage = percentage,
                amount = amount,
                color = categoryColors[category] ?: Color.Unspecified,
                isIntersection = false
            )
        }
    }
}

// Data class for pre-computed row data
private data class DetailRowData(
    val primaryCategories: List<String> = emptyList(),
    val secondaryCategories: List<String> = emptyList(),
    val tertiaryCategories: List<String> = emptyList(),
    val percentage: Double,
    val amount: Double,
    val color: Color,
    val isIntersection: Boolean = false,
    // For simple text case
    val title: String = ""
)

@Composable
private fun DetailRow(
    data: DetailRowData,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (data.title.isNotEmpty()) {
            // Simple text case for error messages
            Text(
                text = data.title,
                color = data.color,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(0.5f),
                fontSize = 12.sp
            )
        } else if (data.isIntersection) {
            // For intersection case - reorder categories (tertiary first, then ∩, then secondary/primary)
            val textContent = buildAnnotatedString {
                // First show tertiary categories in normal color
                if (data.tertiaryCategories.isNotEmpty()) {
                    withStyle(SpanStyle(color = data.color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)) {
                        append(data.tertiaryCategories.joinToString(", "))
                    }
                }
                
                // Show intersection symbol
                if ((data.tertiaryCategories.isNotEmpty() && (data.secondaryCategories.isNotEmpty() || data.primaryCategories.isNotEmpty())) ||
                    (data.secondaryCategories.isNotEmpty() && data.primaryCategories.isNotEmpty())) {
                    withStyle(SpanStyle(color = Color.Gray, fontWeight = FontWeight.Normal, fontSize = 11.sp)) {
                        append(" ∩ ")
                    }
                }
                
                // Show secondary categories in smaller font
                if (data.secondaryCategories.isNotEmpty()) {
                    withStyle(SpanStyle(color = data.color, fontWeight = FontWeight.Normal, fontSize = 10.sp)) {
                        append(data.secondaryCategories.joinToString(", "))
                    }
                }
                
                // Show another intersection symbol if needed
                if (data.secondaryCategories.isNotEmpty() && data.primaryCategories.isNotEmpty()) {
                    withStyle(SpanStyle(color = Color.Gray, fontWeight = FontWeight.Normal, fontSize = 10.sp)) {
                        append(" ∩ ")
                    }
                }
                
                // Show primary categories in smaller font
                if (data.primaryCategories.isNotEmpty()) {
                    withStyle(SpanStyle(color = data.color, fontWeight = FontWeight.Normal, fontSize = 10.sp)) {
                        append(data.primaryCategories.joinToString(", "))
                    }
                }
            }
            
            Text(
                text = textContent,
                modifier = Modifier.weight(0.6f)
            )
        } else {
            // For union case - just show the category
            val category = data.tertiaryCategories.firstOrNull() ?: 
                          data.secondaryCategories.firstOrNull() ?: 
                          data.primaryCategories.firstOrNull() ?: ""
            
            Text(
                text = category,
                color = data.color,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(0.6f),
                fontSize = 12.sp
            )
        }
        
        Text(
            text = "(${String.format("%.1f", data.percentage)}%)",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.15f),
            fontSize = 11.sp
        )
        
        Text(
            text = formatCurrency(data.amount),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.25f),
            fontSize = 12.sp
        )
    }
}

private fun formatCurrency(amount: Double): String {
    // Format currency without decimal places
    val formatted = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(amount)
    return formatted.replace(".00", "")
} 