package com.example.monday.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import android.util.Log

class ExpenseGlanceWidget : GlanceAppWidget() {

    companion object {
        val ITEM_NAME_KEY = stringPreferencesKey("widget_item")
        val PRICE_KEY = stringPreferencesKey("widget_price")
        val QTY_KEY = stringPreferencesKey("widget_qty")
        val UNIT_KEY = stringPreferencesKey("widget_unit")
        
        private const val TAG = "ExpenseGlanceWidget"
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            ExpenseWidgetContent()
        }
    }

    @Composable
    private fun ExpenseWidgetContent() {
        val prefs = currentState<Preferences>()
        val itemName = prefs[ITEM_NAME_KEY] ?: ""
        val price = prefs[PRICE_KEY] ?: "0"
        val qty = prefs[QTY_KEY] ?: ""
        val unit = prefs[UNIT_KEY] ?: ""

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                .background(androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.White)),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // Item Name Section
            Text(
                text = "Item name",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ColorProvider(androidx.compose.ui.graphics.Color.Gray)
                ),
                modifier = GlanceModifier.padding(bottom = 4.dp)
            )
            
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFFF5F5F5)))
                    .padding(12.dp)
                    .clickable(actionRunCallback<EditItemNameAction>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🛒",
                    style = TextStyle(fontSize = 20.sp),
                    modifier = GlanceModifier.padding(end = 8.dp)
                )
                Text(
                    text = if (itemName.isEmpty()) "Tap to enter item" else itemName,
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = ColorProvider(if (itemName.isEmpty()) androidx.compose.ui.graphics.Color.Gray else androidx.compose.ui.graphics.Color.Black)
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
            }

            // Price and Submit Button Row
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                // Price Field
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Price (₹)",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = ColorProvider(androidx.compose.ui.graphics.Color.Gray)
                        ),
                        modifier = GlanceModifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFFF5F5F5)))
                            .padding(12.dp)
                            .clickable(actionRunCallback<EditPriceAction>()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "₹",
                            style = TextStyle(fontSize = 16.sp),
                            modifier = GlanceModifier.padding(end = 8.dp)
                        )
                        Text(
                            text = price,
                            style = TextStyle(
                                fontSize = 16.sp,
                                color = ColorProvider(androidx.compose.ui.graphics.Color.Black)
                            )
                        )
                    }
                }

                // Submit Button
                Box(
                    modifier = GlanceModifier
                        .size(56.dp)
                        .background(androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFF4CE0B3)))
                        .clickable(actionRunCallback<SubmitExpenseAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "→",
                        style = TextStyle(
                            fontSize = 24.sp,
                            color = ColorProvider(androidx.compose.ui.graphics.Color.Black),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Select Quantity Label
            Text(
                text = "Select Quantity",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.padding(bottom = 8.dp)
            )

            // Predefined Quantity Buttons
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("250", "500", "1000", "1500", "2000").forEachIndexed { index, qtyValue ->
                    val label = when(qtyValue) {
                        "250" -> "250g"
                        "500" -> "500g"
                        "1000" -> "1kg"
                        "1500" -> "1.5kg"
                        "2000" -> "2kg"
                        else -> qtyValue
                    }
                    QuantityButton(label, qtyValue, qty == qtyValue && unit == "g")
                    if (index < 4) Spacer(modifier = GlanceModifier.width(4.dp))
                }
            }

            // Custom Quantity Input Row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                // Custom Quantity Field
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Quantity",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = ColorProvider(androidx.compose.ui.graphics.Color.Gray)
                        ),
                        modifier = GlanceModifier.padding(bottom = 4.dp)
                    )
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFFF5F5F5)))
                            .padding(12.dp)
                            .clickable(actionRunCallback<EditCustomQuantityAction>())
                    ) {
                        Text(
                            text = if (qty.isEmpty() || unit.isEmpty()) "Optional" else "$qty$unit",
                            style = TextStyle(
                                fontSize = 16.sp,
                                color = ColorProvider(
                                    if (qty.isEmpty() || unit.isEmpty()) 
                                        androidx.compose.ui.graphics.Color.Gray 
                                    else 
                                        androidx.compose.ui.graphics.Color.Black
                                )
                            )
                        )
                    }
                }

                // Unit Buttons
                listOf("kg", "g", "items").forEachIndexed { index, unitValue ->
                    UnitButton(unitValue, unit == unitValue)
                    if (index < 2) Spacer(modifier = GlanceModifier.width(4.dp))
                }
            }
        }
    }

    @Composable
    private fun QuantityButton(label: String, value: String, isSelected: Boolean) {
        Box(
            modifier = GlanceModifier
                .height(36.dp)
                .background(
                    if (isSelected) 
                        androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFF6750A4)) 
                    else 
                        androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.White)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(
                    actionRunCallback<SelectQuantityAction>(
                        actionParametersOf(QuantityParam to value)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ColorProvider(
                        if (isSelected) 
                            androidx.compose.ui.graphics.Color.White 
                        else 
                            androidx.compose.ui.graphics.Color.Black
                    )
                )
            )
        }
    }

    @Composable
    private fun UnitButton(unitValue: String, isSelected: Boolean) {
        Box(
            modifier = GlanceModifier
                .height(36.dp)
                .background(
                    if (isSelected) 
                        androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(0xFF6750A4)) 
                    else 
                        androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.White)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(
                    actionRunCallback<SelectUnitAction>(
                        actionParametersOf(UnitParam to unitValue)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = unitValue,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ColorProvider(
                        if (isSelected) 
                            androidx.compose.ui.graphics.Color.White 
                        else 
                            androidx.compose.ui.graphics.Color.Black
                    )
                )
            )
        }
    }
}

