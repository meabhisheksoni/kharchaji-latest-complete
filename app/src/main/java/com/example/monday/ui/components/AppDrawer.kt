package com.example.monday.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppDrawerContent(
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    onBatchSave: () -> Unit = {},
    onSettings: () -> Unit = {},
    onExportCalendarView: () -> Unit = {},
    onAllExpenses: () -> Unit = {},
    onSaveAll: () -> Unit = {}
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Text(
            "Kharchaji Menu",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.headlineMedium
        )
        HorizontalDivider()
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.List, contentDescription = "All Expenses") },
            label = { Text("All Expenses") },
            selected = false,
            onClick = onAllExpenses
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar View") },
            label = { Text("Export Calendar View") },
            selected = false,
            onClick = onExportCalendarView
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Save, contentDescription = "Batch Save Records") },
            label = { Text("Batch Save All Records") },
            selected = false,
            onClick = onBatchSave
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Storage, contentDescription = "View All Records") },
            label = { Text("View All Records") },
            selected = false,
            onClick = onSaveAll
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            selected = false,
            onClick = onSettings
        )
    }
} 