package com.example.monday

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.monday.core.utils.formatIndianCurrency
import com.example.monday.core.utils.formatCompactAmount
import com.example.monday.core.utils.parsePrice
import com.example.monday.data.models.TodoItem
import com.example.monday.ui.modern.ModernColors
import com.example.monday.ui.modern.RollingNumber
import com.example.monday.viewmodels.MainViewModel
import com.example.monday.viewmodels.StatsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ─── Domain Models for Financial Intelligence ──────────────────────────────

data class InsightCardData(
    val title: String,
    val subtitle: String,
    val tag: String,
    val icon: ImageVector,
    val tintColor: Color
)

data class CategoryShare(
    val category: String,
    val amount: Double,
    val percentage: Float,
    val color: Color
)

data class InsightsUiState(
    val totalMonthSpend: Double = 0.0,
    val dailyAverageSpend: Double = 0.0,
    val peakDayDate: String = "N/A",
    val peakDayAmount: Double = 0.0,
    val expenseCount: Int = 0,
    val budgetLimit: Double = 15000.0,
    val categoryShares: List<CategoryShare> = emptyList(),
    val smartInsights: List<InsightCardData> = emptyList(),
    val isLoading: Boolean = true
)

// ─── Main Composable Screen ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateToAllExpenses: () -> Unit = {},
    onNavigateToFindAndReplace: () -> Unit = {},
    onNavigateToMonthlyReport: () -> Unit = {},
    onNavigateToCategories: () -> Unit = {},
    onNavigateToTrends: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") todoViewModel: TodoViewModel = viewModel(),
    mainViewModel: MainViewModel = viewModel(),
    statsViewModel: StatsViewModel = viewModel()
) {
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var uiState by remember { mutableStateOf(InsightsUiState()) }
    var lastRenderedMonth by remember { mutableStateOf(selectedMonth) }

    val allTodoItems by mainViewModel.todoItems.collectAsState()
    val allMasterRecords by statsViewModel.allCalculationRecords.collectAsState()

    // Defensive off-main-thread background calculation
    LaunchedEffect(selectedMonth, allTodoItems, allMasterRecords) {
        try {
            if (selectedMonth != lastRenderedMonth) {
                uiState = InsightsUiState(isLoading = true)
                lastRenderedMonth = selectedMonth
            } else {
                uiState = uiState.copy(isLoading = true)
            }
            val computed = withContext(Dispatchers.Default) {
                calculateInsights(
                    month = selectedMonth,
                    todoItems = allTodoItems,
                    budget = 15000.0
                )
            }
            uiState = computed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("InsightsScreen", "Error computing financial insights", e)
            uiState = uiState.copy(isLoading = false)
        }
    }

    Scaffold(
        containerColor = ModernColors.Eggshell,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Financial Insights",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // Premium AI Insight Chip
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = ModernColors.ChipBg,
                            border = BorderStroke(1.dp, ModernColors.ChipBorder)
                        ) {
                            Text(
                                text = "AI Analytics",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ModernColors.ChipText
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ModernColors.SoftCream
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), // 16dp Guide Rail
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Month Navigation Scope Pill ──────────────────────────────
            item {
                MonthSelectorRail(
                    currentMonth = selectedMonth,
                    onPreviousMonth = { selectedMonth = selectedMonth.minusMonths(1) },
                    onNextMonth = { selectedMonth = selectedMonth.plusMonths(1) },
                    onCurrentMonthClick = { selectedMonth = YearMonth.now() }
                )
            }

            // ── 2. Hero Spend & Daily Burn Velocity ─────────────────────────
            item {
                HeroInsightsCard(
                    totalSpend = uiState.totalMonthSpend,
                    dailyAverage = uiState.dailyAverageSpend,
                    peakDate = uiState.peakDayDate,
                    peakAmount = uiState.peakDayAmount,
                    budgetLimit = uiState.budgetLimit
                )
            }

            // ── 3. Smart Heuristic Spending Insights ────────────────────────
            item {
                Text(
                    text = "Smart Patterns & Forecasts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            if (uiState.smartInsights.isEmpty()) {
                item {
                    EmptyInsightsCard()
                }
            } else {
                items(uiState.smartInsights) { insight ->
                    InsightCard(insight)
                }
            }

            // ── 4. Category Spending Distribution ───────────────────────────
            item {
                CategoryDistributionCard(categories = uiState.categoryShares)
            }

            // ── 5. Analytics & Reporting Toolset ────────────────────────────
            item {
                Text(
                    text = "Analytics Deep-Dives",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            item {
                AnalyticsToolRow(
                    onNavigateToMonthlyReport = onNavigateToMonthlyReport,
                    onNavigateToCategories = onNavigateToCategories,
                    onNavigateToTrends = onNavigateToTrends,
                    onNavigateToFindAndReplace = onNavigateToFindAndReplace,
                    onNavigateToAllExpenses = onNavigateToAllExpenses
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ─── Sub-Components (Adhering to MASTER.md) ────────────────────────────────

@Composable
fun MonthSelectorRail(
    currentMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonthClick: () -> Unit = {}
) {
    val isCurrentMonth = currentMonth == YearMonth.now()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ModernColors.CardBg,
        border = BorderStroke(1.dp, ModernColors.CardBorder.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPreviousMonth),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, ModernColors.CardBorder.copy(alpha = 0.5f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous Month",
                        tint = ModernColors.EggnogDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Clickable Center Month Pill (Tap to reset to Current Month)
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onCurrentMonthClick)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isCurrentMonth) "Scope: Complete Monthly Cycle" else "Tap to return to Current Month",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrentMonth) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (isCurrentMonth) ModernColors.EggnogDark else ModernColors.DateSelected
                )
            }

            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onNextMonth),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, ModernColors.CardBorder.copy(alpha = 0.5f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next Month",
                        tint = ModernColors.EggnogDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HeroInsightsCard(
    totalSpend: Double,
    dailyAverage: Double,
    peakDate: String,
    peakAmount: Double,
    budgetLimit: Double
) {
    // Exact Satoshi Bold typography pairing from Home Screen HeroDashboard
    val satoshiFontFamily = remember {
        try {
            FontFamily(Font(R.font.satoshi_bold, FontWeight.Bold))
        } catch (e: Exception) {
            FontFamily.Default
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ModernColors.CardBg,
        border = BorderStroke(1.dp, ModernColors.CardBorder.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "TOTAL SPEND THIS MONTH",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = ModernColors.EggnogDark,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Giant monthly spend amount — odometer rolling animation (identical physics to Home Screen)
            RollingNumber(
                targetValue = totalSpend.toInt(),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = satoshiFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-1.0).sp
            )

            if (budgetLimit > 0) {
                val percent = ((totalSpend / budgetLimit) * 100).coerceAtMost(100.0)
                val barColor = when {
                    percent >= 100.0 -> ModernColors.Destructive
                    percent >= 80.0 -> Color(0xFFD97706)
                    else -> ModernColors.Groceries
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Budget: ₹${formatIndianCurrency(budgetLimit.toInt())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ModernColors.TextMuted
                    )
                    Text(
                        text = "%.1f%% used".format(percent),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = barColor
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                // Tactile Progress Bar (MASTER.md § 7.A)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ModernColors.CardBorder.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((percent / 100f).toFloat())
                            .fillMaxHeight()
                            .background(barColor)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 0.5.dp,
                color = ModernColors.CardBorder.copy(alpha = 0.3f)
            )

            // 2-Metric Burn Rate Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Daily Burn Rate",
                        style = MaterialTheme.typography.labelSmall,
                        color = ModernColors.TextMuted
                    )
                    Text(
                        text = "₹${formatIndianCurrency(dailyAverage.toInt())} / day",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Peak Spending Day",
                        style = MaterialTheme.typography.labelSmall,
                        color = ModernColors.TextMuted
                    )
                    Text(
                        text = if (peakAmount > 0) "$peakDate (${formatCompactAmount(peakAmount)})" else "None",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (peakAmount > 0) ModernColors.Destructive else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun InsightCard(insight: InsightCardData) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ModernColors.CardBg,
        border = BorderStroke(1.dp, ModernColors.CardBorder.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Distinct Icon Badge
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = insight.tintColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = insight.icon,
                        contentDescription = null,
                        tint = insight.tintColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = insight.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = insight.tintColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = insight.tag,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = insight.tintColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = insight.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
fun CategoryDistributionCard(categories: List<CategoryShare>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ModernColors.CardBg,
        border = BorderStroke(1.dp, ModernColors.CardBorder.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Category Allocation",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (categories.isEmpty()) {
                Text(
                    text = "No categorized expenses for this month.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ModernColors.TextMuted
                )
            } else {
                // Multi-color segmented distribution bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    categories.forEach { share ->
                        Box(
                            modifier = Modifier
                                .weight(share.percentage.coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(share.color)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Top 4 Category legend chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.take(4).forEach { share ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(share.color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = share.category,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "₹${formatIndianCurrency(share.amount.toInt())} (${(share.percentage * 100).roundToInt()}%)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ModernColors.EggnogDark
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsToolRow(
    onNavigateToMonthlyReport: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToTrends: () -> Unit,
    onNavigateToFindAndReplace: () -> Unit,
    onNavigateToAllExpenses: () -> Unit
) {
    val tools = listOf(
        Triple("Monthly", Icons.Outlined.CalendarMonth, onNavigateToMonthlyReport),
        Triple("Category", Icons.Outlined.Category, onNavigateToCategories),
        Triple("Trends", Icons.AutoMirrored.Outlined.TrendingUp, onNavigateToTrends),
        Triple("Search", Icons.Outlined.FindReplace, onNavigateToFindAndReplace),
        Triple("Ledger", Icons.AutoMirrored.Outlined.List, onNavigateToAllExpenses)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tools.forEach { (label, icon, onClick) ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onClick),
                color = ModernColors.CardBg,
                border = BorderStroke(1.dp, ModernColors.CardBorder.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = ModernColors.EggnogDark,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyInsightsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ModernColors.CardBg,
        border = BorderStroke(1.dp, ModernColors.CardBorder.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = ModernColors.TodayButton,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Building Financial Intelligence",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Log your daily transactions to unlock pattern detection, repeat purchase forecasts, and category velocity analytics.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = ModernColors.TextMuted
            )
        }
    }
}

// ─── Off-Main-Thread Heuristic Intelligence Engine ──────────────────────────

private fun getExpensePrice(item: TodoItem): Double {
    // 1. Sanitize text: strip comma separators used in Indian notation (e.g. 15,000 -> 15000)
    val sanitizedText = item.text.replace(",", "")

    // 2. Primary format: "Item - ₹15000|CATS:..."
    val priceFromUtil = parsePrice(sanitizedText)
    if (priceFromUtil > 0) return priceFromUtil

    // 3. Resilient Regex extraction supporting commas, spaces, and decimals
    val rupeePattern = Regex("""(?:₹|rs\.?|inr)\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
    val match = rupeePattern.find(sanitizedText)
        ?: Regex("""(?:^|\s)([0-9]+(?:\.[0-9]+)?)\s*$""").find(sanitizedText)

    return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
}

private fun calculateInsights(
    month: YearMonth,
    todoItems: List<TodoItem>,
    budget: Double
): InsightsUiState {
    val zone = ZoneId.systemDefault()
    val now = LocalDate.now()
    val currentYearMonth = YearMonth.now()

    // 1. Filter items within month boundary
    val monthItems = todoItems.filter { item ->
        val date = Instant.ofEpochMilli(item.timestamp).atZone(zone).toLocalDate()
        YearMonth.from(date) == month
    }

    val totalSum = monthItems.sumOf { getExpensePrice(it) }
    val daysInMonth = month.lengthOfMonth()

    // 2. Temporal State Classification (Prevents Anachronistic Projections)
    val isCurrentMonth = month == currentYearMonth
    val isPastMonth = month < currentYearMonth

    val elapsedDays = when {
        isCurrentMonth -> now.dayOfMonth.coerceIn(1, daysInMonth)
        isPastMonth -> daysInMonth
        else -> 0 // Future month has 0 elapsed days
    }

    val dailyAvg = if (elapsedDays > 0) totalSum / elapsedDays else 0.0

    // 3. Peak spending day identification
    val dayGroups = monthItems.groupBy { item ->
        Instant.ofEpochMilli(item.timestamp).atZone(zone).toLocalDate()
    }
    val peakEntry = dayGroups.maxByOrNull { entry -> entry.value.sumOf { getExpensePrice(it) } }
    val peakDateStr = peakEntry?.key?.format(DateTimeFormatter.ofPattern("MMM d")) ?: "None"
    val peakAmount = peakEntry?.value?.sumOf { getExpensePrice(it) } ?: 0.0

    // 4. Category attribution aligned with MasterRecordManager (Single-attribution priority)
    val categoryTotals = mutableMapOf<String, Double>()
    monthItems.forEach { item ->
        val price = getExpensePrice(item)
        if (price > 0) {
            val rawCats = item.categories?.filter { it.isNotBlank() } ?: emptyList()
            val primaryCat = rawCats.firstOrNull() ?: "Other"
            categoryTotals[primaryCat] = (categoryTotals[primaryCat] ?: 0.0) + price
        }
    }

    val categoryShares = categoryTotals.map { (cat, amount) ->
        CategoryShare(
            category = cat,
            amount = amount,
            percentage = if (totalSum > 0) (amount / totalSum).toFloat() else 0f,
            color = ModernColors.categoryColors[cat] ?: ModernColors.Transport
        )
    }.sortedByDescending { it.amount }

    // 5. Intelligent Insight Generation (Zero False-Positives on Empty Data)
    val insights = mutableListOf<InsightCardData>()

    if (monthItems.isNotEmpty() && totalSum > 0) {
        // Insight A: Category Concentration Alert (Threshold: >= 35%)
        val topCategory = categoryShares.firstOrNull()
        if (topCategory != null && topCategory.percentage >= 0.35f && topCategory.amount > 0) {
            val pct = (topCategory.percentage * 100).roundToInt()
            insights.add(
                InsightCardData(
                    title = "High Category Concentration",
                    subtitle = "${topCategory.category} accounts for ${pct}% of your total spending (₹${formatIndianCurrency(topCategory.amount.toInt())}).",
                    tag = "Spending Alert",
                    icon = Icons.Outlined.PieChart,
                    tintColor = topCategory.color
                )
            )
        }

        // Insight B: Budget Velocity (Contextualized by Temporal State)
        if (budget > 0) {
            if (isCurrentMonth) {
                val projectedSpend = dailyAvg * daysInMonth
                if (projectedSpend > budget) {
                    insights.add(
                        InsightCardData(
                            title = "Budget Velocity Warning",
                            subtitle = "At ₹${formatIndianCurrency(dailyAvg.toInt())}/day, projected monthly spend will reach ₹${formatIndianCurrency(projectedSpend.toInt())} (exceeding budget by ₹${formatIndianCurrency((projectedSpend - budget).toInt())}).",
                            tag = "Pace Alert",
                            icon = Icons.Outlined.WarningAmber,
                            tintColor = ModernColors.Destructive
                        )
                    )
                } else {
                    insights.add(
                        InsightCardData(
                            title = "Sustainable Burn Rate",
                            subtitle = "Spending pace is on target. Projected month-end buffer: ₹${formatIndianCurrency((budget - projectedSpend).toInt())}.",
                            tag = "On Track",
                            icon = Icons.Outlined.CheckCircle,
                            tintColor = ModernColors.Groceries
                        )
                    )
                }
            } else if (isPastMonth) {
                // Historical Fact (Not a Projection)
                if (totalSum > budget) {
                    insights.add(
                        InsightCardData(
                            title = "Budget Exceeded",
                            subtitle = "Total spend in ${month.format(DateTimeFormatter.ofPattern("MMMM"))} exceeded the ₹${formatIndianCurrency(budget.toInt())} budget by ₹${formatIndianCurrency((totalSum - budget).toInt())}.",
                            tag = "Over Budget",
                            icon = Icons.Outlined.WarningAmber,
                            tintColor = ModernColors.Destructive
                        )
                    )
                } else {
                    insights.add(
                        InsightCardData(
                            title = "Within Budget",
                            subtitle = "Spending stayed within budget. Saved surplus: ₹${formatIndianCurrency((budget - totalSum).toInt())}.",
                            tag = "Completed",
                            icon = Icons.Outlined.CheckCircle,
                            tintColor = ModernColors.Groceries
                        )
                    )
                }
            }
        }

        // Insight C: Anomaly / Surge Detection
        if (peakAmount > dailyAvg * 2.2 && peakAmount > 500) {
            val multiplier = (peakAmount / dailyAvg.coerceAtLeast(1.0)).roundToInt()
            insights.add(
                InsightCardData(
                    title = "Expense Surge Detected",
                    subtitle = "On $peakDateStr, spending peaked at ₹${formatIndianCurrency(peakAmount.toInt())} (${multiplier}x higher than daily burn rate).",
                    tag = "Spike",
                    icon = Icons.AutoMirrored.Outlined.TrendingUp,
                    tintColor = Color(0xFFD97706)
                )
            )
        }
    }

    return InsightsUiState(
        totalMonthSpend = totalSum,
        dailyAverageSpend = dailyAvg,
        peakDayDate = peakDateStr,
        peakDayAmount = peakAmount,
        expenseCount = monthItems.size,
        budgetLimit = budget,
        categoryShares = categoryShares,
        smartInsights = insights,
        isLoading = false
    )
}
 
