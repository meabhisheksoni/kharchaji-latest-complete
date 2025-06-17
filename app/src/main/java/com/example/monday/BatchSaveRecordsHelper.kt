package com.example.monday

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Helper class to batch save all TodoItems as CalculationRecords for a date range.
 */
class BatchSaveRecordsHelper(
    private val context: Context,
    private val todoViewModel: TodoViewModel
) {
    /**
     * Saves all expenses for each date in the specified range as CalculationRecords.
     * This is useful when you have entries for many dates but haven't saved them as records.
     */
    fun batchSaveAllRecordsInRange(
        startDate: LocalDate = LocalDate.of(2024, 3, 23),
        endDate: LocalDate = LocalDate.now(),
        onProgress: (current: Int, total: Int, date: LocalDate) -> Unit = { _, _, _ -> },
        onComplete: (totalSaved: Int) -> Unit = { _ -> }
    ) {
        todoViewModel.viewModelScope.launch(Dispatchers.IO) {
            var currentDate = startDate
            var totalDays = 0
            var totalRecordsSaved = 0
            
            // Calculate total days
            while (!currentDate.isAfter(endDate)) {
                totalDays++
                currentDate = currentDate.plusDays(1)
            }
            
            currentDate = startDate
            var currentDayCount = 0
            
            // Process each date
            while (!currentDate.isAfter(endDate)) {
                currentDayCount++
                
                withContext(Dispatchers.Main) {
                    onProgress(currentDayCount, totalDays, currentDate)
                }
                
                val savedRecord = processAndSaveRecordForDate(currentDate)
                if (savedRecord) totalRecordsSaved++
                
                currentDate = currentDate.plusDays(1)
            }
            
            withContext(Dispatchers.Main) {
                onComplete(totalRecordsSaved)
                Toast.makeText(
                    context,
                    "Master batch save completed. Saved $totalRecordsSaved master records across $totalDays days.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Process and save a record for a specific date if it has expenses.
     * Returns true if a record was saved, false otherwise.
     */
    private suspend fun processAndSaveRecordForDate(date: LocalDate): Boolean {
        try {
            // Direct database query to get all expenses for the date
            val startOfDayMillis = date.toEpochMilli()
            val endOfDayMillis = date.plusDays(1).toEpochMilli() - 1
            
            // Log date information for debugging
            Log.d("BatchSaveHelper", "Processing date: $date (${date.toEpochMilli()})")
            
            // Get all items directly from the database for this date range
            val expenses = todoViewModel.getAllItemsForDateRange(startOfDayMillis, endOfDayMillis)
            
            if (expenses.isEmpty()) {
                Log.d("BatchSaveHelper", "No expenses found for date: $date")
                return false
            }
            
            Log.d("BatchSaveHelper", "Found ${expenses.size} expenses for date: $date")
            
            // Instead of creating and inserting a regular record, use saveToMasterRecord
            // which will create a master record or update an existing one
            Log.d("BatchSaveHelper", "Creating master save record for date: $date")
            val saveResult = todoViewModel.saveToMasterRecord(date, expenses)
            
            // The saveToMasterRecord function returns a pair where the second boolean indicates if a master record was created/updated
            val masterRecordCreatedOrUpdated = saveResult.second
            
            Log.d("BatchSaveHelper", "Master record ${if (masterRecordCreatedOrUpdated) "created/updated" else "not created"} for date: $date")
            
            return masterRecordCreatedOrUpdated
        } catch (e: Exception) {
            Log.e("BatchSaveHelper", "Error processing date: $date", e)
            return false
        }
    }
} 