// Action Parameters
val QuantityParam = ActionParameters.Key<String>("quantity")
val UnitParam = ActionParameters.Key<String>("unit")

// Action Callbacks
class EditItemNameAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Log.d("ExpenseGlanceWidget", "EditItemNameAction triggered")
        val intent = android.content.Intent(context, WidgetInputActivity::class.java).apply {
            putExtra("field", "item_name")
            putExtra("glance_id", glanceId.toString())
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

class EditPriceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Log.d("ExpenseGlanceWidget", "EditPriceAction triggered")
        val intent = android.content.Intent(context, WidgetInputActivity::class.java).apply {
            putExtra("field", "price")
            putExtra("glance_id", glanceId.toString())
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

class EditCustomQuantityAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Log.d("ExpenseGlanceWidget", "EditCustomQuantityAction triggered")
        val intent = android.content.Intent(context, WidgetInputActivity::class.java).apply {
            putExtra("field", "custom_quantity")
            putExtra("glance_id", glanceId.toString())
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

class SelectQuantityAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val quantity = parameters[QuantityParam] ?: return
        Log.d("ExpenseGlanceWidget", "SelectQuantityAction: $quantity")
        
        // Read current state from SharedPreferences
        val sharedPrefs = context.getSharedPreferences("glance_prefs_${glanceId}", Context.MODE_PRIVATE)
        val currentQty = sharedPrefs.getString("widget_qty", "250") ?: "250"
        val currentUnit = sharedPrefs.getString("widget_unit", "g") ?: "g"
        
        // Toggle logic: if same quantity is clicked and unit is "g", deselect it completely
        val (newQty, newUnit) = if (currentQty == quantity && currentUnit == "g") {
            // Deselect - clear selection (empty values)
            "" to ""
        } else {
            // Select - set new quantity
            quantity to "g"
        }
        
        Log.d("ExpenseGlanceWidget", "Toggle: current=$currentQty$currentUnit, new=$newQty$newUnit")
        
        // Update Glance state
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[ExpenseGlanceWidget.QTY_KEY] = newQty
            prefs[ExpenseGlanceWidget.UNIT_KEY] = newUnit
        }
        
        // Also update SharedPreferences for SubmitExpenseAction
        sharedPrefs.edit().apply {
            putString("widget_qty", newQty)
            putString("widget_unit", newUnit)
            apply()
        }
        
        ExpenseGlanceWidget().update(context, glanceId)
    }
}

class SelectUnitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val unit = parameters[UnitParam] ?: return
        Log.d("ExpenseGlanceWidget", "SelectUnitAction: $unit")
        
        // Read current state from SharedPreferences
        val sharedPrefs = context.getSharedPreferences("glance_prefs_${glanceId}", Context.MODE_PRIVATE)
        val currentUnit = sharedPrefs.getString("widget_unit", "g") ?: "g"
        
        // Toggle logic: if same unit is clicked, deselect it completely
        val newUnit = if (currentUnit == unit) {
            "" // Clear selection
        } else {
            unit
        }
        
        Log.d("ExpenseGlanceWidget", "Toggle unit: current=$currentUnit, new=$newUnit")
        
        // Update Glance state
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[ExpenseGlanceWidget.UNIT_KEY] = newUnit
        }
        
        // Also update SharedPreferences for SubmitExpenseAction
        sharedPrefs.edit().apply {
            putString("widget_unit", newUnit)
            apply()
        }
        
        ExpenseGlanceWidget().update(context, glanceId)
    }
}

class SubmitExpenseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Log.d("ExpenseGlanceWidget", "SubmitExpenseAction triggered")
        
        // Read current widget state from SharedPreferences
        val sharedPrefs = context.getSharedPreferences("glance_prefs_${glanceId}", Context.MODE_PRIVATE)
        val itemName = sharedPrefs.getString("widget_item", "") ?: ""
        val price = sharedPrefs.getString("widget_price", "0") ?: "0"
        val qty = sharedPrefs.getString("widget_qty", "") ?: ""
        val unit = sharedPrefs.getString("widget_unit", "") ?: ""
        
        Log.d("ExpenseGlanceWidget", "Submit: item=$itemName, price=$price, qty=$qty, unit=$unit")
        
        // Validate
        if (price.toDoubleOrNull() == null || price.toDouble() <= 0) {
            android.widget.Toast.makeText(context, "Please enter a valid price", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        // Schedule WorkManager task
        val workRequest = OneTimeWorkRequestBuilder<SaveExpenseWorker>()
            .setInputData(
                workDataOf(
                    "item_name" to (if (itemName.isBlank()) "Unnamed" else itemName),
                    "price" to price,
                    "qty" to qty,
                    "unit" to unit,
                    "glance_id" to glanceId.toString()
                )
            )
            .build()
        
        WorkManager.getInstance(context).enqueue(workRequest)
        
        // Reset widget state in Glance preferences
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
            apply()
        }
        
        ExpenseGlanceWidget().update(context, glanceId)
        
        android.widget.Toast.makeText(context, "Saving expense...", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// Widget Receiver
class ExpenseGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpenseGlanceWidget()
}
