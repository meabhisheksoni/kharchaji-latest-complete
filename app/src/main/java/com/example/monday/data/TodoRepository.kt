package com.example.monday.data

import com.example.monday.data.models.TodoItem
import com.example.monday.data.models.CalculationRecord
import com.example.monday.data.local.TodoDao
import com.example.monday.domain.ITodoRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TodoRepository(private val todoDao: TodoDao) : ITodoRepository {
    override fun getTodoItems(): Flow<List<TodoItem>> = todoDao.getTodoItems()

    override suspend fun insert(todoItem: TodoItem) {
        todoDao.insert(todoItem)
    }

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

    override suspend fun deleteItemsByDateRange(startOfDayMillis: Long, endOfDayMillis: Long) {
        todoDao.deleteTodoItemsByTimestampRange(startOfDayMillis, endOfDayMillis)
    }

    override suspend fun clearAndLoadTodoItems(items: List<TodoItem>) {
        todoDao.clearAndInsertTodoItems(items)
    }

    override suspend fun replaceTodoItemsForDate(items: List<TodoItem>, targetDate: LocalDate) {
        todoDao.replaceTodoItemsForDate(items, targetDate)
    }

    override suspend fun clearAndSetItemsForDate(items: List<TodoItem>, targetDate: LocalDate) {
        todoDao.clearAndSetItemsForDate(items, targetDate)
    }

    override val allCalculationRecords: Flow<List<CalculationRecord>> = todoDao.getAllCalculationRecords()

    override suspend fun insertCalculationRecord(record: CalculationRecord) {
        todoDao.insertCalculationRecord(record)
    }

    override suspend fun insertCalculationRecords(records: List<CalculationRecord>) {
        todoDao.insertCalculationRecords(records)
    }

    override fun getCalculationRecordById(id: Int): Flow<CalculationRecord?> {
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

    override suspend fun getAllItems(): List<TodoItem> {
        return todoDao.getAllItems()
    }

    override suspend fun deleteItems(items: List<TodoItem>) {
        todoDao.deleteItems(items)
    }

    override suspend fun getAllCalculationRecordsForExport(): List<CalculationRecord> {
        return todoDao.getAllCalculationRecordsForExport()
    }

    override suspend fun clearAndInsertAllData(todoItems: List<TodoItem>, calculationRecords: List<CalculationRecord>) {
        todoDao.clearAndInsertAllData(todoItems, calculationRecords)
    }

    override suspend fun mergeAllData(todoItems: List<TodoItem>, calculationRecords: List<CalculationRecord>) {
        todoDao.mergeAllData(todoItems, calculationRecords)
    }

    override suspend fun getAllItemsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): List<TodoItem> {
        return todoDao.getAllItemsForDateRange(startOfDayMillis, endOfDayMillis)
    }

    override suspend fun getAllCalculationRecordsForDateRangeDirect(startOfDayMillis: Long, endOfDayMillis: Long): List<CalculationRecord> {
        return todoDao.getAllCalculationRecordsForDateRangeDirect(startOfDayMillis, endOfDayMillis)
    }

    override suspend fun getMasterSaveRecordsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): List<CalculationRecord> {
        return todoDao.getMasterSaveRecordsForDateRange(startOfDayMillis, endOfDayMillis)
    }

    override suspend fun getMasterRecordsForDateRange(startMillis: Long, endMillis: Long): List<CalculationRecord> {
        return todoDao.getMasterRecordsForDateRange(startMillis, endMillis)
    }

    override suspend fun updateCalculationRecord(record: CalculationRecord) {
        todoDao.updateCalculationRecord(record)
    }

    override suspend fun getItemById(itemId: Int): TodoItem? {
        return todoDao.getItemById(itemId)
    }

    override suspend fun updateItems(items: List<TodoItem>) {
        todoDao.updateItems(items)
    }

    override suspend fun updateCalculationRecords(records: List<CalculationRecord>) {
        todoDao.updateCalculationRecords(records)
    }

    override suspend fun getItemsByCategory(category: String): List<TodoItem> {
        return todoDao.getItemsByCategory(category)
    }

    override suspend fun getAllItemsGroupedByDate(): Map<LocalDate, List<TodoItem>> = withContext(Dispatchers.IO) {
        return@withContext getAllItems().groupBy { item ->
            Instant.ofEpochMilli(item.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
    }
}
