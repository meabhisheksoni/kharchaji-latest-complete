package com.example.monday

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class TodoRepository(private val todoDao: TodoDao) {
    fun getTodoItems(): Flow<List<TodoItem>> = todoDao.getTodoItems()

    suspend fun insert(todoItem: TodoItem) {
        todoDao.insert(todoItem)
    }

    /**
     * Inserts a TodoItem and returns its ID immediately
     */
    suspend fun insertAndGetId(todoItem: TodoItem): Int {
        return todoDao.insertAndGetId(todoItem).toInt()
    }

    suspend fun update(todoItem: TodoItem) {
        todoDao.update(todoItem)
    }

    suspend fun delete(todoItem: TodoItem) {
        todoDao.delete(todoItem)
    }

    suspend fun deleteItemsByIds(itemIds: List<Int>) {
        todoDao.deleteItemsByIds(itemIds)
    }

    suspend fun deleteAll() {
        todoDao.deleteAll()
    }

    suspend fun deleteItemById(itemId: Int) {
        todoDao.deleteItemById(itemId)
    }

    /**
     * Delete all items within a specific date range
     */
    suspend fun deleteItemsByDateRange(startOfDayMillis: Long, endOfDayMillis: Long) {
        todoDao.deleteTodoItemsByTimestampRange(startOfDayMillis, endOfDayMillis)
    }

    suspend fun clearAndLoadTodoItems(items: List<TodoItem>) {
        todoDao.clearAndInsertTodoItems(items)
    }

    suspend fun replaceTodoItemsForDate(items: List<TodoItem>, targetDate: LocalDate) {
        todoDao.replaceTodoItemsForDate(items, targetDate)
    }

    /**
     * Clear all items for a date and set new ones in a single atomic transaction
     */
    suspend fun clearAndSetItemsForDate(items: List<TodoItem>, targetDate: LocalDate) {
        todoDao.clearAndSetItemsForDate(items, targetDate)
    }

    // CalculationRecord methods
    val allCalculationRecords: Flow<List<CalculationRecord>> = todoDao.getAllCalculationRecords()

    suspend fun insertCalculationRecord(record: CalculationRecord) {
        todoDao.insertCalculationRecord(record)
    }

    fun getCalculationRecordById(id: Int): kotlinx.coroutines.flow.Flow<CalculationRecord?> {
        return todoDao.getCalculationRecordById(id)
    }

    suspend fun deleteCalculationRecord(record: CalculationRecord) {
        todoDao.deleteCalculationRecord(record)
    }

    suspend fun deleteCalculationRecordById(recordId: Int) {
        todoDao.deleteCalculationRecordById(recordId)
    }

    suspend fun deleteAllCalculationRecords() {
        todoDao.deleteAllCalculationRecords()
    }

    fun getCalculationRecordsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<CalculationRecord>> {
        return todoDao.getCalculationRecordsForDateRange(startOfDayMillis, endOfDayMillis)
    }

    fun getMasterSaveRecordForDate(startOfDayMillis: Long, endOfDayMillis: Long): Flow<CalculationRecord?> {
        return todoDao.getMasterSaveRecordForDate(startOfDayMillis, endOfDayMillis)
    }

    // Function to get all items for export
    suspend fun getAllItems(): List<TodoItem> {
        return todoDao.getAllItems()
    }

    // Batch delete items
    suspend fun deleteItems(items: List<TodoItem>) {
        todoDao.deleteItems(items)
    }

    suspend fun getAllCalculationRecordsForExport(): List<CalculationRecord> {
        return todoDao.getAllCalculationRecordsForExport()
    }

    suspend fun clearAndInsertAllData(todoItems: List<TodoItem>, calculationRecords: List<CalculationRecord>) {
        todoDao.clearAndInsertAllData(todoItems, calculationRecords)
    }

    /**
     * Get all items for a specific date range directly from database.
     * Used by the batch save process to avoid issues with StateFlow collection.
     */
    suspend fun getAllItemsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): List<TodoItem> {
        return todoDao.getAllItemsForDateRange(startOfDayMillis, endOfDayMillis)
    }

    /**
     * Get all calculation records for a specific date range directly from database.
     * Used by the batch save process to check for existing records.
     */
    suspend fun getAllCalculationRecordsForDateRangeDirect(startOfDayMillis: Long, endOfDayMillis: Long): List<CalculationRecord> {
        return todoDao.getAllCalculationRecordsForDateRangeDirect(startOfDayMillis, endOfDayMillis)
    }

    /**
     * Get master save records for a specific date range
     */
    suspend fun getMasterSaveRecordsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): List<CalculationRecord> {
        return todoDao.getMasterSaveRecordsForDateRange(startOfDayMillis, endOfDayMillis)
    }

    /**
     * Get only master records for a date range (for calendar use)
     */
    suspend fun getMasterRecordsForDateRange(startMillis: Long, endMillis: Long): List<CalculationRecord> {
        return todoDao.getMasterRecordsForDateRange(startMillis, endMillis)
    }

    /**
     * Update an existing calculation record
     */
    suspend fun updateCalculationRecord(record: CalculationRecord) {
        todoDao.updateCalculationRecord(record)
    }

    /**
     * Get a TodoItem by its ID directly from the database
     */
    suspend fun getItemById(itemId: Int): TodoItem? {
        return todoDao.getItemById(itemId)
    }
}