package com.example.monday.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.monday.viewmodels.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.monday.ui.modern.ModernColors

/**
 * Dashboard & Date Bar settings section.
 * Controls: Stats view mode (both/cards/graph), Date bar alignment (start/mid/end).
 */
fun LazyListScope.dashboardSettingsSection(
    dashboardViewMode: String,
    dateBarPosition: String,
    scope: CoroutineScope,
    settingsViewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    onDashboardModeChange: (String) -> Unit,
    onDateBarPositionChange: (String) -> Unit
) {
    // Dashboard View Mode
    item {
        Text(
            text = "Dashboard",
            style = MaterialTheme.typography.titleLarge,
            color = ModernColors.DateText,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = ModernColors.CardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Stats View Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = ModernColors.DateText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Choose what to show in the hero dashboard area",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ModernColors.DateBorder,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val modes = listOf("both" to "Both", "cards" to "Cards", "graph" to "7-Day", "monthly" to "Monthly")
                SegmentedButtonRow(
                    options = modes,
                    selectedKey = dashboardViewMode,
                    onSelect = { modeKey, modeLabel ->
                        onDashboardModeChange(modeKey)
                        scope.launch {
                            settingsViewModel.saveDashboardViewMode(modeKey)
                            snackbarHostState.showSnackbar("Dashboard: $modeLabel")
                        }
                    }
                )
            }
        }
    }

    // Date Bar Alignment
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = ModernColors.CardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Date Bar Alignment",
                    style = MaterialTheme.typography.titleMedium,
                    color = ModernColors.DateText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Pin the selected date to start, middle, or end of the date strip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ModernColors.DateBorder,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val positions = listOf("start" to "Start", "mid" to "Middle", "end" to "End")
                SegmentedButtonRow(
                    options = positions,
                    selectedKey = dateBarPosition,
                    onSelect = { posKey, posLabel ->
                        onDateBarPositionChange(posKey)
                        scope.launch {
                            settingsViewModel.saveDateBarPosition(posKey)
                            snackbarHostState.showSnackbar("Date Bar: $posLabel")
                        }
                    }
                )
            }
        }
    }
}

/**
 * Shared segmented button row — eliminates duplication between
 * dashboard mode selector and date bar position selector.
 */
@Composable
fun SegmentedButtonRow(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (key: String, label: String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (key, label) ->
            val isSelected = selectedKey == key
            val bgColor = if (isSelected)
                ModernColors.EggnogDark
            else
                ModernColors.Eggshell

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable { onSelect(key, label) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected)
                        ModernColors.Eggshell
                    else
                        ModernColors.DateText,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
