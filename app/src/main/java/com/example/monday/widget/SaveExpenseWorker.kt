package com.example.monday.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.monday.AppDatabase
import com.example.monday.TodoItem
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
            
            Log.d(TAG, "Starting save: item=$itemName, price=$priceStr, qty=$qtyStr, unit=$unit")
            
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
                        "items" -> {
                            if (qty == 1.0) "1 item" else "${qty.toInt()} items"
                        }
                        else -> "$qty$unit"
                    }
                    "$itemName ($quantityDisplay) - ₹$formattedPrice"
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
            
            Log.d(TAG, "Creating TodoItem: $itemText")
            
            // Insert into database
            val database = AppDatabase.getDatabase(applicationContext)
            val insertedId = database.todoDao().insertAndGetId(todoItem)
            
            Log.d(TAG, "Successfully saved expense with ID: $insertedId")
            Log.d(TAG, "Item text: $itemText")
            Log.d(TAG, "Timestamp: $timestamp (${currentDate})")
            
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving expense", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            Result.retry()
        }
    }
}
