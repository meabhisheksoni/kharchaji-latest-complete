package com.example.monday.widget

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.example.monday.ui.theme.KharchajiTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class WidgetInputActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "WidgetInputActivity"
    }
    
    private var currentStep by mutableStateOf("item_name")
    private var itemNameValue by mutableStateOf("")
    private var priceValue by mutableStateOf("")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make dialog appear at top with keyboard - use ADJUST_PAN to prevent shifting
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        
        // Set window to appear at top
        window.setGravity(android.view.Gravity.TOP)
        
        val initialField = intent.getStringExtra("field") ?: "item_name"
        currentStep = initialField
        
        Log.d(TAG, "onCreate: initialField=$initialField")

        setContent {
            KharchajiTheme {
                ChainedInputDialog(
                    currentStep = currentStep,
                    onDismiss = { 
                        Log.d(TAG, "Dialog dismissed")
                        finish() 
                    },
                    onItemNameSave = { value ->
                        Log.d(TAG, "Item name saved: $value")
                        itemNameValue = value
                        saveItemName(value)
                        // Smoothly transition to price input
                        currentStep = "price"
                    },
                    onPriceSave = { value, shouldOpenQuantity ->
                        Log.d(TAG, "Price saved: $value, openQuantity=$shouldOpenQuantity")
                        priceValue = value
                        
                        lifecycleScope.launch {
                            // Wait for price to be saved before proceeding
                            savePriceAsync(value)
                            
                            if (shouldOpenQuantity) {
                                // Open quantity selection
                                currentStep = "quantity"
                            } else {
                                // Auto-submit and loop back to item name
                                submitExpense()
                                // Reset to item name for next entry
                                currentStep = "item_name"
                            }
                        }
                    },
                    onQuantitySave = { qty, unit ->
                        Log.d(TAG, "Quantity saved: $qty $unit")
                        lifecycleScope.launch {
                            // Wait for quantity to be saved before submitting
                            saveQuantityAsync(qty, unit)
                            submitExpense()
                            // Loop back to item name for next entry
                            currentStep = "item_name"
                        }
                    }
                )
            }
        }
    }

    private fun saveItemName(value: String) {
        lifecycleScope.launch {
            try {
                val context = applicationContext
                val glanceId = GlanceAppWidgetManager(context)
                    .getGlanceIds(ExpenseGlanceWidget::class.java)
                    .firstOrNull()
                
                if (glanceId == null) {
                    Log.e(TAG, "No GlanceId found")
                    return@launch
                }

                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[ExpenseGlanceWidget.ITEM_NAME_KEY] = value
                }
                
                val sharedPrefs = context.getSharedPreferences("glance_prefs_${glanceId}", MODE_PRIVATE)
                sharedPrefs.edit().putString("widget_item", value).apply()

                ExpenseGlanceWidget().update(context, glanceId)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving item name", e)
            }
        }
    }
    
    private suspend fun savePriceAsync(price: String) {
        try {
            val context = applicationContext
            val glanceId = GlanceAppWidgetManager(context)
                .getGlanceIds(ExpenseGlanceWidget::class.java)
                .firstOrNull()
            
            if (glanceId == null) {
                Log.e(TAG, "No GlanceId found")
                return
            }

            Log.d(TAG, "Saving price: $price")
            
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[ExpenseGlanceWidget.PRICE_KEY] = price
            }
            
            val sharedPrefs = context.getSharedPreferences("glance_prefs_${glanceId}", MODE_PRIVATE)
            sharedPrefs.edit().putString("widget_price", price).commit() // Use commit() for synchronous save
            
            Log.d(TAG, "Price saved to SharedPreferences: ${sharedPrefs.getString("widget_price", "NOT_FOUND")}")

            ExpenseGlanceWidget().update(context, glanceId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving price", e)
        }
    }
    
    private suspend fun saveQuantityAsync(qty: String, unit: String) {
        try {
            val context = applicationContext
            val glanceId = GlanceAppWidgetManager(context)
                .getGlanceIds(ExpenseGlanceWidget::class.java)
                .firstOrNull()
            
            if (glanceId == null) {
                Log.e(TAG, "No GlanceId found")
                return
            }

            Log.d(TAG, "Saving quantity: $qty $unit")

            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[ExpenseGlanceWidget.QTY_KEY] = qty
                prefs[ExpenseGlanceWidget.UNIT_KEY] = unit
            }
            
            val sharedPrefs = context.getSharedPreferences("glance_prefs_${glanceId}", MODE_PRIVATE)
            sharedPrefs.edit().apply {
                putString("widget_qty", qty)
                putString("widget_unit", unit)
            }.commit() // Use commit() for synchronous save
            
            Log.d(TAG, "Quantity saved to SharedPreferences: qty=${sharedPrefs.getString("widget_qty", "NOT_FOUND")}, unit=${sharedPrefs.getString("widget_unit", "NOT_FOUND")}")

            ExpenseGlanceWidget().update(context, glanceId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving quantity", e)
        }
    }
    
    private fun submitExpense() {
        lifecycleScope.launch {
            try {
                val context = applicationContext
                val glanceId = GlanceAppWidgetManager(context)
                    .getGlanceIds(ExpenseGlanceWidget::class.java)
                    .firstOrNull()
                
                if (glanceId == null) {
                    Log.e(TAG, "No GlanceId found")
                    return@launch
                }
                
                val sharedPrefs = context.getSharedPreferences("glance_prefs_${glanceId}", MODE_PRIVATE)
                val itemName = sharedPrefs.getString("widget_item", "") ?: ""
                val price = sharedPrefs.getString("widget_price", "0") ?: "0"
                val qty = sharedPrefs.getString("widget_qty", "") ?: ""
                val unit = sharedPrefs.getString("widget_unit", "") ?: ""
                
                Log.d(TAG, "Submitting expense: item=$itemName, price=$price, qty=$qty, unit=$unit")
                
                // Validate
                val priceDouble = price.toDoubleOrNull()
                if (priceDouble == null || priceDouble <= 0) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(this@WidgetInputActivity, "Please enter a valid price", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // Schedule WorkManager task
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<SaveExpenseWorker>()
                    .setInputData(
                        androidx.work.workDataOf(
                            "item_name" to (if (itemName.isBlank()) "Unnamed" else itemName),
                            "price" to price,
                            "qty" to qty,
                            "unit" to unit,
                            "glance_id" to glanceId.toString()
                        )
                    )
                    .build()
                
                androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
                
                // Reset widget state
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[ExpenseGlanceWidget.ITEM_NAME_KEY] = ""
                    prefs[ExpenseGlanceWidget.PRICE_KEY] = "0"
                    prefs[ExpenseGlanceWidget.QTY_KEY] = ""
                    prefs[ExpenseGlanceWidget.UNIT_KEY] = ""
                }
                
                // Also reset SharedPreferences
                sharedPrefs.edit().apply {
                    putString("widget_item", "")
                    putString("widget_price", "0")
                    putString("widget_qty", "")
                    putString("widget_unit", "")
                }.apply()
                
                ExpenseGlanceWidget().update(context, glanceId)
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(this@WidgetInputActivity, "Expense saved!", android.widget.Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error submitting expense", e)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChainedInputDialog(
    currentStep: String,
    onDismiss: () -> Unit,
    onItemNameSave: (String) -> Unit,
    onPriceSave: (String, Boolean) -> Unit,
    onQuantitySave: (String, String) -> Unit
) {
    var value by remember(currentStep) { mutableStateOf("") }
    var selectedQty by remember(currentStep) { mutableStateOf("") }
    var selectedUnit by remember(currentStep) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val (label, keyboardType, capitalization) = when (currentStep) {
        "item_name" -> Triple("Item Name", KeyboardType.Text, KeyboardCapitalization.Sentences)
        "price" -> Triple("Price", KeyboardType.Decimal, KeyboardCapitalization.None)
        "quantity" -> Triple("Quantity (Optional)", KeyboardType.Decimal, KeyboardCapitalization.None)
        else -> Triple("Value", KeyboardType.Text, KeyboardCapitalization.None)
    }

    // Auto-focus when step changes
    LaunchedEffect(currentStep) {
        if (currentStep != "quantity") {
            kotlinx.coroutines.delay(50)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                Log.e("ChainedInputDialog", "Error requesting focus", e)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter $label") },
        text = {
            when (currentStep) {
                "quantity" -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Preset quantity buttons
                        Text("Select Quantity", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("250g", "500g", "1kg", "1.5kg", "2kg").forEach { qty ->
                                FilterChip(
                                    selected = selectedQty == qty,
                                    onClick = { 
                                        if (selectedQty == qty) {
                                            // Deselect - clear both quantity and unit
                                            selectedQty = ""
                                            selectedUnit = ""
                                        } else {
                                            // Select - set quantity and unit
                                            selectedQty = qty
                                            selectedUnit = "g"
                                        }
                                    },
                                    label = { Text(qty, style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Custom quantity
                        OutlinedTextField(
                            value = value,
                            onValueChange = { 
                                value = it
                                selectedQty = ""
                            },
                            label = { Text("Custom Quantity") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Unit buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("kg", "g", "items").forEach { unit ->
                                FilterChip(
                                    selected = selectedUnit == unit,
                                    onClick = { 
                                        // Toggle unit selection
                                        selectedUnit = if (selectedUnit == unit) "" else unit
                                    },
                                    label = { Text(unit) }
                                )
                            }
                        }
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(label) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = keyboardType,
                            capitalization = capitalization,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (value.isNotBlank()) {
                                    when (currentStep) {
                                        "item_name" -> onItemNameSave(value)
                                        "price" -> onPriceSave(value, false) // Auto-submit on Enter
                                    }
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
            }
        },
        confirmButton = {
            when (currentStep) {
                "price" -> {
                    // Green arrow button for quantity selection
                    IconButton(
                        onClick = {
                            if (value.isNotBlank()) {
                                onPriceSave(value, true) // Open quantity
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .then(
                                if (value.isNotBlank()) {
                                    Modifier.background(
                                        androidx.compose.ui.graphics.Color(0xFF4CE0B3),
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                                } else Modifier
                            )
                    ) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Add Quantity",
                            tint = if (value.isNotBlank()) androidx.compose.ui.graphics.Color.Black 
                                   else androidx.compose.ui.graphics.Color.Gray
                        )
                    }
                }
                "quantity" -> {
                    Button(
                        onClick = {
                            val finalQty = if (selectedQty.isNotBlank()) {
                                selectedQty.replace("kg", "").replace("g", "")
                            } else {
                                value
                            }
                            val finalUnit = if (selectedQty.isNotBlank()) "g" else selectedUnit
                            onQuantitySave(finalQty, finalUnit)
                        }
                    ) {
                        Text("Save")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            if (value.isNotBlank()) {
                                onItemNameSave(value)
                            }
                        }
                    ) {
                        Text("Next")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
