package com.example.monday

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecordScreen(
    recordId: Int,
    todoViewModel: TodoViewModel,
    onNavigateBack: () -> Unit,
    onSaveComplete: () -> Unit
) {
    // Fetch the record to edit
    val recordFlow = remember(recordId) {
        todoViewModel.getCalculationRecordById(recordId)
    }
    val recordState by recordFlow.collectAsState(initial = null)
    
    // Create a mutable state to track the edited record
    var editedRecord by remember { mutableStateOf<CalculationRecord?>(null) }
    
    // When the original record is loaded, initialize the edited record
    LaunchedEffect(recordState) {
        if (recordState != null && editedRecord == null) {
            editedRecord = recordState
        }
    }
    
    // For showing dialogs
    var showAddItemDialog by remember { mutableStateOf(false) }
    var itemToEditIndex by remember { mutableStateOf<Int?>(null) }
    var showConfirmSaveDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (editedRecord != null) {
                        if (editedRecord!!.isMasterSave) {
                            Text("Edit Master Record")
                        } else {
                            Text("Edit Record")
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
                colors = if (editedRecord?.isMasterSave == true) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFFFFF9C4),
                        titleContentColor = Color(0xFFFF9800)
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                },
                actions = {
                    IconButton(onClick = { showConfirmSaveDialog = true }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddItemDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        }
    ) { paddingValues ->
        if (editedRecord == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(
                        if (editedRecord!!.isMasterSave) Color(0xFFFFF9C4).copy(alpha = 0.3f) 
                        else MaterialTheme.colorScheme.background
                    )
            ) {
                // Record information
                Column(modifier = Modifier.padding(16.dp)) {
                    if (editedRecord!!.isMasterSave) {
                        Text(
                            "Master Record - ${editedRecord!!.recordDate.toLocalDate().formatForDisplay("dd MMM yyyy")}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFFFF9800)
                        )
                    } else {
                        Text(
                            "Record - ${editedRecord!!.recordDate.toLocalDate().formatForDisplay("dd MMM yyyy")}",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Total Sum: ₹${String.format("%.2f", editedRecord!!.totalSum)}", 
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // List of items
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    // Show items with edit and delete options
                    itemsIndexed(
                        items = editedRecord!!.items,
                        key = { index, item -> "${index}_${item.description}_${item.price}" }
                    ) { index, item ->
                        EditableRecordItemRow(
                            item = item,
                            onEditClick = { itemToEditIndex = index },
                            onDeleteClick = {
                                scope.launch {
                                    val updatedRecord = todoViewModel.removeRecordItem(editedRecord!!, index)
                                    editedRecord = updatedRecord
                                }
                            }
                        )
                    }
                }
            }
            
            // Add new item dialog
            if (showAddItemDialog) {
                RecordItemEditDialog(
                    item = null, // null indicates we're adding a new item
                    onDismiss = { showAddItemDialog = false },
                    onSave = { description, price, quantity ->
                        scope.launch {
                            // Add new item to the record
                            val updatedRecord = todoViewModel.addRecordItem(
                                editedRecord!!, 
                                description, 
                                price,
                                quantity
                            )
                            editedRecord = updatedRecord
                            showAddItemDialog = false
                        }
                    }
                )
            }
            
            // Edit existing item dialog
            itemToEditIndex?.let { index ->
                val itemToEdit = editedRecord!!.items[index]
                RecordItemEditDialog(
                    item = itemToEdit,
                    onDismiss = { itemToEditIndex = null },
                    onSave = { description, price, quantity ->
                        scope.launch {
                            // Update the item in the record
                            val updatedRecord = todoViewModel.updateRecordItem(
                                editedRecord!!, 
                                index, 
                                description, 
                                price,
                                quantity
                            )
                            editedRecord = updatedRecord
                            itemToEditIndex = null
                        }
                    }
                )
            }
            
            // Confirm save dialog
            if (showConfirmSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmSaveDialog = false },
                    title = { Text("Save Changes") },
                    text = { Text("Save all changes to this record?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    todoViewModel.updateCalculationRecord(editedRecord!!)
                                    showConfirmSaveDialog = false
                                    onSaveComplete()
                                }
                            }
                        ) {
                            Text("SAVE")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmSaveDialog = false }) {
                            Text("CANCEL")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EditableRecordItemRow(
    item: RecordItem,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Description
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                // Price and Quantity in a row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "₹${item.price}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    item.quantity?.let {
                        if (it.isNotBlank()) {
                            Text(
                                text = " · $it",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            
            // Edit button
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            
            // Delete button
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordItemEditDialog(
    item: RecordItem?,
    onDismiss: () -> Unit,
    onSave: (description: String, price: String, quantity: String?) -> Unit
) {
    var description by remember { mutableStateOf(item?.description ?: "") }
    var price by remember { mutableStateOf(item?.price ?: "") }
    
    // For quantity handling
    val predefinedQuantities = listOf("250g", "500g", "1kg", "1.5kg", "2kg")
    val customUnits = listOf("kg", "g", "pcs", "ltr", "mtr", "dozen", "items")
    
    // Parse existing quantity if any
    var selectedPredefinedQuantity by remember { 
        val initialQuantity = item?.quantity
        mutableStateOf(if (initialQuantity != null && predefinedQuantities.contains(initialQuantity)) initialQuantity else "")
    }
    
    var customQuantityValue by remember {
        val initialQuantity = item?.quantity
        mutableStateOf(if (initialQuantity != null && !predefinedQuantities.contains(initialQuantity)) {
            initialQuantity.filter { it.isDigit() || it == '.' }
        } else "")
    }
    
    var selectedCustomUnit by remember {
        val initialQuantity = item?.quantity
        var unit = ""
        if (initialQuantity != null && !predefinedQuantities.contains(initialQuantity)) {
            customUnits.sortedByDescending { it.length }.forEach { u ->
                if (initialQuantity.endsWith(u, ignoreCase = true)) {
                    unit = u
                    return@forEach
                }
            }
        }
        mutableStateOf(if (unit.isNotEmpty()) unit else (if (customQuantityValue.isNotEmpty()) "items" else ""))
    }
    
    var descriptionError by remember { mutableStateOf(false) }
    var priceError by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add New Item" else "Edit Item") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Description field
                OutlinedTextField(
                    value = description,
                    onValueChange = { 
                        description = it
                        descriptionError = it.isBlank()
                    },
                    label = { Text("Item Name") },
                    isError = descriptionError,
                    supportingText = { if (descriptionError) Text("Description cannot be empty") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words, 
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true
                )
                
                // Price field
                OutlinedTextField(
                    value = price,
                    onValueChange = { 
                        price = it
                        priceError = it.isBlank() || it.toDoubleOrNull() == null
                    },
                    label = { Text("Price (₹)") },
                    isError = priceError,
                    supportingText = { if (priceError) Text("Enter a valid price") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true
                )
                
                // Predefined quantity options
                Text("Quantity (Predefined)", style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(predefinedQuantities) { quantity ->
                        FilterChip(
                            selected = quantity == selectedPredefinedQuantity,
                            onClick = { 
                                selectedPredefinedQuantity = if (selectedPredefinedQuantity == quantity) "" else quantity
                                customQuantityValue = ""
                                selectedCustomUnit = ""
                            },
                            label = { Text(quantity) }
                        )
                    }
                }
                
                // Custom quantity section
                Text("Or Custom Quantity", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customQuantityValue, 
                        onValueChange = { 
                            customQuantityValue = it
                            selectedPredefinedQuantity = ""
                        }, 
                        label = { Text("Value") }, 
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        singleLine = true
                    )
                    
                    // Unit selection
                    LazyRow(
                        modifier = Modifier.weight(1f), 
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(customUnits) { unit ->
                            FilterChip(
                                selected = unit == selectedCustomUnit,
                                onClick = { 
                                    selectedCustomUnit = if (selectedCustomUnit == unit) "" else unit
                                    selectedPredefinedQuantity = ""
                                },
                                label = { Text(unit) },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (description.isBlank()) {
                        descriptionError = true
                        return@TextButton
                    }
                    
                    if (price.isBlank() || price.toDoubleOrNull() == null) {
                        priceError = true
                        return@TextButton
                    }
                    
                    // Prepare the final quantity string
                    val finalQuantity = when {
                        selectedPredefinedQuantity.isNotBlank() -> selectedPredefinedQuantity
                        customQuantityValue.isNotBlank() -> {
                            when {
                                selectedCustomUnit.isNotBlank() && selectedCustomUnit != "items" -> 
                                    customQuantityValue + selectedCustomUnit
                                else -> customQuantityValue
                            }
                        }
                        else -> null
                    }
                    
                    onSave(
                        description.trim(), 
                        price.trim(),
                        finalQuantity
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
