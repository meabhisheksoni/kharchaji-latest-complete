package com.example.monday.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.monday.AppDatabase
import com.example.monday.TodoItem
import com.example.monday.ui.theme.KharchajiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class ExpenseWidgetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            KharchajiTheme {
                ExpenseWidgetContent(
                    onClose = { finish() },
                    onSave = { itemName, price, quantity ->
                        saveExpense(itemName, price, quantity)
                    }
                )
            }
        }
    }
    
    private fun saveExpense(itemName: String, price: String, quantity: String?) {
        if (itemName.isBlank() || price.isBlank()) {
            Toast.makeText(this, "Please enter item name and price", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                // Format price
                val formattedPrice = String.format("%.2f", price.toDoubleOrNull() ?: 0.0)
                
                // Create the item text
                val itemText = if (quantity != null && quantity.isNotBlank()) {
                    "$itemName ($quantity) - ₹$formattedPrice"
                } else {
                    "$itemName - ₹$formattedPrice"
                }
                
                // Get current date timestamp
                val currentDate = LocalDate.now()
                val timestamp = currentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                
                // Create new item
                val newItem = TodoItem(
                    text = itemText,
                    timestamp = timestamp
                )
                
                // Insert into database
                withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(applicationContext)
                    database.todoDao().insert(newItem)
                }
                
                Toast.makeText(this@ExpenseWidgetActivity, "Expense added!", Toast.LENGTH_SHORT).show()
                finish()
                
            } catch (e: Exception) {
                Toast.makeText(this@ExpenseWidgetActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseWidgetContent(
    onClose: () -> Unit,
    onSave: (String, String, String?) -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var selectedQuantity by remember { mutableStateOf("") }
    var customQuantity by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("") }
    
    val predefinedQuantities = listOf("250g", "500g", "1kg", "1.5kg", "2kg")
    val units = listOf("kg", "g", "items")
    
    val saveExpense = {
        // Determine final quantity string
        val finalQuantity = when {
            selectedQuantity.isNotBlank() -> selectedQuantity
            customQuantity.isNotBlank() && selectedUnit.isNotBlank() -> "$customQuantity$selectedUnit"
            else -> null
        }
        onSave(itemName, price, finalQuantity)
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Add Expense") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Item Name Field
            OutlinedTextField(
                value = itemName,
                onValueChange = { itemName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Item name") },
                leadingIcon = { 
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Item"
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(8.dp)
            )
            
            // Price Field with Save Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Price (₹)") },
                    leadingIcon = { 
                        Text(
                            "₹",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                
                Button(
                    onClick = saveExpense,
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CE0B3)
                    )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Save",
                        tint = Color.Black
                    )
                }
            }
            
            // Quantity Selection
            Text(
                "Select Quantity",
                style = MaterialTheme.typography.titleMedium
            )
            
            // Predefined Quantities
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                predefinedQuantities.forEach { quantity ->
                    FilterChip(
                        selected = selectedQuantity == quantity,
                        onClick = { 
                            selectedQuantity = if (selectedQuantity == quantity) "" else quantity
                            // Clear custom quantity when predefined is selected
                            if (selectedQuantity.isNotBlank()) {
                                customQuantity = ""
                                selectedUnit = ""
                            }
                        },
                        label = { Text(quantity) },
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
            
            // Custom Quantity Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Custom Quantity Value
                OutlinedTextField(
                    value = customQuantity,
                    onValueChange = { 
                        customQuantity = it
                        // Clear predefined selection when custom is entered
                        if (customQuantity.isNotBlank()) {
                            selectedQuantity = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                
                // Unit Selection
                units.forEach { unit ->
                    FilterChip(
                        selected = selectedUnit == unit,
                        onClick = { 
                            selectedUnit = if (selectedUnit == unit) "" else unit
                            // Clear predefined selection when unit is selected
                            if (selectedUnit.isNotBlank()) {
                                selectedQuantity = ""
                            }
                        },
                        label = { Text(unit) },
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
        }
    }
}
