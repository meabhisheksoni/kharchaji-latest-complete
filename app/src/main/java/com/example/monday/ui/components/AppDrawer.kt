package com.example.monday.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
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
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBatchSave: () -> Unit = {},
    onSettings: () -> Unit = {}
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Text(
            "Kharchaji Menu",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.headlineMedium
        )
        HorizontalDivider()
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Save, contentDescription = "Batch Save Records") },
            label = { Text("Batch Save All Records") },
            selected = false,
            onClick = onBatchSave
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.UploadFile, contentDescription = "Export Data") },
            label = { Text("Export Data") },
            selected = false,
            onClick = onExport
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Download, contentDescription = "Load Data") },
            label = { Text("Import Data") },
            selected = false,
            onClick = onImport
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            selected = false,
            onClick = onSettings
        )
    }
} 