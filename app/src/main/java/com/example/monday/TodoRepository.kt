package com.example.monday

import com.example.monday.domain.ITodoRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

class TodoRepository(private val todoDao: TodoDao) : ITodoRepository {
    override fun getTodoItems(): Flow<List<TodoItem>> = todoDao.getTodoItems()

    override suspend fun insert(todoItem: TodoItem) {
        todoDao.insert(todoItem)
    }

    /**
     * Inserts a TodoItem and returns its ID immediately
     */
    override suspend fun insertAndGetId(todoItem: TodoItem): Int {
        return todoDao.insertAndGetId(todoItem).toInt()
    }

    override suspend fun insertItems(todoItems: List<TodoItem>) {
        todoDao.insertItems(todoItems)
    }

    override suspend fun update(todoItem: TodoItem) {
        todoDao.update(todoItem)
    }

    override suspend fun delete(todoItem: TodoItem) {
        todoDao.delete(todoItem)
    }

    override suspend fun deleteItemsByIds(itemIds: List<Int>) {
        todoDao.deleteItemsByIds(itemIds)
    }

    override suspend fun deleteAll() {
        todoDao.deleteAll()
    }

    override suspend fun deleteItemById(itemId: Int) {
        todoDao.deleteItemById(itemId)
    }

    /**
     * Delete all items within a specific date range
     */
    override suspend fun deleteItemsByDateRange(startOfDayMillis: Long, endOfDayMillis: Long) {
        todoDao.deleteTodoItemsByTimestampRange(startOfDayMillis, endOfDayMillis)
    }

    override suspend fun clearAndLoadTodoItems(items: List<TodoItem>) {
        todoDao.clearAndInsertTodoItems(items)
    }

    override suspend fun replaceTodoItemsForDate(items: List<TodoItem>, targetDate: LocalDate) {
        todoDao.replaceTodoItemsForDate(items, targetDate)
    }

    /**
     * Clear all items for a date and set new ones in a single atomic transaction
     */
    override suspend fun clearAndSetItemsForDate(items: List<TodoItem>, targetDate: LocalDate) {
        todoDao.clearAndSetItemsForDate(items, targetDate)
    }

    // CalculationRecord methods
    override val allCalculationRecords: Flow<List<CalculationRecord>> = todoDao.getAllCalculationRecords()

    override suspend fun insertCalculationRecord(record: CalculationRecord) {
        todoDao.insertCalculationRecord(record)
    }

    override suspend fun insertCalculationRecords(records: List<CalculationRecord>) {
        todoDao.insertCalculationRecords(records)
    }

    override fun getCalculationRecordById(id: Int): kotlinx.coroutines.flow.Flow<CalculationRecord?> {
        return todoDao.getCalculationRecordById(id)
    }

    override suspend fun deleteCalculationRecord(record: CalculationRecord) {
        todoDao.deleteCalculationRecord(record)
    }

    override suspend fun deleteCalculationRecordById(recordId: Int) {
        todoDao.deleteCalculationRecordById(recordId)
    }

    override suspend fun deleteAllCalculationRecords() {
        todoDao.deleteAllCalculationRecords()
    }

    override fun getCalculationRecordsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<CalculationRecord>> {
        return todoDao.getCalculationRecordsForDateRange(startOfDayMillis, endOfDayMillis)
    }

    override fun getMasterSaveRecordForDate(startOfDayMillis: Long, endOfDayMillis: Long): Flow<CalculationRecord?> {
        return todoDao.getMasterSaveRecordForDate(startOfDayMillis, endOfDayMillis)
    }

    // Function to get all items for export
    override suspend fun getAllItems(): List<TodoItem> {
        return todoDao.getAllItems()
    }

    // Batch delete items
    override suspend fun deleteItems(items: List<TodoItem>) {
        todoDao.deleteItems(items)
    }

    override suspend fun getAllCalculationRecordsForExport(): List<CalculationRecord> {
        return todoDao.getAllCalculationRecordsForExport()
    }

    override suspend fun clearAndInsertAllData(todoItems: List<TodoItem>, calculationRecords: List<CalculationRecord>) {
        todoDao.clearAndInsertAllData(todoItems, calculationRecords)
    }

    /**
     * Get all items for a specific date range directly from database.
     * Used by the batch save process to avoid issues with StateFlow collection.
     */
    override suspend fun getAllItemsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): List<TodoItem> {
        return todoDao.getAllItemsForDateRange(startOfDayMillis, endOfDayMillis)
    }

    /**
     * Get all calculation records for a specific date range directly from database.
     * Used by the batch save process to check for existing records.
     */
    override suspend fun getAllCalculationRecordsForDateRangeDirect(startOfDayMillis: Long, endOfDayMillis: Long): List<CalculationRecord> {
        return todoDao.getAllCalculationRecordsForDateRangeDirect(startOfDayMillis, endOfDayMillis)
    }

    /**
     * Get master save records for a specific date range
     */
    override suspend fun getMasterSaveRecordsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): List<CalculationRecord> {
        return todoDao.getMasterSaveRecordsForDateRange(startOfDayMillis, endOfDayMillis)
    }

    /**
     * Get only master records for a date range (for calendar use)
     */
    override suspend fun getMasterRecordsForDateRange(startMillis: Long, endMillis: Long): List<CalculationRecord> {
        return todoDao.getMasterRecordsForDateRange(startMillis, endMillis)
    }

    /**
     * Update an existing calculation record
     */
    override suspend fun updateCalculationRecord(record: CalculationRecord) {
        todoDao.updateCalculationRecord(record)
    }

    /**
     * Get a TodoItem by its ID directly from the database
     */
    override suspend fun getItemById(itemId: Int): TodoItem? {
        return todoDao.getItemById(itemId)
    }

    override suspend fun updateItems(items: List<TodoItem>) {
        todoDao.updateItems(items)
    }

    /**
     * Bulk update calculation records in a single transaction
     */
    override suspend fun updateCalculationRecords(records: List<CalculationRecord>) {
        todoDao.updateCalculationRecords(records)
    }

    /**
     * Get items by category for efficient filtering
     */
    override suspend fun getItemsByCategory(category: String): List<TodoItem> {
        return todoDao.getItemsByCategory(category)
    }

    /**
     * Get all items and categorize them by date for more efficient access
     */
    override suspend fun getAllItemsGroupedByDate(): Map<LocalDate, List<TodoItem>> {
        return getAllItems().groupBy { item ->
            Instant.ofEpochMilli(item.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
    }
}