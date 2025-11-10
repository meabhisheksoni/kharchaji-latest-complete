package com.example.monday

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import android.util.Log
import java.time.ZoneId

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_table ORDER BY id DESC")
    fun getTodoItems(): Flow<List<TodoItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(todoItem: TodoItem)

    /**
     * Inserts a TodoItem and returns its ID immediately
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAndGetId(todoItem: TodoItem): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(todoItems: List<TodoItem>)

    @Update
    suspend fun update(todoItem: TodoItem)

    @Delete
    suspend fun delete(todoItem: TodoItem)

    @Query("DELETE FROM todo_table")
    suspend fun deleteAll() {
        Log.d("TodoDao", "deleteAll() called")
    }

    @Query("DELETE FROM todo_table WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Int)

    @Query("DELETE FROM todo_table WHERE id IN (:itemIds)")
    suspend fun deleteItemsByIds(itemIds: List<Int>)

    @Query("DELETE FROM todo_table WHERE timestamp >= :startOfDayMillis AND timestamp < :endOfNextDayMillis")
    suspend fun deleteTodoItemsByTimestampRange(startOfDayMillis: Long, endOfNextDayMillis: Long) {
        Log.d("TodoDao", "deleteTodoItemsByTimestampRange called with start: $startOfDayMillis, end: $endOfNextDayMillis")
    }

    @Transaction
    suspend fun clearAndInsertTodoItems(items: List<TodoItem>) {
        Log.d("TodoDao", "clearAndInsertTodoItems called with ${items.size} items. Will call deleteAll().")
        deleteAll()
        insertItems(items)
    }

    @Transaction
    suspend fun replaceTodoItemsForDate(items: List<TodoItem>, targetDate: LocalDate) {
        val startOfDayMillis = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfNextDayMillis = targetDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        Log.d("TodoDao", "replaceTodoItemsForDate called for date: $targetDate with ${items.size} items. Will call deleteTodoItemsByTimestampRange.")
        deleteTodoItemsByTimestampRange(startOfDayMillis, endOfNextDayMillis)
        insertItems(items)
        Log.d("TodoDao", "replaceTodoItemsForDate finished for date: $targetDate")
    }

    /**
     * Perform a clear and set operation in a single transaction for instant UI update
     */
    @Transaction
    suspend fun clearAndSetItemsForDate(items: List<TodoItem>, targetDate: LocalDate) {
        val startOfDayMillis = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfNextDayMillis = targetDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        Log.d("TodoDao", "clearAndSetItemsForDate: Deleting items for date range and inserting ${items.size} new items")
        deleteTodoItemsByTimestampRange(startOfDayMillis, endOfNextDayMillis)
        insertItems(items)
    }

    // CalculationRecord methods
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculationRecord(record: CalculationRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculationRecords(records: List<CalculationRecord>)

    @Query("SELECT * FROM calculation_records ORDER BY isMasterSave DESC, timestamp DESC")
    fun getAllCalculationRecords(): kotlinx.coroutines.flow.Flow<List<CalculationRecord>>

    @Query("SELECT * FROM calculation_records WHERE id = :id")
    fun getCalculationRecordById(id: Int): kotlinx.coroutines.flow.Flow<CalculationRecord?>

    @Delete
    suspend fun deleteCalculationRecord(record: CalculationRecord)

    @Query("DELETE FROM calculation_records WHERE id = :recordId")
    suspend fun deleteCalculationRecordById(recordId: Int)

    @Query("DELETE FROM calculation_records")
    suspend fun deleteAllCalculationRecords()

    @Query("SELECT * FROM calculation_records WHERE recordDate >= :startOfDayMillis AND recordDate <= :endOfDayMillis ORDER BY isMasterSave DESC, timestamp DESC")
    fun getCalculationRecordsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<CalculationRecord>>

    @Query("SELECT * FROM calculation_records WHERE recordDate >= :startOfDayMillis AND recordDate <= :endOfDayMillis AND isMasterSave = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getMasterSaveRecordForDate(startOfDayMillis: Long, endOfDayMillis: Long): Flow<CalculationRecord?>

    /**
     * Get only master records for a date range (for calendar use)
     */
    @Query("SELECT * FROM calculation_records WHERE recordDate >= :startMillis AND recordDate <= :endMillis AND isMasterSave = 1 ORDER BY recordDate ASC, timestamp DESC")
    suspend fun getMasterRecordsForDateRange(startMillis: Long, endMillis: Long): List<CalculationRecord>

    @Query("SELECT * FROM todo_table ORDER BY timestamp DESC, isDone ASC")
    suspend fun getAllItems(): List<TodoItem>

    @Query("SELECT * FROM calculation_records ORDER BY isMasterSave DESC, timestamp DESC")
    suspend fun getAllCalculationRecordsForExport(): List<CalculationRecord>

    @Transaction
    suspend fun deleteItems(items: List<TodoItem>) {
        if (items.isEmpty()) return
        
        // More efficient - performs a single DELETE operation
        val itemIds = items.map { it.id }
        deleteItemsByIds(itemIds)
    }

    @Transaction
    suspend fun clearAndInsertAllData(todoItems: List<TodoItem>, calculationRecords: List<CalculationRecord>) {
        Log.d("TodoDao", "clearAndInsertAllData: Deleting all existing data and inserting ${todoItems.size} items and ${calculationRecords.size} records")
        deleteAll() // Deletes all TodoItems
        deleteAllCalculationRecords()
        insertItems(todoItems)
        insertCalculationRecords(calculationRecords)
    }

    /**
     * Get all items for a specific date range directly from database.
     * Used by the batch save process to avoid issues with StateFlow collection.
     */
    @Query("SELECT * FROM todo_table WHERE timestamp >= :startOfDayMillis AND timestamp <= :endOfDayMillis ORDER BY id ASC")
    suspend fun getAllItemsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): List<TodoItem>

    /**
     * Get all calculation records for a specific date range directly from database.
     * Used by the batch save process to check for existing records.
     */
    @Query("SELECT * FROM calculation_records WHERE recordDate >= :startOfDayMillis AND recordDate <= :endOfDayMillis ORDER BY isMasterSave DESC, timestamp DESC")
    suspend fun getAllCalculationRecordsForDateRangeDirect(startOfDayMillis: Long, endOfDayMillis: Long): List<CalculationRecord>

    /**
     * Get master save records for a specific date range
     */
    @Query("SELECT * FROM calculation_records WHERE recordDate >= :startOfDayMillis AND recordDate <= :endOfDayMillis AND isMasterSave = 1 ORDER BY timestamp DESC")
    suspend fun getMasterSaveRecordsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): List<CalculationRecord>

    /**
     * Update an existing calculation record
     */
    @Update
    suspend fun updateCalculationRecord(record: CalculationRecord)

    @Query("SELECT * FROM todo_table WHERE id = :itemId")
    suspend fun getItemById(itemId: Int): TodoItem?

    @Update
    suspend fun updateItems(items: List<TodoItem>)

    /**
     * Bulk update calculation records in a single transaction
     */
    @Transaction
    @Update
    suspend fun updateCalculationRecords(records: List<CalculationRecord>)

    /**
     * Get items by category for efficient filtering
     */
    @Query("SELECT * FROM todo_table WHERE categories LIKE '%' || :category || '%' ORDER BY timestamp DESC")
    suspend fun getItemsByCategory(category: String): List<TodoItem>
}