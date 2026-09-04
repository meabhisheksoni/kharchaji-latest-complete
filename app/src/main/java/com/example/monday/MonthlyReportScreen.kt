package com.example.monday
import com.example.monday.core.utils.*
import com.example.monday.data.models.TodoItem
import com.example.monday.data.models.CalculationRecord
import com.example.monday.data.models.RecordItem
import com.example.monday.viewmodels.MainViewModel

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
import androidx.compose.material3.ripple
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    todoViewModel: TodoViewModel, mainViewModel: MainViewModel,
    statsViewModel: com.example.monday.viewmodels.StatsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToFilter: () -> Unit,
    selectedCategories: List<String>
) {
    val currentYear = Year.now().value
    var selectedYear by remember { mutableStateOf(currentYear) }
    val coroutineScope = rememberCoroutineScope()

    val allCategories by mainViewModel.todoItems.map { items ->
        items.flatMap { parseCategoryInfo(it.text).second }.toSet()
    }.collectAsState(initial = emptySet())

    val categoryColors by remember(allCategories) {
        mutableStateOf(generateCategoryColors(allCategories.toList()))
    }

    // Live reactive binding to avoid blocking the main UI thread during year aggregation
    val allRecords by statsViewModel.allCalculationRecords.collectAsState()

    val yearDataState by produceState(
        initialValue = Pair(
            emptyMap<YearMonth, Map<String, Double>>(),
            emptyMap<YearMonth, List<CalculationRecord>>()
        ),
        key1 = allRecords,
        key2 = selectedYear
    ) {
        withContext(Dispatchers.Default) {
            val startOfYearMillis = YearMonth.of(selectedYear, 1).atDay(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfYearMillis = YearMonth.of(selectedYear, 12).atEndOfMonth()
                .plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

            val allYearMasterRecords = allRecords.filter {
                it.isMasterSave && it.recordDate in startOfYearMillis..endOfYearMillis
            }

            val groupedRecords = allYearMasterRecords.groupBy { record ->
                YearMonth.from(Instant.ofEpochMilli(record.recordDate).atZone(ZoneId.systemDefault()))
            }

            val yearData = mutableMapOf<YearMonth, Map<String, Double>>()
            val recordsData = mutableMapOf<YearMonth, List<CalculationRecord>>()

            for (month in 1..12) {
                val yearMonth = YearMonth.of(selectedYear, month)
                val monthRecords = groupedRecords[yearMonth] ?: emptyList()

                recordsData[yearMonth] = monthRecords
                yearData[yearMonth] = statsViewModel.calculateMasterRecordTotals(monthRecords)
            }

            value = Pair(yearData, recordsData)
        }
    }

    val masterRecordsByMonth = yearDataState.first
    val masterRecordsForDialog = yearDataState.second
    val isLoading = false

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
    }

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
                            maxTotal = maxMonthlyTotal.value, 
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
                
                // Check if any of the item's categories match the selected categories
                val matchingCategories = categories.filter { it in selectedCategories }
                
                if (matchingCategories.isNotEmpty()) {
                    // Only add the price ONCE per item, using the first matching category
                    // This prevents double/triple counting when an item has multiple selected categories
                    val primaryMatchingCategory = matchingCategories.first()
                    monthResult[primaryMatchingCategory] = (monthResult[primaryMatchingCategory] ?: 0.0) + price
                    Log.d("MonthlyReport", "Match for '$primaryMatchingCategory' in item ${item.description}: $price (had ${matchingCategories.size} matching categories)")
                }
            }
        }
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
            totalExpense >= 10000000 -> { // â‰¥ 1 Cr
                val crores = totalExpense / 10000000
                "₹${String.format("%.1f", crores)}Cr"
            }
            totalExpense >= 100000 -> { // â‰¥ 1 Lakh
                val lakhs = totalExpense / 100000
                "₹${String.format("%.1f", lakhs)}L"
            }
            totalExpense >= 1000 -> { // â‰¥ 1K
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
                indication = ripple(),
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
