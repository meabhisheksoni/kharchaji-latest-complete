package com.example.monday

import com.example.monday.core.utils.*
import com.example.monday.data.models.TodoItem
import com.example.monday.data.models.CalculationRecord
import com.example.monday.data.models.RecordItem
import com.example.monday.data.local.TodoDao
import com.example.monday.data.local.AppDatabase

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object MasterSaveHelper {
    private const val TAG = "AutoMasterSave"

    /**
     * Appends a newly created TodoItem to today's Master CalculationRecord.
     * If no master record exists for today, creates a new one.
     */
    suspend fun appendToMasterAsync(context: Context, item: TodoItem) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val dao = db.todoDao()

            val itemDate = Instant.ofEpochMilli(item.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            val startOfDayMillis = itemDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDayMillis = itemDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

            // Check if there's already a master record for this date
            val masterRecords = dao.getMasterSaveRecordsForDateRange(startOfDayMillis, endOfDayMillis)

            val baseRecordItem = todoItemToRecordItem(item)
            val recordItem = baseRecordItem.copy(
                isChecked = true, // Auto-checked in master sum
                sourceItemId = item.id
            )

            if (masterRecords.isNotEmpty()) {
                // Update existing
                val existingMaster = masterRecords.first()
                val updatedItems = existingMaster.items.toMutableList()
                updatedItems.add(recordItem)

                val newTotalSum = updatedItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
                val newCheckedItemsCount = updatedItems.count { it.isChecked }
                val newCheckedItemsSum = updatedItems.filter { it.isChecked }.sumOf { it.price.toDoubleOrNull() ?: 0.0 }

                val updatedMaster = existingMaster.copy(
                    items = updatedItems,
                    totalSum = newTotalSum,
                    checkedItemsCount = newCheckedItemsCount,
                    checkedItemsSum = newCheckedItemsSum,
                    timestamp = System.currentTimeMillis()
                )

                dao.updateCalculationRecord(updatedMaster)

            } else {
                // Create new
                val priceDouble = recordItem.price.toDoubleOrNull() ?: 0.0
                val newMaster = CalculationRecord(
                    timestamp = System.currentTimeMillis(),
                    recordDate = startOfDayMillis,
                    items = listOf(recordItem),
                    totalSum = priceDouble,
                    checkedItemsCount = 1,
                    checkedItemsSum = priceDouble,
                    isMasterSave = true
                )

                dao.insertCalculationRecord(newMaster)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-save item to Master Record: ${e.message}", e)
        }
    }
}

