package com.example.monday.widget

import com.example.monday.core.utils.*
import com.example.monday.data.models.TodoItem
import com.example.monday.data.local.TodoDao
import com.example.monday.data.local.AppDatabase

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.monday.MasterSaveHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class SaveExpenseWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SaveExpenseWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val itemName = inputData.getString("item_name") ?: "Unnamed"
            val priceStr = inputData.getString("price") ?: "0"
            val qtyStr = inputData.getString("qty") ?: ""
            val unit = inputData.getString("unit") ?: ""
            
            // Parse and validate
            val price = priceStr.toDoubleOrNull() ?: 0.0
            if (price <= 0) {
                Log.e(TAG, "Invalid price: $priceStr")
                return@withContext Result.failure()
            }
            
            // Format price
            val formattedPrice = String.format("%.2f", price)
            
            // Format quantity with unit (optional)
            val itemText = if (qtyStr.isNotBlank() && unit.isNotBlank()) {
                val qty = qtyStr.toDoubleOrNull() ?: 0.0
                if (qty > 0) {
                    val quantityDisplay = when (unit) {
                        "kg" -> {
                            if (qty >= 1) "${qty.toInt()}kg" else "${(qty * 1000).toInt()}g"
                        }
                        "g" -> {
                            if (qty >= 1000) "${(qty / 1000)}kg" else "${qty.toInt()}g"
                        }
                        "ml" -> {
                            if (qty >= 1000) "${(qty / 1000)}L" else "${qty.toInt()}ml"
                        }
                        "L" -> {
                            if (qty >= 1) "${qty.toInt()}L" else "${(qty * 1000).toInt()}ml"
                        }
                        "items" -> {
                            if (qty == 1.0) "1 item" else "${qty.toInt()} items"
                        }
                        else -> "$qty$unit"
                    }
                    "$itemName ($quantityDisplay) - ₹$formattedPrice"
                } else {
                    "$itemName - ₹$formattedPrice"
                }
            } else if (qtyStr.isNotBlank()) {
                // Qty without unit â€” save as items/count
                val qty = qtyStr.toDoubleOrNull() ?: 0.0
                if (qty > 0) {
                    val countDisplay = if (qty == 1.0) "1 item" else "${qty.toInt()} items"
                    "$itemName ($countDisplay) - ₹$formattedPrice"
                } else {
                    "$itemName - ₹$formattedPrice"
                }
            } else {
                "$itemName - ₹$formattedPrice"
            }
            
            // Get current date timestamp
            val currentDate = LocalDate.now()
            val timestamp = currentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            // Create TodoItem
            val todoItem = TodoItem(
                text = itemText,
                timestamp = timestamp,
                isDone = false
            )
            
            // Insert into database
            val database = AppDatabase.getDatabase(applicationContext)
            val insertedId = database.todoDao().insertAndGetId(todoItem)
            Log.d(TAG, "Timestamp: $timestamp (${currentDate})")
            
            // â”€â”€ Auto Master Save Integration â”€â”€
            val prefManager = com.example.monday.managers.PreferenceManager.from(applicationContext)
            val autoMasterSave = prefManager.getPaymentMonitorSetting("auto_master_save") ?: false
            if (autoMasterSave) {
                // Background worker, so we can just call it synchronously here since we are in doWork()
                // Wait, it's a completely suspendable function. Let's make sure doWork runs context properly.
                // We're inside CoroutineWorker doWork(), which is suspend.
                val itemWithId = todoItem.copy(id = insertedId.toInt())
                MasterSaveHelper.appendToMasterAsync(applicationContext, itemWithId)
            }
            // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving expense", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            Result.retry()
        }
    }
}
