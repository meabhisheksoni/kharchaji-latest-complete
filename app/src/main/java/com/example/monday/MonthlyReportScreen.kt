package com.example.monday

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.*
import kotlin.math.roundToInt
import com.example.monday.ui.components.UncategorizedExpensesDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportScreen(
    todoViewModel: TodoViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToFilter: () -> Unit,
    selectedCategories: List<String>
) {
    val currentYear = Year.now().value
    var selectedYear by remember { mutableStateOf(currentYear) }
    val coroutineScope = rememberCoroutineScope()

    val allCategories by todoViewModel.todoItems.map { items ->
        items.flatMap { parseCategoryInfo(it.text).second }.toSet()
    }.collectAsState(initial = emptySet())

    val categoryColors by remember(allCategories) {
        mutableStateOf(generateCategoryColors(allCategories.toList()))
    }

    // State to hold master record data by month
    var masterRecordsByMonth by remember { mutableStateOf<Map<YearMonth, Map<String, Double>>>(emptyMap()) }
    // New state to hold the actual master records for detail dialog
    var masterRecordsForDialog by remember { mutableStateOf<Map<YearMonth, List<CalculationRecord>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load master record data for the selected year
    LaunchedEffect(selectedYear) {
        isLoading = true
        val yearData = mutableMapOf<YearMonth, Map<String, Double>>()
        val recordsData = mutableMapOf<YearMonth, List<CalculationRecord>>()
        
        // For each month in the year, fetch master record data
        for (month in 1..12) {
            val yearMonth = YearMonth.of(selectedYear, month)
            val monthData = todoViewModel.getMasterRecordTotalsForMonth(yearMonth)
            yearData[yearMonth] = monthData
            
            // Also store the actual master records for this month
            val startOfMonthMillis = yearMonth.atDay(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfMonthMillis = yearMonth.atEndOfMonth()
                .plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            
            // Get master records for this month
            val masterRecords = todoViewModel.getMasterRecordsForMonth(startOfMonthMillis, endOfMonthMillis)
            recordsData[yearMonth] = masterRecords
        }
        
        masterRecordsByMonth = yearData
        masterRecordsForDialog = recordsData
        isLoading = false
    }

    // Calculate filtered monthly expenses based on selected categories
    val filteredMonthlyExpenses = remember(masterRecordsByMonth, selectedCategories) {
        derivedStateOf {
            if (selectedCategories.isEmpty()) {
                masterRecordsByMonth
            } else {
                filterMonthlyExpenses(masterRecordsForDialog, selectedCategories.toSet())
            }
        }
    }

    // Calculate the maximum monthly total from filtered data
    val maxMonthlyTotal = remember(masterRecordsByMonth, selectedCategories) {
        derivedStateOf {
            filteredMonthlyExpenses.value.values.maxOfOrNull { monthData ->
                monthData.values.sum()
            } ?: 0.0
        }
    }.value

    var showDetailDialog by remember { mutableStateOf(false) }
    var dialogItems by remember { mutableStateOf<List<TodoItem>>(emptyList()) }
    var dialogMasterRecords by remember { mutableStateOf<List<CalculationRecord>>(emptyList()) }
    var selectedMonthYear by remember { mutableStateOf("") }
    
    var showUncategorizedDialog by remember { mutableStateOf(false) }
    
    // New dialog to show master record details
    if (showDetailDialog) {
        // Create TodoItems from master records for compatibility with ExpenseDetailDialog
        val convertedItems = remember(dialogMasterRecords) {
            dialogMasterRecords.flatMap { record ->
                record.items.map { item ->
                    // Convert RecordItem to TodoItem format
                    TodoItem(
                        id = 0, // Dummy ID
                        text = "${item.description} - ₹${item.price}" + 
                              (if (!item.categories.isNullOrEmpty()) "|CATS:${item.categories.joinToString(",")}" else ""),
                        isDone = item.isChecked,
                        timestamp = record.recordDate,
                        categories = item.categories,
                        imageUris = item.imageUris,
                        hasPrimaryCategory = item.categories?.any { getCategoryType(it) == CategoryType.PRIMARY } ?: false,
                        hasSecondaryCategory = item.categories?.any { getCategoryType(it) == CategoryType.SECONDARY } ?: false,
                        hasTertiaryCategory = item.categories?.any { getCategoryType(it) == CategoryType.TERTIARY } ?: false
                    )
                }
            }
        }

        ExpenseDetailDialog(
            onDismiss = { showDetailDialog = false },
            items = convertedItems,
            selectedCategories = selectedCategories,
            categoryColors = categoryColors,
            titleOverride = selectedMonthYear + " (Master Records)"
        )
    }

    if (showUncategorizedDialog) {
        UncategorizedExpensesDialog(
            todoViewModel = todoViewModel,
            onDismiss = { showUncategorizedDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly Reports") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showUncategorizedDialog = true }) {
                        Icon(Icons.Outlined.HelpOutline, contentDescription = "Uncategorized Expenses")
                    }
                    IconButton(onClick = onNavigateToFilter) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 8.dp)
            ) {
                YearNavigation(
                    selectedYear = selectedYear,
                    currentYear = currentYear,
                    onYearChange = { selectedYear = it }
                )

                if (selectedCategories.isNotEmpty()) {
                    val (primary, secondary, tertiary) = intelligentlyCategorize(selectedCategories.toSet())
                    CategoryLegendLayout(
                        primaryCategories = primary.map { CategoryLegendItem(it, categoryColors[it] ?: Color.Gray) },
                        secondaryCategories = secondary.map { CategoryLegendItem(it, categoryColors[it] ?: Color.Gray) },
                        tertiaryCategories = tertiary.map { CategoryLegendItem(it, categoryColors[it] ?: Color.Gray) }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    for (month in Month.values()) {
                        val yearMonth = YearMonth.of(selectedYear, month)
                        // Use filtered data for visual representation
                        val monthExpenses = filteredMonthlyExpenses.value[yearMonth] ?: emptyMap()
                        val monthMasterRecords = masterRecordsForDialog[yearMonth] ?: emptyList()
                        
                        StackedBarMonth(
                            modifier = Modifier.weight(1f),
                            month = month,
                            expenses = monthExpenses,
                            maxTotal = maxMonthlyTotal,
                            categoryColors = categoryColors,
                            onClick = {
                                if (monthMasterRecords.isNotEmpty() || monthExpenses.isNotEmpty()) {
                                    dialogMasterRecords = monthMasterRecords
                                    selectedMonthYear = "${month.getDisplayName(TextStyle.FULL, Locale.getDefault())} $selectedYear"
                                    showDetailDialog = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// Helper function to filter monthly expenses based on selected categories
private fun filterMonthlyExpenses(
    masterRecordsForDialog: Map<YearMonth, List<CalculationRecord>>,
    selectedCategories: Set<String>
): Map<YearMonth, Map<String, Double>> {
    if (selectedCategories.isEmpty()) {
        return masterRecordsForDialog.mapValues { mutableMapOf<String, Double>() }
    }
    
    val result = mutableMapOf<YearMonth, MutableMap<String, Double>>()
    
    // Separate the selected categories by type for efficient checking
    val selectedPrimary = selectedCategories.filter { getCategoryType(it) == CategoryType.PRIMARY }.toSet()
    val selectedSecondary = selectedCategories.filter { getCategoryType(it) == CategoryType.SECONDARY }.toSet()
    val selectedTertiary = selectedCategories.filter { getCategoryType(it) == CategoryType.TERTIARY }.toSet()
    
    Log.d("MonthlyReport", "Filtering with categories - Primary: $selectedPrimary, Secondary: $selectedSecondary, Tertiary: $selectedTertiary")
    
    // Process each month
    for ((yearMonth, masterRecords) in masterRecordsForDialog) {
        val monthResult = mutableMapOf<String, Double>()
        
        // Process each master record for this month
        for (record in masterRecords) {
            // Process each item in the record
            val recordItems = record.items
            for (i in recordItems.indices) {
                val item = recordItems[i]
                val categories = item.categories ?: continue
                val price = item.price.toDoubleOrNull() ?: 0.0
                
                // For each category in the item, check if it should be included
                for (category in categories) {
                    val hasSelectedPrimary = selectedPrimary.isNotEmpty()
                    val hasSelectedSecondary = selectedSecondary.isNotEmpty()
                    val hasSelectedTertiary = selectedTertiary.isNotEmpty()
                    
                    val shouldShow = when {
                        // If tertiary is selected, we only show tertiary when primary and secondary match
                        selectedTertiary.isNotEmpty() -> {
                            category in selectedTertiary && hasSelectedPrimary && hasSelectedSecondary
                        }
                        
                        // If secondary is selected, we only show secondary when primary matches
                        selectedSecondary.isNotEmpty() -> {
                            category in selectedSecondary && hasSelectedPrimary
                        }
                        
                        // If only primary is selected, we show primary categories
                        else -> category in selectedPrimary
                    }
                    
                    if (shouldShow) {
                        // Add the price to our result for this category
                        monthResult[category] = (monthResult[category] ?: 0.0) + price
                        Log.d("MonthlyReport", "Hierarchical match for '$category' in item ${item.description}: $price")
                    }
                }
            }
        }
        
        Log.d("MonthlyReport", "Final result for $yearMonth: ${monthResult.entries.joinToString { "${it.key}=${it.value}" }}")
        result[yearMonth] = monthResult
    }
    return result
}

@Composable
fun YearNavigation(
    selectedYear: Int,
    currentYear: Int,
    onYearChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onYearChange(selectedYear - 1) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Year")
        }

        Text(
            text = selectedYear.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        IconButton(
            onClick = { onYearChange(selectedYear + 1) }
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next Year"
            )
        }
    }
}

@Composable
fun StackedBarMonth(
    modifier: Modifier = Modifier,
    month: Month,
    expenses: Map<String, Double>,
    maxTotal: Double,
    categoryColors: Map<String, Color>,
    onClick: () -> Unit
) {
    val totalExpense = expenses.values.sum()
    val barMaxHeight = 300.dp
    
    // Calculate bar height based on total expense relative to max total
    val barHeight = if (maxTotal > 0) {
        (barMaxHeight * (totalExpense / maxTotal).toFloat()).coerceIn(4.dp, barMaxHeight)
    } else 0.dp
    
    // Format amount for display
    val formattedAmount = remember(totalExpense) {
        when {
            totalExpense >= 10000000 -> { // ≥ 1 Cr
                val crores = totalExpense / 10000000
                "₹${String.format("%.1f", crores)}Cr"
            }
            totalExpense >= 100000 -> { // ≥ 1 Lakh
                val lakhs = totalExpense / 100000
                "₹${String.format("%.1f", lakhs)}L"
            }
            totalExpense >= 1000 -> { // ≥ 1K
                val thousands = totalExpense / 1000
                "₹${String.format("%.0f", thousands)}K"
            }
            totalExpense > 0 -> "₹${totalExpense.roundToInt()}"
            else -> ""
        }
    }
    
    val interactionSource = remember { MutableInteractionSource() }
    
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true),
                enabled = expenses.isNotEmpty(),
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        if (totalExpense > 0) {
            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontSize = 9.sp,
                maxLines = 1,
                modifier = Modifier
                    .width(36.dp)
                    .padding(bottom = 2.dp)
            )
            
            // Sort expenses by category type to ensure consistent stacking order
            val sortedExpenses = expenses.entries.sortedWith(
                compareBy { entry ->
                    val categoryType = getCategoryType(entry.key)
                    when (categoryType) {
                        CategoryType.PRIMARY -> 0
                        CategoryType.SECONDARY -> 1
                        CategoryType.TERTIARY -> 2
                    }
                }
            )
            
            // Create stacked bar segments
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(barHeight)
            ) {
                var currentOffset = 0f
                sortedExpenses.forEach { (category, amount) ->
                    val segmentHeight = (amount / totalExpense).toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(segmentHeight)
                            .offset(y = (currentOffset * barHeight.value).dp)
                            .background(categoryColors[category] ?: Color.Gray)
                    )
                    currentOffset += segmentHeight
                }
            }
        }
        
        Text(
            text = month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun hasMatchingParentCategories(
    category: String,
    selectedSecondary: Set<String>,
    selectedPrimary: Set<String>
): Boolean {
    val (primary, secondary, _) = intelligentlyCategorize(setOf(category))
        .let { (p, s, t) -> Triple(p.toSet(), s.toSet(), t.toSet()) }
    
    return (selectedPrimary.isEmpty() || primary.any { it in selectedPrimary }) &&
           (selectedSecondary.isEmpty() || secondary.any { it in selectedSecondary })
}