package com.example.monday

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.* 
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculationRecordsScreen(
    todoViewModel: TodoViewModel,
    displayDate: LocalDate?,
    onNavigateBack: () -> Unit,
    onRecordClick: (Int) -> Unit, // Callback for when a record is clicked, passing its ID
    onEditRecordClick: (Int) -> Unit = {} // New callback for edit action
) {
    // Memoize the Flow to prevent re-creation on every recomposition
    val recordsFlow = remember(displayDate, todoViewModel) {
        if (displayDate != null) {
            todoViewModel.getCalculationRecordsForDate(displayDate)
        } else {
            flowOf(emptyList<CalculationRecord>()) // Use flowOf for an empty list if date is null
        }
    }
    val records by recordsFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (displayDate != null) "Records for ${displayDate.formatForDisplay()}" else "Calculation Records (Error)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Potentially add a "Delete All Records" button here later
                }
            )
        }
    ) { paddingValues ->
        if (displayDate == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Error: Date not specified for records.")
            }
        } else if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No calculation records found.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sort records to show master saves at the top
                val sortedRecords = records.sortedWith(
                    compareByDescending<CalculationRecord> { it.isMasterSave }
                        .thenByDescending { it.timestamp }
                )
                
                items(sortedRecords, key = { it.id }) { record ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            when (dismissValue) {
                                SwipeToDismissBoxValue.EndToStart -> { // Swiped from right to left (delete)
                                    todoViewModel.deleteCalculationRecordById(record.id)
                                    true // Indicate the dismiss action is confirmed
                                }
                                SwipeToDismissBoxValue.StartToEnd -> { // Swiped from left to right (edit)
                                    onEditRecordClick(record.id)
                                    false // Return false to avoid dismissing the item
                                }
                                else -> false
                            }
                        },
                        positionalThreshold = { it * 0.25f } // Threshold to trigger dismiss
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = true, // Enable swipe from left to right for edit
                        enableDismissFromEndToStart = true,  // Enable swipe from right to left for delete
                        backgroundContent = {
                            val color = when (dismissState.dismissDirection) {
                                SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.7f)
                                SwipeToDismissBoxValue.StartToEnd -> Color.Blue.copy(alpha = 0.7f)
                                else -> Color.Transparent
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = when (dismissState.dismissDirection) {
                                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                    else -> Alignment.CenterEnd
                                }
                            ) {
                                when (dismissState.dismissDirection) {
                                    SwipeToDismissBoxValue.StartToEnd -> Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = Color.White
                                    )
                                    SwipeToDismissBoxValue.EndToStart -> Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.White
                                    )
                                    else -> {}
                                }
                            }
                        },
                        content = {
                            CalculationRecordItem(record = record, onClick = { onRecordClick(record.id) })
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculationRecordItem(
    record: CalculationRecord,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (record.isMasterSave) Color(0xFFFFF9C4) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Record ID: ${record.id} - Date: ${record.recordDate.toLocalDate().formatForDisplay("dd MMM yyyy")}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (record.isMasterSave) {
                    Text(
                        text = "MASTER",
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Total Sum: ₹${String.format("%.2f", record.totalSum)}")
            Text("Checked Items: ${record.checkedItemsCount} (Sum: ₹${String.format("%.2f", record.checkedItemsSum)})")
            // Further details like the list of items can be shown on a dedicated detail screen
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculationRecordDetailScreen(
    recordId: Int,
    todoViewModel: TodoViewModel,
    onNavigateBack: () -> Unit,
    onSetMemoAndReturnToExpenses: () -> Unit
) {
    // Memoize the Flow to prevent re-creation on every recomposition
    val recordFlow = remember(recordId, todoViewModel) {
        todoViewModel.getCalculationRecordById(recordId)
    }
    val recordState by recordFlow.collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (recordState != null) {
                        if (recordState?.isMasterSave == true) {
                            Text("Master Record")
                        } else {
                            Text("Record Details (ID: ${recordState?.id})")
                        }
                    } else {
                        Text("Loading...")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = if (recordState?.isMasterSave == true) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFFFFF9C4),
                        titleContentColor = Color(0xFFFF9800)
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                }
            )
        }
    ) { paddingValues ->
        if (recordState == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else { 
            // Use recordState with safe calls (?) to avoid smart cast issues
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(if (recordState?.isMasterSave == true) Color(0xFFFFF9C4).copy(alpha = 0.3f) else MaterialTheme.colorScheme.background)
            ) { // Main container Column
                // Header Section (Not scrollable)
                Column(modifier = Modifier.padding(16.dp)) {
                    if (recordState?.isMasterSave != true) {
                        recordState?.recordDate?.let { date ->
                            Text("Date Recorded: ${date.toLocalDate().formatForDisplay("dd MMM yyyy")}", 
                                style = MaterialTheme.typography.titleSmall)
                        }
                        recordState?.timestamp?.let { timestamp ->
                            Text("Created: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))}", 
                                style = MaterialTheme.typography.titleLarge)
                        }
                    } else {
                        recordState?.recordDate?.let { date ->
                            Text(
                                "Master Record - ${date.toLocalDate().formatForDisplay("dd MMM yyyy")}",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    recordState?.totalSum?.let { sum ->
                        Text("Total Sum: ₹${String.format("%.2f", sum)}", 
                            style = MaterialTheme.typography.titleMedium)
                    }
                    
                    if (recordState?.isMasterSave != true) {
                        Text("Checked Items: ${recordState?.checkedItemsCount ?: 0} (Sum: ₹${String.format("%.2f", recordState?.checkedItemsSum ?: 0.0)})", 
                            style = MaterialTheme.typography.titleMedium)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Items:", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Scrollable Items List
                LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    val record = recordState
                    if (record != null) {
                        if (record.isMasterSave) {
                            // For master records, show items with serial numbers and without checked status
                            itemsIndexed(record.items, key = { index, item -> "${record.id}_${item.description}_${item.price}_${index}" }) { index, item ->
                                MasterRecordListItem(index = index + 1, item = item)
                            }
                        } else {
                            // For regular records, show items with checked status
                            itemsIndexed(record.items, key = { index, item -> "${record.id}_${item.description}_${item.price}_${index}" }) { index, item ->
                                RecordDetailListItem(item = item)
                            }
                        }
                    }
                }

                // Footer Buttons Section (Not scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), // Add padding around the button row
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val record = recordState
                    if (record != null) {
                        if (!record.isMasterSave) {
                            Button(
                                onClick = { 
                                    todoViewModel.loadRecordItemsAsCurrentExpenses(record.items, record.recordDate.toLocalDate())
                                    onSetMemoAndReturnToExpenses()
                                },
                                modifier = Modifier.weight(1f).padding(end = 4.dp)
                            ) {
                                Text("MERGE")
                            }
                        } else {
                            Button(
                                onClick = { 
                                    todoViewModel.loadRecordItemsAsCurrentExpenses(record.items, record.recordDate.toLocalDate())
                                    onSetMemoAndReturnToExpenses()
                                },
                                modifier = Modifier.weight(1f).padding(end = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9800)
                                )
                            ) {
                                Text("MERGE")
                            }
                        }
                        
                        // New Clear and Set button
                        Button(
                            onClick = { 
                                todoViewModel.clearAndSetRecordItems(record.items, record.recordDate.toLocalDate())
                                onSetMemoAndReturnToExpenses()
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text("CLEAR AND SET")
                        }
                        
                        Button(
                            onClick = { 
                                todoViewModel.deleteCalculationRecordById(record.id)
                                onNavigateBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        ) {
                            Text("DELETE")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordDetailListItem(item: RecordItem) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.description,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            item.quantity?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "₹${item.price}",
                style = MaterialTheme.typography.bodyLarge
            )
            if(item.isChecked){
                Icon(Icons.Filled.Check, contentDescription = "Checked", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 8.dp))
            }
        }
        HorizontalDivider()
    }
}

@Composable
fun MasterRecordListItem(index: Int, item: RecordItem) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Serial number
            Text(
                text = "$index.",
                modifier = Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Item description
            Text(
                text = item.description,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            
            // Quantity if available
            item.quantity?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Price
            Text(
                text = "₹${item.price}",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        HorizontalDivider()
    }
}
