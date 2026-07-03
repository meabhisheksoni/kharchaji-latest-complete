package com.example.monday.widget
import com.example.monday.core.utils.*
import com.example.monday.data.models.TodoItem
import com.example.monday.data.local.TodoDao
import com.example.monday.data.local.AppDatabase
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class ExpenseWidgetService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return START_NOT_STICKY
    }
    
    private fun handleIntent(intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (appWidgetId == -1) return
        
        val prefs = getSharedPreferences("widget_$appWidgetId", MODE_PRIVATE)
        
        when (intent.action) {
            "SELECT_QUANTITY" -> {
                val quantity = intent.getStringExtra("quantity") ?: return
                prefs.edit().apply {
                    putString("selected_quantity", quantity)
                    putString("custom_quantity", "") // Clear custom
                    putString("selected_unit", "") // Clear unit
                    apply()
                }
            }
            
            "SELECT_UNIT" -> {
                val unit = intent.getStringExtra("unit") ?: return
                prefs.edit().apply {
                    putString("selected_unit", unit)
                    putString("selected_quantity", "") // Clear predefined
                    apply()
                }
            }
            
            "SUBMIT_EXPENSE" -> {
                submitExpense(appWidgetId, prefs)
            }
        }
    }
    
    private fun submitExpense(appWidgetId: Int, prefs: android.content.SharedPreferences) {
        serviceScope.launch {
            try {
                // Get widget data from SharedPreferences
                val itemName = prefs.getString("item_name", "") ?: ""
                val price = prefs.getString("price", "") ?: ""
                val selectedQuantity = prefs.getString("selected_quantity", "") ?: ""
                val customQuantity = prefs.getString("custom_quantity", "") ?: ""
                val selectedUnit = prefs.getString("selected_unit", "") ?: ""
                
                if (itemName.isBlank() || price.isBlank()) {
                    Log.e("WidgetService", "Item name or price is blank")
                    return@launch
                }
                
                // Format price
                val formattedPrice = String.format("%.2f", price.toDoubleOrNull() ?: 0.0)
                
                // Determine final quantity string
                val finalQuantity = when {
                    selectedQuantity.isNotBlank() -> selectedQuantity
                    customQuantity.isNotBlank() && selectedUnit.isNotBlank() -> "$customQuantity$selectedUnit"
                    else -> null
                }
                
                // Create the item text
                val itemText = if (finalQuantity != null) {
                    "$itemName ($finalQuantity) - ₹$formattedPrice"
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
                val database = AppDatabase.getDatabase(applicationContext)
                database.todoDao().insert(newItem)
                
                // Clear the form
                prefs.edit().apply {
                    putString("item_name", "")
                    putString("price", "")
                    putString("selected_quantity", "")
                    putString("custom_quantity", "")
                    putString("selected_unit", "")
                    apply()
                }
                
                // Update widget UI
                val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
                
            } catch (e: Exception) {
                Log.e("WidgetService", "Error submitting expense", e)
            }
        }
    }
}



