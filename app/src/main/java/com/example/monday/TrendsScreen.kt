package com.example.monday

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import com.example.monday.core.utils.*
import com.example.monday.data.models.TodoItem
import com.example.monday.viewmodels.MainViewModel
import com.example.monday.ui.components.DefaultCategories

data class ItemTrend(
    val itemName: String,
    val categories: List<String>,
    val occurrences: Int,
    val averageDaysBetween: Double,
    val lastOccurrence: LocalDate,
    val nextPredicted: LocalDate,
    val totalAmount: Double,
    val averageAmount: Double,
    val monthlyEstimate: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    onNavigateBack: () -> Unit,
    todoViewModel: TodoViewModel = viewModel(),
    mainViewModel: MainViewModel = viewModel()
) {
    var itemTrends by remember { mutableStateOf<List<ItemTrend>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        itemTrends = calculateItemTrends(todoViewModel, mainViewModel, setOf("Grocery"))
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grocery Trends") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (itemTrends.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No grocery items found")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Purchase Patterns",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                items(itemTrends) { trend ->
                    TrendBulletPoint(trend = trend)
                }
            }
        }
    }
}

@Composable
fun TrendBulletPoint(trend: ItemTrend) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "•",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp)
        )
        Column {
            Text(
                buildSimpleInsightText(trend),
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
            )
        }
    }
}

fun buildSimpleInsightText(trend: ItemTrend): String {
    val days = trend.averageDaysBetween.roundToInt()
    val nextIn = ChronoUnit.DAYS.between(LocalDate.now(), trend.nextPredicted)
    val avgPrice = trend.averageAmount.roundToInt()
    val monthlyEst = trend.monthlyEstimate.roundToInt()
    
    val frequency = when {
        days == 1 -> "daily"
        days <= 3 -> "every $days days"
        days <= 7 -> "weekly"
        days <= 14 -> "bi-weekly"
        days <= 31 -> "monthly"
        else -> "every $days days"
    }
    
    val nextText = when {
        nextIn <= 0 -> "today"
        nextIn == 1L -> "tomorrow"
        else -> "in $nextIn days"
    }
    
    return "${trend.itemName}: Bought $frequency (₹$avgPrice avg) - Next $nextText - Monthly estimate: ₹$monthlyEst"
}



suspend fun calculateItemTrends(
    todoViewModel: TodoViewModel, mainViewModel: MainViewModel,
    selectedCategories: Set<String>
): List<ItemTrend> {
    val allExpenses = mainViewModel.getAllExpensesForExport()
    
    // Filter by selected categories and group by item name
    val itemGroups = allExpenses
        .filter { expense ->
            !expense.categories.isNullOrEmpty() &&
            expense.categories.any { it in selectedCategories }
        }
        .groupBy { expense ->
            // Extract item name (text before ₹)
            expense.text.substringBefore("₹").trim().lowercase()
        }
        .filter { it.value.size >= 2 } // Need at least 2 occurrences for a trend
    
    // Calculate trends for each item
    return itemGroups.map { (itemName, expenses) ->
        val sortedExpenses = expenses.sortedBy { it.timestamp }
        val dates = sortedExpenses.map { 
            java.time.Instant.ofEpochMilli(it.timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
        }
        
        // Calculate average days between occurrences
        val daysBetween = dates.zipWithNext { a, b ->
            ChronoUnit.DAYS.between(a, b).toDouble()
        }
        val avgDays = if (daysBetween.isNotEmpty()) daysBetween.average() else 0.0
        
        // Predict next occurrence
        val lastDate = dates.last()
        val nextPredicted = lastDate.plusDays(avgDays.roundToInt().toLong())
        
        // Calculate amounts
        val amounts = expenses.map { 
            it.text.substringAfter("₹").trim().toDoubleOrNull() ?: 0.0 
        }
        val totalAmount = amounts.sum()
        val avgAmount = totalAmount / expenses.size
        
        // Calculate monthly estimate (30 days)
        val monthlyEstimate = if (avgDays > 0) {
            (30.0 / avgDays) * avgAmount
        } else {
            avgAmount
        }
        
        // Get all categories this item appears in
        val itemCategories = expenses
            .flatMap { it.categories ?: emptyList() }
            .distinct()
            .filter { it in selectedCategories }
        
        ItemTrend(
            itemName = itemName.replaceFirstChar { it.uppercase() },
            categories = itemCategories,
            occurrences = expenses.size,
            averageDaysBetween = avgDays,
            lastOccurrence = lastDate,
            nextPredicted = nextPredicted,
            totalAmount = totalAmount,
            averageAmount = avgAmount,
            monthlyEstimate = monthlyEstimate
        )
    }
    .sortedByDescending { it.occurrences }
}
