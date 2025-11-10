package com.example.monday.domain

import com.example.monday.CalculationRecord
import com.example.monday.TodoItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface for Todo operations
 * This allows for better testability through dependency injection
 */
interface ITodoRepository {
    // TodoItem operations
    fun getTodoItems(): Flow<List<TodoItem>>
    suspend fun insert(todoItem: TodoItem)
    suspend fun insertAndGetId(todoItem: TodoItem): Int
    suspend fun insertItems(todoItems: List<TodoItem>)
    suspend fun update(todoItem: TodoItem)
    suspend fun delete(todoItem: TodoItem)
    suspend fun deleteItemsByIds(itemIds: List<Int>)
    suspend fun deleteAll()
    suspend fun deleteItemById(itemId: Int)
    suspend fun deleteItemsByDateRange(startOfDayMillis: Long, endOfDayMillis: Long)
    suspend fun clearAndLoadTodoItems(items: List<TodoItem>)
    suspend fun replaceTodoItemsForDate(items: List<TodoItem>, targetDate: LocalDate)
    suspend fun clearAndSetItemsForDate(items: List<TodoItem>, targetDate: LocalDate)
    
    // CalculationRecord operations
    val allCalculationRecords: Flow<List<CalculationRecord>>
    suspend fun insertCalculationRecord(record: CalculationRecord)
    suspend fun insertCalculationRecords(records: List<CalculationRecord>)
    fun getCalculationRecordById(id: Int): Flow<CalculationRecord?>
    suspend fun deleteCalculationRecord(record: CalculationRecord)
    suspend fun deleteCalculationRecordById(recordId: Int)
    suspend fun deleteAllCalculationRecords()
    fun getCalculationRecordsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<CalculationRecord>>
    fun getMasterSaveRecordForDate(startOfDayMillis: Long, endOfDayMillis: Long): Flow<CalculationRecord?>
    
    // Additional operations
    suspend fun getAllItems(): List<TodoItem>
    suspend fun deleteItems(items: List<TodoItem>)
    suspend fun getAllCalculationRecordsForExport(): List<CalculationRecord>
    suspend fun clearAndInsertAllData(todoItems: List<TodoItem>, calculationRecords: List<CalculationRecord>)
    suspend fun getAllItemsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): List<TodoItem>
    suspend fun getAllCalculationRecordsForDateRangeDirect(startOfDayMillis: Long, endOfDayMillis: Long): List<CalculationRecord>
    suspend fun getMasterSaveRecordsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): List<CalculationRecord>
    suspend fun getMasterRecordsForDateRange(startMillis: Long, endMillis: Long): List<CalculationRecord>
    suspend fun updateCalculationRecord(record: CalculationRecord)
    suspend fun getItemById(itemId: Int): TodoItem?
    suspend fun updateItems(items: List<TodoItem>)
    suspend fun getItemsByCategory(category: String): List<TodoItem>
    suspend fun updateCalculationRecords(records: List<CalculationRecord>)
    suspend fun getAllItemsGroupedByDate(): Map<LocalDate, List<TodoItem>>
} 