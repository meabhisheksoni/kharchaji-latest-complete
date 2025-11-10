package com.example.monday

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.TrendingUp

data class StatOption(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val color: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateToAllExpenses: () -> Unit = {},
    onNavigateToFindAndReplace: () -> Unit = {},
    onNavigateToMonthlyReport: () -> Unit = {},
    onNavigateToCategories: () -> Unit = {}
) {
    // Use coroutineScope for handling clicks
    val coroutineScope = rememberCoroutineScope()
            
    // Define options list without remember
            val statOptions = listOf(
                StatOption(
                    title = "All Expenses",
            icon = Icons.AutoMirrored.Outlined.List,
                    description = "View all your expenses in one place",
                    color = MaterialTheme.colorScheme.primaryContainer,
                    onClick = onNavigateToAllExpenses
                ),
        StatOption(
            title = "Find & Replace",
            icon = Icons.Outlined.FindReplace,
            description = "Find and replace text in expenses",
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            onClick = onNavigateToFindAndReplace
        ),
                StatOption(
                    title = "Monthly Report",
                    icon = Icons.Outlined.CalendarMonth,
                    description = "View expenses by month",
                    color = MaterialTheme.colorScheme.secondaryContainer,
            onClick = onNavigateToMonthlyReport
                ),
                StatOption(
                    title = "Categories",
                    icon = Icons.Outlined.Category,
                    description = "Expenses by category",
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = onNavigateToCategories
                ),
                StatOption(
                    title = "Trends",
            icon = Icons.AutoMirrored.Outlined.TrendingUp,
                    description = "Spending trends over time",
                    color = MaterialTheme.colorScheme.errorContainer,
                    onClick = { /* TODO: Implement trends */ }
                ),
                StatOption(
                    title = "Budget",
                    icon = Icons.Outlined.AccountBalance,
                    description = "Budget tracking",
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { /* TODO: Implement budget tracking */ }
                ),
                StatOption(
                    title = "Insights",
                    icon = Icons.Outlined.Insights,
                    description = "Smart expense insights",
                    color = MaterialTheme.colorScheme.inversePrimary,
                    onClick = { /* TODO: Implement insights */ }
                ),
                StatOption(
                    title = "Export",
                    icon = Icons.Outlined.FileDownload,
                    description = "Export expense data",
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    onClick = { /* TODO: Implement export */ }
                ),
                StatOption(
                    title = "Charts",
                    icon = Icons.Outlined.PieChart,
                    description = "Visual expense charts",
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    onClick = { /* TODO: Implement charts */ }
                ),
                StatOption(
                    title = "Settings",
                    icon = Icons.Outlined.Settings,
                    description = "Statistics settings",
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    onClick = { /* TODO: Implement statistics settings */ }
                )
            )
            
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Statistics & Reports") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Expense Analytics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // Optimized grid implementation
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = true,
                content = {
                    items(
                        items = statOptions,
                        key = { it.title } // Use stable keys for better performance
                    ) { option ->
                        OptimizedStatCard(
                            option = option,
                            onCardClick = {
                                // Use coroutineScope to handle click events without blocking UI
                                coroutineScope.launch {
                                    option.onClick()
                }
            }
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun OptimizedStatCard(option: StatOption, onCardClick: () -> Unit) {
    // Use Surface instead of Card for better performance
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            // Optimized clickable with custom ripple effect
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true, color = MaterialTheme.colorScheme.primary),
                role = Role.Button,
                onClick = onCardClick
            ),
        color = option.color,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.title,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = option.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
} 