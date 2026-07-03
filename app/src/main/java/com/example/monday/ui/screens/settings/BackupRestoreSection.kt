package com.example.monday.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.monday.viewmodels.SettingsViewModel
import com.example.monday.TodoViewModel
import com.example.monday.core.utils.exportBackup
import com.example.monday.core.utils.importBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.monday.ui.modern.ModernColors

/**
 * Backup & Restore settings section.
 * Controls: Auto Master Save toggle, Export Data, Import Data.
 */
fun LazyListScope.backupRestoreSection(
    enableAutoMasterSave: Boolean,
    context: Context,
    scope: CoroutineScope,
    viewModel: TodoViewModel, mainViewModel: com.example.monday.viewmodels.MainViewModel, statsViewModel: com.example.monday.viewmodels.StatsViewModel,
    settingsViewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    onAutoMasterSaveChange: (Boolean) -> Unit,
    onImportClick: () -> Unit
) {
    item {
        Text(
            text = "Backup & Restore",
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
                    text = "Data Management",
                    style = MaterialTheme.typography.titleMedium,
                    color = ModernColors.DateText,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Auto Master Save Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto Master Save",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ModernColors.DateText
                        )
                        Text(
                            text = "Automatically add every new expense to today's Master List",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ModernColors.DateBorder
                        )
                    }
                    Switch(
                        checked = enableAutoMasterSave,
                        onCheckedChange = {
                            onAutoMasterSaveChange(it)
                            scope.launch {
                                settingsViewModel.savePaymentMonitorSetting("auto_master_save", it)
                                snackbarHostState.showSnackbar("Auto Master Save ${if (it) "enabled" else "disabled"}")
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ModernColors.Eggshell,
                            checkedTrackColor = ModernColors.EggnogDark
                        )
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Export Data
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { exportBackup(context, viewModel, mainViewModel, statsViewModel) }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Upload, contentDescription = "Export Data", tint = ModernColors.EggnogDark)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = "Export Data", style = MaterialTheme.typography.bodyLarge, color = ModernColors.DateText)
                        Text(
                            text = "Save all your expenses and settings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ModernColors.DateBorder
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Import Data
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onImportClick() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Import Data", tint = ModernColors.EggnogDark)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = "Import Data", style = MaterialTheme.typography.bodyLarge, color = ModernColors.DateText)
                        Text(
                            text = "Restore your expenses from backup",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ModernColors.DateBorder
                        )
                    }
                }
            }
        }
    }
}
