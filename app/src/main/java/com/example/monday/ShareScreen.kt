package com.example.monday

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date

// Project specific imports
import com.example.monday.viewmodels.MainViewModel
import com.example.monday.viewmodels.StatsViewModel
import com.example.monday.viewmodels.ExportViewModel
import com.example.monday.core.utils.shareExpensesList
import com.example.monday.core.utils.shareExpensesAsPdf
import com.example.monday.core.utils.parsePrice
import com.example.monday.core.utils.parseItemText
import com.example.monday.ui.modern.ModernColors
import com.example.monday.data.models.ExportHistoryItem
import com.example.monday.data.models.TodoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    todoViewModel: TodoViewModel,
    mainViewModel: MainViewModel,
    statsViewModel: StatsViewModel,
    exportViewModel: ExportViewModel,
    currentSelectedDate: LocalDate,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isSharing by remember { mutableStateOf(false) }
    
    // Read the export buffer and history from the ExportViewModel
    val exportBuffer by exportViewModel.exportBuffer.collectAsState()
    val exportHistory by exportViewModel.exportHistory.collectAsState()
    
    // Calculate totals
    val totalBufferedItems = exportBuffer.values.sumOf { it.size }
    val sumToShare = exportBuffer.values.flatten().sumOf { parsePrice(it.text) }

    // Use a TabRow to switch between "Preview" and "History"
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Preview Export", "Export History")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Expenses") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ModernColors.Eggshell
                )
            )
        },
        containerColor = ModernColors.Eggshell
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = ModernColors.Eggshell,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            if (selectedTabIndex == 0) {
                // Preview Tab
                ExportPreviewTab(
                    exportBuffer = exportBuffer,
                    totalSum = sumToShare,
                    isSharing = isSharing,
                    onShareImage = {
                        isSharing = true
                        shareExpensesList(
                            context = context,
                            itemsToShare = exportBuffer,
                            sumOfItemsToShare = sumToShare,
                            monthlySummaryText = null,
                            onFileReady = { file ->
                                exportViewModel.addExportToHistory(
                                    id = UUID.randomUUID().toString(),
                                    filePath = file.absolutePath,
                                    totalSum = sumToShare,
                                    itemCount = totalBufferedItems,
                                    type = "image",
                                    items = exportBuffer.values.flatten().map { it.text }
                                )
                                exportViewModel.uncheckExportedItems()
                                isSharing = false
                            }
                        )
                    },
                    onSharePdf = {
                        isSharing = true
                        shareExpensesAsPdf(
                            context = context,
                            itemsToShare = exportBuffer,
                            sumOfItemsToShare = sumToShare,
                            monthlySummaryText = null,
                            onFileReady = { file ->
                                exportViewModel.addExportToHistory(
                                    id = UUID.randomUUID().toString(),
                                    filePath = file.absolutePath,
                                    totalSum = sumToShare,
                                    itemCount = totalBufferedItems,
                                    type = "pdf",
                                    items = exportBuffer.values.flatten().map { it.text }
                                )
                                exportViewModel.uncheckExportedItems()
                                isSharing = false
                            }
                        )
                    },
                    onRemoveDate = { date -> exportViewModel.removeFromExportBuffer(date) }
                )
            } else {
                // History Tab
                ExportHistoryTab(
                    exportHistory = exportHistory,
                    onPinToggle = { item -> exportViewModel.updateExportHistoryItem(item.copy(isPinned = !item.isPinned)) },
                    onDelete = { item -> exportViewModel.deleteExportHistoryItem(item) }
                )
            }
        }
    }
}

@Composable
fun ExportPreviewTab(
    exportBuffer: Map<LocalDate, List<TodoItem>>,
    totalSum: Double,
    isSharing: Boolean,
    onShareImage: () -> Unit,
    onSharePdf: () -> Unit,
    onRemoveDate: (LocalDate) -> Unit
) {
    if (exportBuffer.isEmpty() || exportBuffer.values.all { it.isEmpty() }) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No items selected for export.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val sortedDates = exportBuffer.keys.sorted()
            sortedDates.forEach { date ->
                val items = exportBuffer[date] ?: emptyList()
                if (items.isNotEmpty()) {
                    item {
                        DateExportCard(date = date, items = items, onRemove = { onRemoveDate(date) })
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = ModernColors.CardBg)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Grand Total", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(String.format(Locale.getDefault(), "₹%.2f", totalSum), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ModernColors.Destructive)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Action Buttons
        Surface(
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            color = ModernColors.SoftCream
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onShareImage,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSharing
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export & Share as Image")
                }
                Button(
                    onClick = onSharePdf,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSharing,
                    colors = ButtonDefaults.buttonColors(containerColor = ModernColors.Transport)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ask to Send as PDF")
                }
                if (isSharing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun DateExportCard(date: LocalDate, items: List<TodoItem>, onRemove: () -> Unit) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, EEE", Locale.getDefault())
    val subtotal = items.sumOf { parsePrice(it.text) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = ModernColors.CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = date.format(dateFormatter),
                    color = ModernColors.Groceries,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove Date", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            items.forEach { item ->
                val (nameStr, quantity, priceStr) = parseItemText(item.text)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(text = nameStr, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    if (quantity != null) {
                        Text(text = quantity, color = ModernColors.Destructive, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                    Text(text = "₹$priceStr", fontSize = 14.sp)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subtotal", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text("₹$subtotal", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ExportHistoryTab(
    exportHistory: List<ExportHistoryItem>,
    onPinToggle: (ExportHistoryItem) -> Unit,
    onDelete: (ExportHistoryItem) -> Unit
) {
    if (exportHistory.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No export history.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(exportHistory) { item ->
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val dateStr = sdf.format(Date(item.timestamp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = ModernColors.CardBg)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (item.type == "pdf") Icons.Default.PictureAsPdf else Icons.Default.Image,
                                contentDescription = null,
                                tint = if (item.type == "pdf") ModernColors.Transport else ModernColors.Groceries,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(dateStr, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Row {
                            IconButton(onClick = { onPinToggle(item) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = if (item.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = "Pin",
                                    tint = if (item.isPinned) ModernColors.Destructive else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${item.itemCount} items exported • Total: ₹${item.totalSum}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}