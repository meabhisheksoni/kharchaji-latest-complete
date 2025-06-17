package com.example.monday.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.monday.BatchSaveRecordsHelper
import com.example.monday.TodoViewModel
import com.example.monday.formatForDisplay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchSaveScreen(
    todoViewModel: TodoViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    
    var isProcessing by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0f) }
    var currentDate by remember { mutableStateOf<LocalDate?>(null) }
    var totalSavedRecords by remember { mutableStateOf(0) }
    var totalDays by remember { mutableStateOf(0) }
    var currentDay by remember { mutableStateOf(0) }
    
    val batchSaveHelper = remember { BatchSaveRecordsHelper(context, todoViewModel) }
    
    val startDate = remember { LocalDate.of(2024, 3, 23) }
    val endDate = remember { LocalDate.now() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batch Save Records") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                                    Text(
                    text = "This will save all your expense entries as master records",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Date range: ${startDate.formatForDisplay()} to ${endDate.formatForDisplay()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            isProcessing = true
                            batchSaveHelper.batchSaveAllRecordsInRange(
                                startDate = startDate,
                                onProgress = { current, total, date ->
                                    currentDay = current
                                    totalDays = total
                                    currentDate = date
                                    currentProgress = current.toFloat() / total.toFloat()
                                },
                                onComplete = { saved ->
                                    totalSavedRecords = saved
                                    isProcessing = false
                                }
                            )
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Master Batch Save")
                    }
                }
            }
            
            if (isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Processing...",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        currentDate?.let {
                            Text(
                                text = "Current date: ${it.formatForDisplay()}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Progress: $currentDay / $totalDays days",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LinearProgressIndicator(
                            progress = { currentProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (totalSavedRecords > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Batch Save Completed!",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Saved $totalSavedRecords records across $totalDays days",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
} 