package com.example.monday.widget

import com.example.monday.core.utils.*
import android.content.Context
import com.example.monday.data.models.TodoItem
import com.example.monday.data.local.TodoDao
import com.example.monday.data.local.AppDatabase

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.example.monday.ui.theme.KharchajiTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.activity.viewModels
import com.example.monday.TodoViewModel
import com.example.monday.viewmodels.MainViewModel
import com.example.monday.MasterSaveHelper
import java.time.LocalDate
import java.time.ZoneId

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WidgetInputActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "WidgetInputActivity"
    }
    
    private var currentStep by mutableStateOf("item_name")
    private var itemNameValue by mutableStateOf("")
    private var priceValue by mutableStateOf("")
    private var prefillAmountValue by mutableStateOf("")
    // Counter to force-reset composable state when saving in manual mode
    private var resetCounter by mutableStateOf(0)

    private val todoViewModel: TodoViewModel by viewModels()
    private val mainViewModel: com.example.monday.viewmodels.MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make keyboard appear without shifting dialog
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        
        prefillAmountValue = intent.getStringExtra("prefill_amount") ?: ""
        // Always start on the merged name+price step
        currentStep = "name_and_price"
        
        // If we have a prefill amount, pre-save it so it's ready
        if (prefillAmountValue.isNotBlank()) {
            priceValue = prefillAmountValue
            lifecycleScope.launch {
                savePriceAsync(applicationContext, prefillAmountValue)
            }
        }

        setContent {
            val uniqueNames by mainViewModel.uniqueItemNames.collectAsState()
            KharchajiTheme {
                ChainedInputDialog(
                    currentStep = currentStep,
                    prefillAmount = prefillAmountValue,
                    resetKey = resetCounter,
                    uniqueNames = uniqueNames,
                    onDismiss = { _, _ ->
                        finish() 
                    },
                    onSave = { name, price, qty, unit ->
                        
                        // 1. Validate Synchronously
                        val priceDouble = price.toDoubleOrNull()
                        if (priceDouble == null || priceDouble <= 0) {
                            android.widget.Toast.makeText(this@WidgetInputActivity, "Please enter a valid price", android.widget.Toast.LENGTH_SHORT).show()
                            return@ChainedInputDialog
                        }

                        // 2. Set State
                        itemNameValue = name
                        priceValue = price

                        // 3. Fire and Forget Coroutine for all DB/Glance operations
                        val appContext = applicationContext
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            saveItemName(appContext, name)
                            savePriceAsync(appContext, price)
                            if (qty.isNotBlank()) {
                                saveQuantityAsync(appContext, qty, unit)
                            }
                            submitExpense(appContext, name, priceDouble, qty, unit)
                        }
                        
                        // 4. Instant UI Feedback and Teardown
                        android.widget.Toast.makeText(this@WidgetInputActivity, "Expense saved!", android.widget.Toast.LENGTH_SHORT).show()
                        if (prefillAmountValue.isNotBlank()) {
                            finish()
                        } else {
                            resetCounter++
                            currentStep = "name_and_price"
                        }
                    }
                )
            }
        }
    }

    private suspend fun saveItemName(context: android.content.Context, value: String) {
        try {
            val glanceId = GlanceAppWidgetManager(context)
                .getGlanceIds(ExpenseGlanceWidget::class.java)
                .firstOrNull()
            
            if (glanceId == null) return

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
    
    private suspend fun savePriceAsync(context: android.content.Context, price: String) {
        try {
            val glanceId = GlanceAppWidgetManager(context)
                .getGlanceIds(ExpenseGlanceWidget::class.java)
                .firstOrNull()
            
            if (glanceId == null) return
            
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[ExpenseGlanceWidget.PRICE_KEY] = price
            }
            
            val sharedPrefs = context.getSharedPreferences("glance_prefs_${glanceId}", MODE_PRIVATE)
            sharedPrefs.edit().putString("widget_price", price).commit()

            ExpenseGlanceWidget().update(context, glanceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving price", e)
        }
    }
    
    private suspend fun saveQuantityAsync(context: android.content.Context, qty: String, unit: String) {
        try {
            val glanceId = GlanceAppWidgetManager(context)
                .getGlanceIds(ExpenseGlanceWidget::class.java)
                .firstOrNull()
            
            if (glanceId == null) return

            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[ExpenseGlanceWidget.QTY_KEY] = qty
                prefs[ExpenseGlanceWidget.UNIT_KEY] = unit
            }
            
            val sharedPrefs = context.getSharedPreferences("glance_prefs_${glanceId}", MODE_PRIVATE)
            sharedPrefs.edit().apply {
                putString("widget_qty", qty)
                putString("widget_unit", unit)
            }.commit()

            ExpenseGlanceWidget().update(context, glanceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving quantity", e)
        }
    }
    
    private suspend fun submitExpense(context: android.content.Context, name: String, priceDouble: Double, qty: String, unit: String) {
        try {
            
            val finalName = if (name.isBlank()) "Unnamed" else name
            val formattedPrice = String.format("%.2f", priceDouble)

            val itemText = if (qty.isNotBlank() && unit.isNotBlank()) {
                val qtyDouble = qty.toDoubleOrNull() ?: 0.0
                if (qtyDouble > 0) {
                    val quantityDisplay = when (unit) {
                        "kg" -> if (qtyDouble >= 1) "${qtyDouble.toInt()}kg" else "${(qtyDouble * 1000).toInt()}g"
                        "g" -> if (qtyDouble >= 1000) "${(qtyDouble / 1000)}kg" else "${qtyDouble.toInt()}g"
                        "ml" -> if (qtyDouble >= 1000) "${(qtyDouble / 1000)}L" else "${qtyDouble.toInt()}ml"
                        "L" -> if (qtyDouble >= 1) "${qtyDouble.toInt()}L" else "${(qtyDouble * 1000).toInt()}ml"
                        "items" -> if (qtyDouble == 1.0) "1 item" else "${qtyDouble.toInt()} items"
                        else -> "$qtyDouble$unit"
                    }
                    "$finalName ($quantityDisplay) - ₹$formattedPrice"
                } else {
                    "$finalName - ₹$formattedPrice"
                }
            } else if (qty.isNotBlank()) {
                val qtyDouble = qty.toDoubleOrNull() ?: 0.0
                if (qtyDouble > 0) {
                    val countDisplay = if (qtyDouble == 1.0) "1 item" else "${qtyDouble.toInt()} items"
                    "$finalName ($countDisplay) - ₹$formattedPrice"
                } else {
                    "$finalName - ₹$formattedPrice"
                }
            } else {
                "$finalName - ₹$formattedPrice"
            }

            val currentDate = LocalDate.now()
            // Use actual creation time, not midnight
            val timestamp = System.currentTimeMillis()

            val todoItem = TodoItem(
                text = itemText,
                timestamp = timestamp,
                isDone = false
            )

            val database = AppDatabase.getDatabase(context)
            val insertedId = database.todoDao().insertAndGetId(todoItem)

            val prefManager = com.example.monday.managers.PreferenceManager.from(context)
            val autoMasterSave = prefManager.getPaymentMonitorSetting("auto_master_save") ?: false
            if (autoMasterSave) {
                val itemWithId = todoItem.copy(id = insertedId.toInt())
                MasterSaveHelper.appendToMasterAsync(context, itemWithId)
            }
            
            // Update widget if it exists
            val glanceId = GlanceAppWidgetManager(context)
                .getGlanceIds(ExpenseGlanceWidget::class.java)
                .firstOrNull()
            
            if (glanceId != null) {
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[ExpenseGlanceWidget.ITEM_NAME_KEY] = ""
                    prefs[ExpenseGlanceWidget.PRICE_KEY] = "0"
                    prefs[ExpenseGlanceWidget.QTY_KEY] = ""
                    prefs[ExpenseGlanceWidget.UNIT_KEY] = ""
                }
                
                val sharedPrefs = context.getSharedPreferences("glance_prefs_${glanceId}", MODE_PRIVATE)
                sharedPrefs.edit().apply {
                    putString("widget_item", "")
                    putString("widget_price", "0")
                    putString("widget_qty", "")
                    putString("widget_unit", "")
                }.apply()
                
                ExpenseGlanceWidget().update(context, glanceId)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in background save", e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChainedInputDialog(
    currentStep: String,
    prefillAmount: String = "",
    resetKey: Int = 0,
    uniqueNames: List<String> = emptyList(),
    onDismiss: (String, String) -> Unit,
    onSave: (name: String, price: String, qty: String, unit: String) -> Unit
) {
    val isPrefilled = prefillAmount.isNotBlank()
    var itemName by remember(currentStep, prefillAmount, resetKey) { mutableStateOf("") }
    var price by remember(currentStep, prefillAmount, resetKey) { mutableStateOf(if (isPrefilled) prefillAmount else "") }
    var customQty by remember(currentStep, resetKey) { mutableStateOf("") }
    var selectedUnit by remember(currentStep, resetKey) { mutableStateOf("") }
    var selectedPresetTag by remember(currentStep, resetKey) { mutableStateOf("") }
    var showPresetTags by remember(currentStep, resetKey) { mutableStateOf(false) }
    var isQtyFieldFocused by remember { mutableStateOf(false) }
    var isNameFocused by remember { mutableStateOf(true) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val nameFocusRequester = remember { FocusRequester() }
    val priceFocusRequester = remember { FocusRequester() }
    val qtyFocusRequester = remember { FocusRequester() }
    
    // startsWith filter so "M" only shows M-items; hide if exact match already selected
    val filteredNames = if (itemName.isBlank()) {
        emptyList()
    } else {
        uniqueNames.filter { it.startsWith(itemName, ignoreCase = true) && !it.equals(itemName, ignoreCase = true) }
    }

    // Resolve final qty + unit from either preset or custom
    fun resolveQtyUnit(): Pair<String, String> {
        // Preset takes priority if selected
        if (selectedPresetTag.isNotBlank()) {
            return when {
                selectedPresetTag.endsWith("kg") -> {
                    val num = selectedPresetTag.replace("kg", "")
                    val grams = ((num.toDoubleOrNull() ?: 0.0) * 1000).toInt()
                    grams.toString() to "g"
                }
                selectedPresetTag.endsWith("g") -> {
                    selectedPresetTag.replace("g", "") to "g"
                }
                else -> selectedPresetTag to "g"
            }
        }
        // Custom qty â€” save number even without a unit (implies pcs/count)
        if (customQty.isNotBlank()) {
            return customQty to selectedUnit
        }
        return "" to ""
    }

    fun doSave() {
        if (itemName.isNotBlank() && price.isNotBlank()) {
            val (qty, unit) = resolveQtyUnit()
            try { nameFocusRequester.requestFocus() } catch (_: Exception) {} // Prevent keyboard closing flash
            onSave(itemName.trim(), price.trim(), qty, unit)
        }
    }

    // Auto-focus item name on open
    LaunchedEffect(currentStep, resetKey) {
        try { nameFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = { onDismiss(currentStep, "") }
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 120.dp, bottom = 24.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Consume click to prevent dismiss */ }
                ),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Add Expense",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Input fields
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                // ── Item Name with inline suggestions ──
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Item Name") },
                        textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = if (isPrefilled && price.isNotBlank()) ImeAction.Done else ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { 
                                if (!isPrefilled) priceFocusRequester.requestFocus() 
                            },
                            onDone = { doSave() }
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequester)
                            .onFocusChanged { isNameFocused = it.isFocused }
                    )

                    // Inline suggestions — only when name field is focused
                    if (isNameFocused && filteredNames.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                            shadowElevation = 2.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 155.dp) // exactly 5 compact items
                                    .padding(top = 4.dp) // spacing from text field
                                    .verticalScroll(rememberScrollState())
                            ) {
                                filteredNames.forEach { suggestion ->
                                    Text(
                                        text = suggestion,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                itemName = suggestion
                                                // Keep keyboard open: move focus to price if not prefilled,
                                                // otherwise re-focus name so user can hit Done/Enter to save
                                                if (!isPrefilled) priceFocusRequester.requestFocus() else nameFocusRequester.requestFocus()
                                            }
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // â”€â”€ Price row (full width when Qty hidden, split when shown) â”€â”€
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Price field â€” Indian comma formatting
                    OutlinedTextField(
                        value = price,
                        onValueChange = { newVal ->
                            if (!isPrefilled) {
                                // Only allow digits and one decimal point
                                val filtered = newVal.filter { it.isDigit() || it == '.' }
                                price = filtered
                            }
                        },
                        label = { Text("₹ Price") },
                        textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        // Indian comma formatting for display
                        visualTransformation = remember { com.example.monday.core.utils.IndianCurrencyVisualTransformation() },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { doSave() }
                        ),
                        readOnly = isPrefilled,
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(priceFocusRequester)
                    )

                    // Custom Qty field â€” only visible when eye toggle is on
                    if (showPresetTags) {
                        OutlinedTextField(
                            value = customQty,
                            onValueChange = {
                                customQty = it
                                if (it.isNotBlank()) selectedPresetTag = ""
                            },
                            label = { Text("Qty") },
                            textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { doSave() }
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(qtyFocusRequester)
                                .onFocusChanged { isQtyFieldFocused = it.isFocused }
                        )
                    }
                }

                // â”€â”€ Unit selector tags â€” only when eye toggle is on and qty focused â”€â”€
                if (showPresetTags && (isQtyFieldFocused || customQty.isNotBlank())) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf("kg", "g", "ml", "L").forEach { unit ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (selectedUnit == unit) Color(0xFF6F6F6F) else Color.Transparent,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        selectedUnit = if (selectedUnit == unit) "" else unit
                                        if (selectedUnit.isNotBlank()) selectedPresetTag = ""
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = unit,
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = if (selectedUnit == unit) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedUnit == unit) Color.Black else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                }
                }

                // â”€â”€ Action and Presets Section â”€â”€
                Column(modifier = Modifier.fillMaxWidth()) {
                    // â”€â”€ Action icons row â”€â”€
                    Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cross â€” cancel (plain grey icon)
                    IconButton(
                        onClick = { onDismiss(currentStep, "") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Tick â€” save (plain grey icon)
                    IconButton(
                        onClick = { doSave() },
                        enabled = itemName.isNotBlank() && price.isNotBlank(),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save",
                            tint = Color.Gray
                        )
                    }
                }

                // â”€â”€ Eye toggle + Preset tags â”€â”€
                Spacer(modifier = Modifier.height(8.dp))

                // Eye toggle row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showPresetTags = !showPresetTags },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (showPresetTags) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle quantity presets",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                // Preset tag rows (instant, no animation)
                if (showPresetTags) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Row 1: grams
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf("100g", "200g", "250g", "300g", "500g").forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .then(
                                            if (selectedPresetTag == tag) {
                                                Modifier.background(Color(0xFF6F6F6F), RectangleShape)
                                            } else Modifier
                                        )
                                        .clickable {
                                            if (selectedPresetTag == tag) {
                                                selectedPresetTag = ""
                                            } else {
                                                selectedPresetTag = tag
                                                // Preset overrides custom input
                                                customQty = ""
                                                selectedUnit = ""
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tag,
                                        style = TextStyle(
                                            fontSize = 13.sp,
                                            fontWeight = if (selectedPresetTag == tag) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedPresetTag == tag) Color.Black else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }

                        // Row 2: kilograms
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf("1kg", "1.5kg", "2kg", "3kg", "4kg", "5kg").forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .then(
                                            if (selectedPresetTag == tag) {
                                                Modifier.background(Color(0xFF6F6F6F), RectangleShape)
                                            } else Modifier
                                        )
                                        .clickable {
                                            if (selectedPresetTag == tag) {
                                                selectedPresetTag = ""
                                            } else {
                                                selectedPresetTag = tag
                                                customQty = ""
                                                selectedUnit = ""
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tag,
                                        style = TextStyle(
                                            fontSize = 13.sp,
                                            fontWeight = if (selectedPresetTag == tag) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedPresetTag == tag) Color.Black else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}



