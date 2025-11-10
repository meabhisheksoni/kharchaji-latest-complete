package com.example.monday

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TodoRepository
    private val _todoItems = MutableStateFlow<List<TodoItem>>(emptyList())
    val todoItems: StateFlow<List<TodoItem>> = _todoItems

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _undoableDeletedItemsByDate = MutableStateFlow<Map<LocalDate, List<TodoItem>>>(emptyMap())

    private val _currentDateUndoItems = MutableStateFlow<List<TodoItem>>(emptyList())
    val undoableDeletedItems: StateFlow<List<TodoItem>> = _currentDateUndoItems

    private val imageUpdateMutex = Mutex()

    private val _primaryCategories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    val primaryCategories: StateFlow<List<ExpenseCategory>> = _primaryCategories

    private val _secondaryCategories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    val secondaryCategories: StateFlow<List<ExpenseCategory>> = _secondaryCategories

    private val _tertiaryCategories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    val tertiaryCategories: StateFlow<List<ExpenseCategory>> = _tertiaryCategories

    private val _lastCategoryAction = MutableStateFlow<CategoryAction?>(null)
    val lastCategoryAction: StateFlow<CategoryAction?> = _lastCategoryAction

    sealed class CategoryAction {
        data class Added(val category: ExpenseCategory, val type: String) : CategoryAction()
        data class Edited(val oldCategory: ExpenseCategory, val newCategory: ExpenseCategory, val type: String) : CategoryAction()
        data class Deleted(val category: ExpenseCategory, val type: String, val affectedItems: List<TodoItem>, val affectedRecords: List<CalculationRecord>) : CategoryAction()
        data class Moved(val category: ExpenseCategory, val type: String, val oldPosition: Int, val newPosition: Int) : CategoryAction()
    }

    init {
        val todoDao = AppDatabase.getDatabase(application).todoDao()
        repository = TodoRepository(todoDao)
        viewModelScope.launch {
            try {
                repository.getTodoItems().collectLatest { items ->
                    _todoItems.value = items.sortedBy { it.id }
                }
            } catch (e: Exception) {
                Log.e("KharchaJi", "Error collecting todo items", e)
                _todoItems.value = emptyList()
            }
        }
        loadAllCategories()
        viewModelScope.launch {
            try {
                kotlinx.coroutines.flow.combine(_undoableDeletedItemsByDate, _selectedDate) { itemsByDate, date ->
                    itemsByDate[date] ?: emptyList()
                }.collect { items ->
                    _currentDateUndoItems.value = items
                }
            } catch (e: Exception) {
                Log.e("KharchaJi", "Error collecting undo items", e)
                _currentDateUndoItems.value = emptyList()
            }
        }
    }

    private fun loadAllCategories() {
        _primaryCategories.value = getSavedCategories("primary") ?: com.example.monday.ui.components.DefaultCategories.primaryCategories
        _secondaryCategories.value = getSavedCategories("secondary") ?: com.example.monday.ui.components.DefaultCategories.secondaryCategories
        _tertiaryCategories.value = getSavedCategories("tertiary") ?: com.example.monday.ui.components.DefaultCategories.tertiaryCategories
    }

    fun addItem(item: TodoItem) = viewModelScope.launch(Dispatchers.IO) {
        try {
            repository.insert(item)
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error adding item: ${item.text}", e)
        }
    }

    suspend fun addItemAndGetId(item: TodoItem): Int {
        return repository.insertAndGetId(item)
    }

    fun updateItem(updatedItem: TodoItem) = viewModelScope.launch(Dispatchers.IO) {
        try {
            Log.d("CategoryDebug", "Updating item with ID: ${updatedItem.id}")
            Log.d("CategoryDebug", "Categories: ${updatedItem.categories}")
            Log.d("CategoryDebug", "Primary: ${updatedItem.hasPrimaryCategory}, Secondary: ${updatedItem.hasSecondaryCategory}, Tertiary: ${updatedItem.hasTertiaryCategory}")
            repository.update(updatedItem)
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error updating item: ${updatedItem.id}", e)
        }
    }

    fun removeItem(item: TodoItem) = viewModelScope.launch(Dispatchers.IO) {
        try {
            addDeletedItemForCurrentDate(item)
            repository.delete(item)
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error removing item: ${item.text}", e)
        }
    }

    fun deleteSelectedItemsAndEnableUndo(itemsToDelete: List<TodoItem>) = viewModelScope.launch(Dispatchers.IO) {
        try {
            if (itemsToDelete.isEmpty()) return@launch
            addDeletedItemsForCurrentDate(itemsToDelete)
            val idsToDelete = itemsToDelete.map { it.id }
            repository.deleteItemsByIds(idsToDelete)
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error deleting selected items", e)
        }
    }

    fun setAllItemsChecked(checked: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val currentItems = _todoItems.value
            val updatedItems = currentItems.map { it.copy(isDone = checked) }
            repository.updateItems(updatedItems)
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error setting all items checked: $checked", e)
        }
    }

    fun deleteAllItems() = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAll()
        _undoableDeletedItemsByDate.value = emptyMap()
    }

    fun deleteItemById(itemId: Int) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteItemById(itemId)
        val updatedMap = _undoableDeletedItemsByDate.value.mapValues { (_, items) ->
            items.filter { it.id != itemId }
        }.filter { (_, items) -> items.isNotEmpty() }
        _undoableDeletedItemsByDate.value = updatedMap
    }

    fun loadRecordItemsAsCurrentExpenses(recordItems: List<RecordItem>, targetDate: LocalDate) = viewModelScope.launch(Dispatchers.IO) {
        Log.d("CategoryFix", "==== MERGE STARTED ====")
        Log.d("CategoryFix", "Total record items: ${recordItems.size}")
        recordItems.forEachIndexed { index, item ->
            Log.d("CategoryFix", "RecordItem #$index: ${item.description}, Categories: ${item.categories}, Images: ${item.imageUris?.size ?: 0}")
        }
        val startOfDayMillis = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val existingItems = repository.getAllItemsForDateRange(startOfDayMillis, startOfDayMillis + 86400000 - 1)
        val existingItemsMap = existingItems.associateBy {
            val (name, quantity, price) = parseItemText(it.text)
            "$name|$price|${quantity ?: ""}"
        }
        Log.d("CategoryFix", "Found ${existingItems.size} existing items")
        for (recordItem in recordItems) {
            val key = "${recordItem.description}|${recordItem.price}|${recordItem.quantity ?: ""}"
            val existingItem = existingItemsMap[key]
            if (existingItem != null) {
                Log.d("CategoryFix", "Found existing item: ${existingItem.text}")
                val needsUpdate = (!recordItem.categories.isNullOrEmpty() && existingItem.categories != recordItem.categories) ||
                        (!recordItem.imageUris.isNullOrEmpty() && existingItem.imageUris != recordItem.imageUris)
                if (needsUpdate) {
                    Log.d("CategoryFix", "Updating item: ${existingItem.text}")
                    val itemText = recordItemToTodoItemText(recordItem)
                    val (hasPrimaryCategory, hasSecondaryCategory, hasTertiaryCategory) = determineCategoryTypes(recordItem.categories)
                    val updatedItem = existingItem.copy(
                        text = itemText,
                        categories = recordItem.categories,
                        imageUris = recordItem.imageUris,
                        hasPrimaryCategory = hasPrimaryCategory,
                        hasSecondaryCategory = hasSecondaryCategory,
                        hasTertiaryCategory = hasTertiaryCategory
                    )
                    repository.update(updatedItem)
                    Log.d("CategoryFix", "Updated item with new data")
                } else {
                    Log.d("CategoryFix", "No update needed")
                }
            } else {
                Log.d("CategoryFix", "Creating new item: ${recordItem.description}")
                val itemText = recordItemToTodoItemText(recordItem)
                val finalCategories = recordItem.categories ?: parseCategoryInfo(itemText).second
                val (hasPrimaryCategory, hasSecondaryCategory, hasTertiaryCategory) = determineCategoryTypes(finalCategories)
                val newItem = TodoItem(
                    text = itemText,
                    isDone = recordItem.isChecked,
                    timestamp = startOfDayMillis,
                    categories = finalCategories,
                    imageUris = recordItem.imageUris,
                    hasPrimaryCategory = hasPrimaryCategory,
                    hasSecondaryCategory = hasSecondaryCategory,
                    hasTertiaryCategory = hasTertiaryCategory
                )
                repository.insert(newItem)
                Log.d("CategoryFix", "Inserted new item")
            }
        }
        _undoableDeletedItemsByDate.value = _undoableDeletedItemsByDate.value.mapValues { it.value.filter { it.id != -1 } }
        updateSelectedDate(targetDate)
        Log.d("CategoryFix", "==== MERGE COMPLETED ====")
    }

    fun clearAndSetRecordItems(recordItems: List<RecordItem>, targetDate: LocalDate) = viewModelScope.launch(Dispatchers.IO) {
        Log.d("CategoryFix", "==== CLEAR AND SET STARTED ====")
        Log.d("CategoryFix", "Total record items: ${recordItems.size}")
        recordItems.forEachIndexed { index, item ->
            Log.d("CategoryFix", "RecordItem #$index: ${item.description}, Categories: ${item.categories}, Images: ${item.imageUris?.size ?: 0}")
        }
        val startOfDayMillis = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val itemsToDelete = repository.getAllItemsForDateRange(startOfDayMillis, startOfDayMillis + 86400000 - 1)
        val idsToDelete = itemsToDelete.map { it.id }
        Log.d("CategoryFix", "Found ${idsToDelete.size} items to delete.")
        if (idsToDelete.isNotEmpty()) {
            repository.deleteItemsByIds(idsToDelete)
            Log.d("CategoryFix", "Deleted items with IDs: $idsToDelete")
        }
        val newTodoItems = recordItems.map {
            val itemText = recordItemToTodoItemText(it)
            val finalCategories = it.categories ?: parseCategoryInfo(itemText).second
            val (hasPrimaryCategory, hasSecondaryCategory, hasTertiaryCategory) = determineCategoryTypes(finalCategories)
            TodoItem(
                text = itemText,
                isDone = it.isChecked,
                timestamp = startOfDayMillis,
                categories = finalCategories,
                imageUris = it.imageUris,
                hasPrimaryCategory = hasPrimaryCategory,
                hasSecondaryCategory = hasSecondaryCategory,
                hasTertiaryCategory = hasTertiaryCategory
            )
        }
        newTodoItems.forEachIndexed { index, item ->
            Log.d("CategoryFix", "New TodoItem #$index: Text: ${item.text}, Images: ${item.imageUris?.size ?: 0}")
        }
        repository.insertItems(newTodoItems)
        Log.d("CategoryFix", "Finished inserting ${newTodoItems.size} new items.")
        val targetDateMap = _undoableDeletedItemsByDate.value.toMutableMap()
        targetDateMap.remove(targetDate)
        _undoableDeletedItemsByDate.value = targetDateMap
        updateSelectedDate(targetDate)
        Log.d("CategoryFix", "==== CLEAR AND SET COMPLETED ====")
    }

    private fun determineCategoryTypes(categories: List<String>?): Triple<Boolean, Boolean, Boolean> {
        if (categories.isNullOrEmpty()) {
            return Triple(false, false, false)
        }
        val hasPrimary = categories.any { cat -> getSavedCategories("primary")?.any { it.name == cat } == true }
        val hasSecondary = categories.any { cat -> getSavedCategories("secondary")?.any { it.name == cat } == true }
        val hasTertiary = categories.any { cat -> getSavedCategories("tertiary")?.any { it.name == cat } == true }
        val hasUnknown = categories.isNotEmpty() && !hasPrimary && !hasSecondary && !hasTertiary
        return Triple(hasPrimary || hasUnknown, hasSecondary, hasTertiary)
    }

    private fun todoItemToDisplayText(todoItem: TodoItem): String {
        return todoItem.text.trim()
    }

    private fun addDeletedItemForCurrentDate(item: TodoItem) {
        val currentDate = _selectedDate.value
        val currentItems = _undoableDeletedItemsByDate.value[currentDate] ?: emptyList()
        _undoableDeletedItemsByDate.value = _undoableDeletedItemsByDate.value +
                mapOf(currentDate to (listOf(item) + currentItems))
    }

    private fun addDeletedItemsForCurrentDate(items: List<TodoItem>) {
        if (items.isEmpty()) return
        val currentDate = _selectedDate.value
        val currentItems = _undoableDeletedItemsByDate.value[currentDate] ?: emptyList()
        _undoableDeletedItemsByDate.value = _undoableDeletedItemsByDate.value +
                mapOf(currentDate to (items.reversed() + currentItems))
    }

    fun undoLastDelete() = viewModelScope.launch {
        try {
            val currentDate = _selectedDate.value
            val itemsForCurrentDate = _undoableDeletedItemsByDate.value[currentDate] ?: emptyList()
            if (itemsForCurrentDate.isNotEmpty()) {
                val itemToRestore = itemsForCurrentDate.first()
                repository.insert(itemToRestore)
                val updatedItems = itemsForCurrentDate.drop(1)
                val updatedMap = _undoableDeletedItemsByDate.value.toMutableMap()
                if (updatedItems.isEmpty()) {
                    updatedMap.remove(currentDate)
                } else {
                    updatedMap[currentDate] = updatedItems
                }
                _undoableDeletedItemsByDate.value = updatedMap
            }
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error undoing last delete", e)
        }
    }

    fun clearLastDeletedItem() = viewModelScope.launch {
        try {
            val currentDate = _selectedDate.value
            Log.d("KharchaJi", "Clearing last deleted items for date: $currentDate")
            val updatedMap = _undoableDeletedItemsByDate.value.toMutableMap()
            updatedMap.remove(currentDate)
            _undoableDeletedItemsByDate.value = updatedMap
            Log.d("KharchaJi", "Successfully cleared deleted items for date: $currentDate")
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error clearing last deleted item", e)
        }
    }

    val allCalculationRecords: StateFlow<List<CalculationRecord>> = repository.allCalculationRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertCalculationRecord(record: CalculationRecord) = viewModelScope.launch {
        try {
            repository.insertCalculationRecord(record)
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error inserting calculation record", e)
        }
    }

    fun getCalculationRecordById(id: Int): StateFlow<CalculationRecord?> {
        return repository.getCalculationRecordById(id)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    fun deleteCalculationRecord(record: CalculationRecord) = viewModelScope.launch {
        try {
            Log.d("KharchaJi", "Deleting calculation record: ${record.id}")
            repository.deleteCalculationRecord(record)
            Log.d("KharchaJi", "Successfully deleted calculation record: ${record.id}")
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error deleting calculation record: ${record.id}", e)
        }
    }

    fun deleteCalculationRecordById(recordId: Int) = viewModelScope.launch {
        try {
            Log.d("KharchaJi", "Deleting calculation record by ID: $recordId")
            repository.deleteCalculationRecordById(recordId)
            Log.d("KharchaJi", "Successfully deleted calculation record by ID: $recordId")
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error deleting calculation record by ID: $recordId", e)
        }
    }

    fun deleteAllCalculationRecords() = viewModelScope.launch {
        try {
            Log.d("KharchaJi", "Deleting all calculation records")
            repository.deleteAllCalculationRecords()
            Log.d("KharchaJi", "Successfully deleted all calculation records")
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error deleting all calculation records", e)
        }
    }

    fun getCalculationRecordsForDate(date: LocalDate): Flow<List<CalculationRecord>> {
        val startOfDayMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDayMillis = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        return repository.getCalculationRecordsForDateRange(startOfDayMillis, endOfDayMillis)
    }

    fun getMasterRecordForDate(date: LocalDate): Flow<CalculationRecord?> {
        val startOfDayMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDayMillis = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        Log.d("CalendarDebug", "VM: getMasterRecordForDate for $date (Millis: $startOfDayMillis to $endOfDayMillis)")
        return repository.getMasterSaveRecordForDate(startOfDayMillis, endOfDayMillis)
    }

    fun getExpensesForDate(date: LocalDate): StateFlow<List<TodoItem>> {
        val startOfDayMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDayMillis = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        val filteredItems = MutableStateFlow<List<TodoItem>>(emptyList())
        viewModelScope.launch {
            _todoItems.collectLatest { allItems ->
                filteredItems.value = allItems.filter {
                    it.timestamp >= startOfDayMillis && it.timestamp <= endOfDayMillis
                }
            }
        }
        return filteredItems
    }

    fun getExpensesForCurrentMonthUpToDate(currentDate: LocalDate): StateFlow<List<TodoItem>> {
        val firstDayOfMonth = currentDate.withDayOfMonth(1)
        val startOfMonthMillis = firstDayOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfCurrentDateMillis = currentDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        val filteredItems = MutableStateFlow<List<TodoItem>>(emptyList())
        viewModelScope.launch {
            _todoItems.collectLatest { allItems ->
                filteredItems.value = allItems.filter {
                    it.timestamp >= startOfMonthMillis && it.timestamp <= endOfCurrentDateMillis
                }
            }
        }
        return filteredItems
    }

    fun updateSelectedDate(newDate: LocalDate) {
        _selectedDate.value = newDate
    }

    fun incrementDate() {
        _selectedDate.value = _selectedDate.value.plusDays(1)
    }

    fun decrementDate() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun setDate(date: LocalDate) {
        updateSelectedDate(date)
    }

    suspend fun getAllExpensesForExport(): List<TodoItem> {
        Log.d("ExportBackup", "Fetching all expenses directly from database")
        return repository.getAllItems()
    }

    suspend fun getAllCalculationRecordsForExport(): List<CalculationRecord> {
        Log.d("ExportBackup", "Fetching all calculation records directly from database")
        return repository.getAllCalculationRecordsForExport()
    }

    suspend fun getUncategorizedExpenses(): List<TodoItem> {
        Log.d("UncategorizedExpenses", "Fetching all uncategorized expenses")
        val allItems = repository.getAllItems()
        return allItems.filter { it.categories.isNullOrEmpty() }
    }

    suspend fun getExpensesWithLessThanThreeCategories(): List<TodoItem> {
        Log.d("CategoryExpenses", "Fetching expenses with less than three categories")
        val allItems = repository.getAllItems()
        return allItems.filter { 
            !it.categories.isNullOrEmpty() && it.categories!!.size < 3 
        }
    }
    
    suspend fun getExpensesWithMoreThanThreeCategories(): List<TodoItem> {
        Log.d("CategoryExpenses", "Fetching expenses with more than three categories")
        val allItems = repository.getAllItems()
        return allItems.filter { 
            !it.categories.isNullOrEmpty() && it.categories!!.size > 3 
        }
    }
    
    suspend fun getExpensesWithExactlyThreeCategories(): List<TodoItem> {
        Log.d("CategoryExpenses", "Fetching expenses with exactly three categories")
        val allItems = repository.getAllItems()
        return allItems.filter { 
            !it.categories.isNullOrEmpty() && it.categories!!.size == 3 
        }
    }

    suspend fun getAllUniqueCategories(): List<String> {
        Log.d("Categories", "Fetching all unique categories from database")
        val allItems = repository.getAllItems()
        val allCategories = allItems
            .mapNotNull { it.categories }
            .flatten()
        return allCategories.distinct().sorted()
    }

    suspend fun getPrimaryCategories(): List<String> {
        Log.d("Categories", "Fetching only primary categories for export dialog")
        try {
            val primaryCats = getSavedCategories("primary")?.map { it.name } ?: emptyList()
            Log.d("Categories", "Found ${primaryCats.size} primary categories defined in settings")
            
            if (primaryCats.isEmpty()) {
                Log.d("Categories", "No primary categories defined in settings")
                return emptyList()
            }
            
            val allItems = repository.getAllItems()
            Log.d("Categories", "Fetched ${allItems.size} total items from repository")
            
            val usedCategories = allItems
                .mapNotNull { it.categories }
                .flatten()
                .distinct()
            
            Log.d("Categories", "Found ${usedCategories.size} unique categories across all expenses")
            
            val result = usedCategories.filter { primaryCats.contains(it) }.sorted()
            Log.d("Categories", "Returning ${result.size} primary categories that are actually used in expenses")
            
            return result
        } catch (e: Exception) {
            Log.e("Categories", "Error fetching primary categories", e)
            return emptyList()
        }
    }

    suspend fun getAllCategoriesByType(): Map<String, List<String>> {
        Log.d("Categories", "Fetching all categories by type for HTML export")
        val primaryCats = getSavedCategories("primary")?.map { it.name } ?: emptyList()
        val secondaryCats = getSavedCategories("secondary")?.map { it.name } ?: emptyList()
        val tertiaryCats = getSavedCategories("tertiary")?.map { it.name } ?: emptyList()
        val allItems = repository.getAllItems()
        val usedCategories = allItems
            .mapNotNull { it.categories }
            .flatten()
            .distinct()
        val usedPrimaryCategories = usedCategories.filter { primaryCats.contains(it) }.sorted()
        val usedSecondaryCategories = usedCategories.filter { secondaryCats.contains(it) }.sorted()
        val usedTertiaryCategories = usedCategories.filter { tertiaryCats.contains(it) }.sorted()
        val uncategorizedCategories = usedCategories.filter {
            !primaryCats.contains(it) && !secondaryCats.contains(it) && !tertiaryCats.contains(it)
        }.sorted()
        return mapOf(
            "primary" to usedPrimaryCategories,
            "secondary" to usedSecondaryCategories,
            "tertiary" to usedTertiaryCategories,
            "other" to uncategorizedCategories
        )
    }

    fun clearAndInsertAllData(todoItems: List<TodoItem>, calculationRecords: List<CalculationRecord>) = viewModelScope.launch(Dispatchers.IO) {
        Log.d("ImportBackup", "Starting clearAndInsertAllData")
        Log.d("ImportBackup", "Inserting ${todoItems.size} todo items and ${calculationRecords.size} calculation records")
        val itemsWithCategories = todoItems.filter { !it.categories.isNullOrEmpty() }
        Log.d("ImportBackup", "Found ${itemsWithCategories.size} items with categories")
        val recordsWithCategories = calculationRecords.filter { record ->
            record.items.any { !it.categories.isNullOrEmpty() }
        }
        Log.d("ImportBackup", "Found ${recordsWithCategories.size} records containing items with categories")
        repository.clearAndInsertAllData(todoItems, calculationRecords)
        Log.d("ImportBackup", "Completed clearAndInsertAllData")
    }

    suspend fun getAllItemsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): List<TodoItem> {
        return withContext(Dispatchers.IO) {
            repository.getAllItemsForDateRange(startOfDayMillis, endOfDayMillis)
        }
    }

    fun insertCalculationRecordIfNotDuplicateAsync(record: CalculationRecord) = viewModelScope.launch {
        insertCalculationRecordIfNotDuplicate(record)
    }

    suspend fun insertCalculationRecordIfNotDuplicate(record: CalculationRecord): Boolean {
        Log.d("DuplicateCheck", "====== CHECKING FOR DUPLICATES ======")
        val recordDate = record.recordDate.toLocalDate()
        val startOfDay = recordDate.toEpochMilli()
        val endOfDay = recordDate.plusDays(1).toEpochMilli() - 1
        val existingRecords = repository.getAllCalculationRecordsForDateRangeDirect(startOfDay, endOfDay)
            .filter { !it.isMasterSave }
        Log.d("DuplicateCheck", "Found ${existingRecords.size} existing non-master records for date: $recordDate")
        val newRecordItems = record.items.map {
            "${it.description.trim()}|${it.price.trim()}|${it.quantity?.trim() ?: ""}"
        }.sorted()
        Log.d("DuplicateCheck", "New record has ${newRecordItems.size} items")
        for (existingRecord in existingRecords) {
            val existingItems = existingRecord.items.map {
                "${it.description.trim()}|${it.price.trim()}|${it.quantity?.trim() ?: ""}"
            }.sorted()
            Log.d("DuplicateCheck", "Comparing with record #${existingRecord.id} (${existingItems.size} items)")
            if (newRecordItems.size == existingItems.size && newRecordItems == existingItems) {
                Log.d("DuplicateCheck", "DUPLICATE FOUND: Record #${existingRecord.id} has identical items")
                return false
            }
        }
        Log.d("DuplicateCheck", "No duplicates found, creating new record")
        repository.insertCalculationRecord(record)
        return true
    }

    suspend fun saveToMasterRecord(date: LocalDate, allItems: List<TodoItem>): Pair<Boolean, Boolean> {
        val recordItems = allItems.map { todoItemToRecordItem(it) }
        if (recordItems.isEmpty()) {
            return Pair(false, false)
        }
        val totalSum = recordItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
        val checkedItems = recordItems.filter { it.isChecked }
        val checkedItemsCount = checkedItems.size
        val checkedItemsSum = checkedItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
        var regularRecordCreated = false
        var masterRecordCreatedOrUpdated = false
        Log.d("MasterSave", "Checking for existing regular records with identical items")
        val startOfDayMillis = date.toEpochMilli()
        val endOfDayMillis = date.plusDays(1).toEpochMilli() - 1
        val existingRegularRecords = repository.getAllCalculationRecordsForDateRangeDirect(
            startOfDayMillis, endOfDayMillis
        ).filter { !it.isMasterSave }
        Log.d("MasterSave", "Found ${existingRegularRecords.size} existing regular records for date: $date")
        val newRecordItems = recordItems.map {
            val categoriesPart = it.categories?.joinToString(",") ?: ""
            val imagesPart = it.imageUris?.sorted()?.joinToString(",") ?: ""
            "${it.description.trim()}|${it.price.trim()}|${it.quantity?.trim() ?: ""}|$categoriesPart|$imagesPart"
        }.sorted()
        var duplicateFound = false
        for (existingRecord in existingRegularRecords) {
            val existingItems = existingRecord.items.map {
                val categoriesPart = it.categories?.joinToString(",") ?: ""
                val imagesPart = it.imageUris?.sorted()?.joinToString(",") ?: ""
                "${it.description.trim()}|${it.price.trim()}|${it.quantity?.trim() ?: ""}|$categoriesPart|$imagesPart"
            }.sorted()
            if (newRecordItems.size == existingItems.size && newRecordItems == existingItems) {
                Log.d("MasterSave", "Found identical regular record #${existingRecord.id}, skipping creation")
                duplicateFound = true
                break
            }
        }
        if (!duplicateFound) {
            val regularRecord = CalculationRecord(
                items = recordItems,
                totalSum = totalSum,
                checkedItemsCount = checkedItemsCount,
                checkedItemsSum = checkedItemsSum,
                recordDate = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                isMasterSave = false
            )
            repository.insertCalculationRecord(regularRecord)
            regularRecordCreated = true
            Log.d("MasterSave", "Created new regular record")
        }
        val existingMasterRecords = repository.getMasterSaveRecordsForDateRange(
            startOfDayMillis, endOfDayMillis
        )
        if (existingMasterRecords.isNotEmpty()) {
            val existingMaster = existingMasterRecords.first()
            Log.d("MasterSave", "Found existing master record #${existingMaster.id}")
            val updatedMasterItems = mutableListOf<RecordItem>()
            val existingItemsBySourceId = existingMaster.items
                .filter { it.sourceItemId != null }
                .associateBy { it.sourceItemId }
            val existingItemsByName = existingMaster.items
                .groupBy { it.description.trim().lowercase() }
            val processedExistingItems = mutableSetOf<RecordItem>()
            for (newItem in recordItems) {
                var matchFound = false
                var bestMatchItem: RecordItem? = null
                if (newItem.sourceItemId != null) {
                    bestMatchItem = existingItemsBySourceId[newItem.sourceItemId]
                    if (bestMatchItem != null) {
                        matchFound = true
                        Log.d("MasterSave", "Found match by sourceItemId: ${newItem.sourceItemId} for '${newItem.description}'")
                    }
                }
                if (!matchFound) {
                    val normalizedName = newItem.description.trim().lowercase()
                    val matchingExistingItems = existingItemsByName[normalizedName]
                    if (matchingExistingItems != null && matchingExistingItems.isNotEmpty()) {
                        bestMatchItem = matchingExistingItems.firstOrNull { it !in processedExistingItems }
                        if (bestMatchItem != null) {
                            matchFound = true
                            Log.d("MasterSave", "Found match by name: '${newItem.description}'")
                        }
                    }
                }
                if (!matchFound) {
                    for (existingItem in existingMaster.items) {
                        if (existingItem in processedExistingItems) continue
                        if (existingItem.price.trim() == newItem.price.trim()) {
                            val existingNameLower = existingItem.description.trim().lowercase()
                            val newNameLower = newItem.description.trim().lowercase()
                            if (existingNameLower.contains(newNameLower) || newNameLower.contains(existingNameLower)) {
                                bestMatchItem = existingItem
                                matchFound = true
                                Log.d("MasterSave", "Found fuzzy match: '${existingItem.description}' ~ '${newItem.description}'")
                                break
                            }
                        }
                    }
                }
                if (matchFound && bestMatchItem != null) {
                    processedExistingItems.add(bestMatchItem)
                    val finalSourceItemId = newItem.sourceItemId ?: bestMatchItem.sourceItemId
                    val updatedItem = bestMatchItem.copy(
                        description = newItem.description,
                        price = newItem.price,
                        quantity = newItem.quantity,
                        categories = newItem.categories,
                        imageUris = newItem.imageUris,
                        isChecked = newItem.isChecked,
                        sourceItemId = finalSourceItemId
                    )
                    Log.d("MasterSave", "Updating existing item: '${bestMatchItem.description}' -> '${newItem.description}' (price: ${bestMatchItem.price} -> ${newItem.price})")
                    updatedMasterItems.add(updatedItem)
                } else {
                    Log.d("MasterSave", "Adding new item to master record: ${newItem.description}")
                    updatedMasterItems.add(newItem)
                }
            }
            val remainingItems = existingMaster.items.filter { it !in processedExistingItems }
            if (remainingItems.isNotEmpty()) {
                Log.d("MasterSave", "Adding ${remainingItems.size} unmatched existing items to master record")
                updatedMasterItems.addAll(remainingItems)
            }
            val updatedTotalSum = updatedMasterItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
            val masterItemsChanged = !areItemListsIdentical(existingMaster.items, updatedMasterItems)
            if (masterItemsChanged) {
                val updatedMaster = existingMaster.copy(
                    items = updatedMasterItems,
                    totalSum = updatedTotalSum,
                    timestamp = System.currentTimeMillis()
                )
                repository.updateCalculationRecord(updatedMaster)
                masterRecordCreatedOrUpdated = true
                Log.d("MasterSave", "Updated existing master record with new items")
            } else {
                Log.d("MasterSave", "No changes to master record, skipping update")
            }
        } else {
            val masterRecord = CalculationRecord(
                items = recordItems,
                totalSum = totalSum,
                checkedItemsCount = checkedItemsCount,
                checkedItemsSum = checkedItemsSum,
                recordDate = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                isMasterSave = true
            )
            repository.insertCalculationRecord(masterRecord)
            masterRecordCreatedOrUpdated = true
            Log.d("MasterSave", "Created new master record")
        }
        return Pair(regularRecordCreated, masterRecordCreatedOrUpdated)
    }

    private fun areItemListsIdentical(items1: List<RecordItem>, items2: List<RecordItem>): Boolean {
        if (items1.size != items2.size) return false
        val items1Simplified = items1.map {
            val categoriesPart = it.categories?.joinToString(",") ?: ""
            val imagesPart = it.imageUris?.sorted()?.joinToString(",") ?: ""
            "${it.description.trim()}|${it.price.trim()}|${it.quantity?.trim() ?: ""}|$categoriesPart|$imagesPart"
        }.sorted()
        val items2Simplified = items2.map {
            val categoriesPart = it.categories?.joinToString(",") ?: ""
            val imagesPart = it.imageUris?.sorted()?.joinToString(",") ?: ""
            "${it.description.trim()}|${it.price.trim()}|${it.quantity?.trim() ?: ""}|$categoriesPart|$imagesPart"
        }.sorted()
        return items1Simplified == items2Simplified
    }

    fun saveToMasterRecordAsync(date: LocalDate, allItems: List<TodoItem>) = viewModelScope.launch {
        saveToMasterRecord(date, allItems)
    }

    fun updateCalculationRecord(record: CalculationRecord) = viewModelScope.launch {
        repository.updateCalculationRecord(record)
    }

    suspend fun removeRecordItem(record: CalculationRecord, index: Int): CalculationRecord {
        val updatedItems = record.items.toMutableList()
        if (index >= 0 && index < updatedItems.size) {
            updatedItems.removeAt(index)
        }
        val totalSum = updatedItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
        val checkedItems = updatedItems.filter { it.isChecked }
        val checkedItemsCount = checkedItems.size
        val checkedItemsSum = checkedItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
        return record.copy(
            items = updatedItems,
            totalSum = totalSum,
            checkedItemsCount = checkedItemsCount,
            checkedItemsSum = checkedItemsSum,
            timestamp = System.currentTimeMillis()
        )
    }

    suspend fun addRecordItem(
        record: CalculationRecord,
        description: String,
        price: String,
        quantity: String?,
        categories: List<String>? = null,
        sourceItemId: Int? = null
    ): CalculationRecord {
        val updatedItems = record.items.toMutableList()
        updatedItems.add(
            RecordItem(
                description = description,
                price = price,
                quantity = quantity,
                isChecked = false,
                categories = categories,
                sourceItemId = sourceItemId
            )
        )
        val totalSum = updatedItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
        val checkedItems = updatedItems.filter { it.isChecked }
        val checkedItemsCount = checkedItems.size
        val checkedItemsSum = checkedItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
        return record.copy(
            items = updatedItems,
            totalSum = totalSum,
            checkedItemsCount = checkedItemsCount,
            checkedItemsSum = checkedItemsSum,
            timestamp = System.currentTimeMillis()
        )
    }

    suspend fun updateRecordItem(
        record: CalculationRecord,
        index: Int,
        description: String,
        price: String,
        quantity: String?,
        categories: List<String>? = null
    ): CalculationRecord {
        val updatedItems = record.items.toMutableList()
        if (index >= 0 && index < updatedItems.size) {
            val itemCategories = categories ?: updatedItems[index].categories
            val sourceItemId = updatedItems[index].sourceItemId
            updatedItems[index] = RecordItem(
                description = description,
                price = price,
                quantity = quantity,
                isChecked = updatedItems[index].isChecked,
                categories = itemCategories,
                imageUris = updatedItems[index].imageUris,
                sourceItemId = sourceItemId
            )
        }
        val totalSum = updatedItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
        val checkedItems = updatedItems.filter { it.isChecked }
        val checkedItemsCount = checkedItems.size
        val checkedItemsSum = checkedItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
        return record.copy(
            items = updatedItems,
            totalSum = totalSum,
            checkedItemsCount = checkedItemsCount,
            checkedItemsSum = checkedItemsSum,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Get master record totals for a specific month, grouped by category
     * This is used by the Monthly Report screen to show bars based on master records
     */
    suspend fun getMasterRecordTotalsForMonth(yearMonth: YearMonth): Map<String, Double> {
        return withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, Double>()
            
            try {
                val startOfMonthMillis = yearMonth.atDay(1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfMonthMillis = yearMonth.atEndOfMonth()
                    .plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                
                // Get all master records for this month
                val records = repository.getMasterRecordsForDateRange(startOfMonthMillis, endOfMonthMillis)
                Log.d("MasterRecords", "Found ${records.size} master records for month: $yearMonth")
                
                // Process each record
                records.forEach { record ->
                    // Process each item in the record
                    record.items.forEach { item ->
                        // Skip if no categories or price is null
                        if (item.categories.isNullOrEmpty() || item.price == null) return@forEach
                        
                        // Convert price to Double
                        val price = item.price.toDoubleOrNull() ?: return@forEach
                        Log.d("MasterRecords", "Processing item: ${item.description} with price: $price and categories: ${item.categories}")
                        
                        // First, include the category directly as it appears in the item
                        // This ensures we catch categories that might be stored directly
                        item.categories.forEach { category ->
                            result[category] = (result[category] ?: 0.0) + price
                        }
                        
                        // Then also process through hierarchy for nested category handling
                        val (primary, secondary, tertiary) = intelligentlyCategorize(item.categories.toSet())
                            .let { (p, s, t) -> Triple(p.toSet(), s.toSet(), t.toSet()) }
                        
                        primary.forEach { category ->
                            if (!item.categories.contains(category)) { // Avoid double-counting
                                result[category] = (result[category] ?: 0.0) + price
                            }
                        }
                        
                        secondary.forEach { category ->
                            if (!item.categories.contains(category)) { // Avoid double-counting
                                result[category] = (result[category] ?: 0.0) + price
                            }
                        }
                        
                        tertiary.forEach { category ->
                            if (!item.categories.contains(category)) { // Avoid double-counting
                                result[category] = (result[category] ?: 0.0) + price
                            }
                        }
                    }
                }
                
                // Log the final result for debugging
                Log.d("MasterRecords", "Final category totals for $yearMonth: ${result.entries.joinToString { "${it.key}=${it.value}" }}")
            } catch (e: Exception) {
                Log.e("MasterRecords", "Error processing master records", e)
                e.printStackTrace()
            }
            
            result
        }
    }

    /**
     * Get all items for a specific date range directly
     * This is used by the Monthly Report screen to show items when clicking on a month
     */
    suspend fun getItemsForMonthRange(startMillis: Long, endMillis: Long): List<TodoItem> {
        return withContext(Dispatchers.IO) {
            repository.getAllItemsForDateRange(startMillis, endMillis)
        }
    }

    suspend fun saveCategories(type: String, categories: List<ExpenseCategory>) {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("categories_prefs", android.content.Context.MODE_PRIVATE)
        val gson = Gson()
        val json = gson.toJson(categories)
        sharedPreferences.edit().putString("${type}_categories", json).apply()
        when (type) {
            "primary" -> _primaryCategories.value = categories
            "secondary" -> _secondaryCategories.value = categories
            "tertiary" -> _tertiaryCategories.value = categories
        }
    }

    fun getSavedCategories(type: String): List<ExpenseCategory>? {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("categories_prefs", android.content.Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("${type}_categories", null) ?: return null
        val gson = Gson()
        val typeToken = TypeToken.getParameterized(List::class.java, ExpenseCategory::class.java).type
        val categories = gson.fromJson<List<ExpenseCategory>>(json, typeToken)
        val defaultCategories = when (type) {
            "primary" -> com.example.monday.ui.components.DefaultCategories.primaryCategories
            "secondary" -> com.example.monday.ui.components.DefaultCategories.secondaryCategories
            "tertiary" -> com.example.monday.ui.components.DefaultCategories.tertiaryCategories
            else -> emptyList()
        }
        val iconMap = defaultCategories.associate { it.name to it.icon }
        return categories.map { category ->
            val defaultIcon = iconMap[category.name] ?: Icons.Default.MoreHoriz
            category.copy(icon = defaultIcon)
        }
    }

    suspend fun saveCategoryVisibilitySetting(type: String, isVisible: Boolean) {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("categories_prefs", android.content.Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("${type}_visibility", isVisible).apply()
    }

    fun getCategoryVisibilitySetting(type: String): Boolean? {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("categories_prefs", android.content.Context.MODE_PRIVATE)
        if (!sharedPreferences.contains("${type}_visibility")) return null
        return sharedPreferences.getBoolean("${type}_visibility", true)
    }

    fun saveRecentlySelectedCategory(categoryType: String, categoryName: String) {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("categories_prefs", android.content.Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("recent_${categoryType}", categoryName).apply()
    }

    fun getRecentlySelectedCategory(categoryType: String): String? {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("categories_prefs", android.content.Context.MODE_PRIVATE)
        return sharedPreferences.getString("recent_${categoryType}", null)
    }

    fun insertEmptyCalculationRecordIfNeeded(date: LocalDate) = viewModelScope.launch(Dispatchers.IO) {
        val startOfDayMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDayMillis = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        val existingRecords = repository.getAllCalculationRecordsForDateRangeDirect(
            startOfDayMillis, endOfDayMillis
        )
        if (existingRecords.isEmpty()) {
            val emptyRecord = CalculationRecord(
                items = emptyList(),
                totalSum = 0.0,
                checkedItemsCount = 0,
                checkedItemsSum = 0.0,
                recordDate = startOfDayMillis,
                isMasterSave = false
            )
            repository.insertCalculationRecord(emptyRecord)
            Log.d("CalculationRecords", "Created empty record for date: $date")
        } else {
            Log.d("CalculationRecords", "Found ${existingRecords.size} existing records for date: $date")
        }
    }

    fun getItemById(id: Int): TodoItem {
        return _todoItems.value.find { it.id == id } ?: throw IllegalArgumentException("Item with ID $id not found")
    }

    fun deleteImageFromItem(item: TodoItem, imageUrl: String) = viewModelScope.launch {
        imageUpdateMutex.withLock {
            Log.d("ImageDeletion", "Mutex locked for deleting $imageUrl")
            Log.d("ImageDeletion", "Starting image deletion for item ID ${item.id}, URL: $imageUrl")
            val currentItem = repository.getItemById(item.id)
            if (currentItem == null) {
                Log.e("ImageDeletion", "Item with ID ${item.id} not found in repository. Aborting deletion.")
                return@withLock
            }
            val updatedUris = currentItem.imageUris?.toMutableList() ?: mutableListOf()
            Log.d("ImageDeletion", "Original image URIs from repo: $updatedUris")
            val removed = updatedUris.remove(imageUrl)
            if (!removed) {
                Log.w("ImageDeletion", "Image URL not found in item's list, might have been already deleted: $imageUrl")
            }
            Log.d("ImageDeletion", "Updated image URIs after removal: $updatedUris")
            val updatedItem = currentItem.copy(imageUris = updatedUris)
            Log.d("ImageDeletion", "Updating item ID=${currentItem.id} with new URIs size: ${updatedUris.size}")
            repository.update(updatedItem)
            _todoItems.value = _todoItems.value.map {
                if (it.id == currentItem.id) updatedItem else it
            }
            if (removed) {
                try {
                    val uri = Uri.parse(imageUrl)
                    Log.d("ImageDeletion", "Parsed URI for file deletion: $uri")
                    when (uri.scheme) {
                        "content" -> {
                            val contentResolver = getApplication<Application>().contentResolver
                            try {
                                val result = contentResolver.delete(uri, null, null)
                                Log.d("ImageDeletion", "ContentResolver deletion result: $result")
                            } catch (e: Exception) {
                                Log.e("ImageDeletion", "Error deleting via ContentResolver", e)
                            }
                        }
                        "file" -> {
                            val file = File(uri.path ?: "")
                            Log.d("ImageDeletion", "File path: ${file.absolutePath}, exists: ${file.exists()}")
                            if (file.exists()) {
                                if (file.delete()) {
                                    Log.d("ImageDeletion", "File deletion successful")
                                } else {
                                    Log.w("ImageDeletion", "File deletion failed")
                                }
                            } else {
                                Log.w("ImageDeletion", "File not found for deletion")
                            }
                        }
                        else -> {
                            Log.w("ImageDeletion", "Unhandled URI scheme for deletion: ${uri.scheme}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ImageDeletion", "Error deleting image file", e)
                }
            }
            Log.d("ImageDeletion", "Mutex unlocked for deleting $imageUrl")
        }
    }

    fun addImageToItem(item: TodoItem, imageUri: Uri) = viewModelScope.launch {
        Log.d("ImageDebug", "Starting addImageToItem for item ${item.id} with uri: $imageUri")
        try {
            val tempUris = item.imageUris.orEmpty() + imageUri.toString()
            val tempUpdatedItem = item.copy(imageUris = tempUris)
            _todoItems.value = _todoItems.value.map {
                if (it.id == item.id) tempUpdatedItem else it
            }
            withContext(Dispatchers.IO) {
                val internalUri = copyUriToInternalStorage(getApplication(), imageUri)
                Log.d("ImageDebug", "After copyUriToInternalStorage, result: $internalUri")
                if (internalUri != null) {
                    val currentItem = repository.getItemById(item.id)
                    val currentUris = currentItem?.imageUris.orEmpty()
                    Log.d("ImageDebug", "Current image URIs from database: $currentUris")
                    val updatedUris = currentUris + internalUri.toString()
                    Log.d("ImageDebug", "Updated image URIs: $updatedUris")
                    val updatedItem = currentItem?.copy(imageUris = updatedUris) ?: item.copy(imageUris = updatedUris)
                    Log.d("ImageDebug", "Updating item in repository with new URIs")
                    repository.update(updatedItem)
                    withContext(Dispatchers.Main) {
                        _todoItems.value = _todoItems.value.map {
                            if (it.id == item.id) updatedItem else it
                        }
                    }
                    Log.d("ImageDebug", "Item updated successfully")
                } else {
                    Log.e("ImageDebug", "Failed to copy URI to internal storage")
                    withContext(Dispatchers.Main) {
                        _todoItems.value = _todoItems.value.map {
                            if (it.id == item.id) item else it
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ImageDebug", "Error in addImageToItem", e)
            _todoItems.value = _todoItems.value.map {
                if (it.id == item.id) item else it
            }
        }
    }

    suspend fun updateCategory(oldCategory: ExpenseCategory, newCategory: ExpenseCategory, type: String) {
        _lastCategoryAction.value = CategoryAction.Edited(oldCategory, newCategory, type)
        withContext(Dispatchers.IO) {
            val allItems = repository.getAllItems()
            val itemsToUpdate = allItems.filter { it.categories?.contains(oldCategory.name) == true }
            Log.d("CategoryUpdate", "Found ${itemsToUpdate.size} TodoItems with category '${oldCategory.name}' to update")
            for (item in itemsToUpdate) {
                val newCategories = item.categories?.map { if (it == oldCategory.name) newCategory.name else it }
                val updatedItem = item.copy(categories = newCategories)
                repository.update(updatedItem)
                Log.d("CategoryUpdate", "Updated TodoItem ID: ${item.id}")
            }
            val allRecords = repository.getAllCalculationRecordsForExport()
            var updatedRecordsCount = 0
            for (record in allRecords) {
                var recordNeedsUpdate = false
                val updatedItems = record.items.map { recordItem ->
                    if (recordItem.categories?.contains(oldCategory.name) == true) {
                        recordNeedsUpdate = true
                        val newRecordCategories = recordItem.categories.map {
                            if (it == oldCategory.name) newCategory.name else it
                        }
                        recordItem.copy(categories = newRecordCategories)
                    } else {
                        recordItem
                    }
                }
                if (recordNeedsUpdate) {
                    val updatedRecord = record.copy(items = updatedItems)
                    repository.updateCalculationRecord(updatedRecord)
                    updatedRecordsCount++
                    Log.d("CategoryUpdate", "Updated CalculationRecord ID: ${record.id}")
                }
            }
            Log.d("CategoryUpdate", "Updated a total of $updatedRecordsCount calculation records")
        }
        val currentCategories = when (type) {
            "primary" -> _primaryCategories.value.toMutableList()
            "secondary" -> _secondaryCategories.value.toMutableList()
            "tertiary" -> _tertiaryCategories.value.toMutableList()
            else -> mutableListOf()
        }
        val index = currentCategories.indexOf(oldCategory)
        if (index != -1) {
            currentCategories[index] = newCategory
            saveCategories(type, currentCategories)
            Log.d("CategoryUpdate", "Updated category in preferences: ${oldCategory.name} -> ${newCategory.name}")
        }
    }

    suspend fun deleteCategory(categoryToDelete: ExpenseCategory, type: String) {
        val affectedItems = mutableListOf<TodoItem>()
        val affectedRecords = mutableListOf<CalculationRecord>()

        withContext(Dispatchers.IO) {
            val allItems = repository.getAllItems()
            val itemsToUpdate = allItems.filter { it.categories?.contains(categoryToDelete.name) == true }
            Log.d("CategoryDelete", "Found ${itemsToUpdate.size} TodoItems with category '${categoryToDelete.name}' to update")

            for (item in itemsToUpdate) {
                affectedItems.add(item)
                val updatedCategories = item.categories?.filter { it != categoryToDelete.name }
                val (hasPrimary, hasSecondary, hasTertiary) = determineCategoryTypes(updatedCategories)
                val updatedItem = item.copy(
                    categories = updatedCategories,
                    hasPrimaryCategory = hasPrimary,
                    hasSecondaryCategory = hasSecondary,
                    hasTertiaryCategory = hasTertiary
                )
                repository.update(updatedItem)
                Log.d("CategoryDelete", "Removed category '${categoryToDelete.name}' from TodoItem ID: ${item.id}")
            }

            val allRecords = repository.getAllCalculationRecordsForExport()
            var updatedRecordsCount = 0
            for (record in allRecords) {
                var recordNeedsUpdate = false
                val updatedRecordItems = record.items.map { recordItem ->
                    if (recordItem.categories?.contains(categoryToDelete.name) == true) {
                        if (!recordNeedsUpdate) {
                            affectedRecords.add(record)
                        }
                        recordNeedsUpdate = true
                        val updatedCategories = recordItem.categories.filter { it != categoryToDelete.name }
                        recordItem.copy(categories = if (updatedCategories.isEmpty()) null else updatedCategories)
                    } else {
                        recordItem
                    }
                }
                if (recordNeedsUpdate) {
                    val updatedRecord = record.copy(items = updatedRecordItems)
                    repository.updateCalculationRecord(updatedRecord)
                    updatedRecordsCount++
                    Log.d("CategoryDelete", "Removed category '${categoryToDelete.name}' from CalculationRecord ID: ${record.id}")
                }
            }
            Log.d("CategoryDelete", "Updated a total of $updatedRecordsCount calculation records")
        }

        val currentCategories = when (type) {
            "primary" -> _primaryCategories.value.toMutableList()
            "secondary" -> _secondaryCategories.value.toMutableList()
            "tertiary" -> _tertiaryCategories.value.toMutableList()
            else -> mutableListOf()
        }
        if (currentCategories.remove(categoryToDelete)) {
            saveCategories(type, currentCategories)
            Log.d("CategoryDelete", "Removed category '${categoryToDelete.name}' from preferences")
            _lastCategoryAction.value = CategoryAction.Deleted(
                category = categoryToDelete,
                type = type,
                affectedItems = affectedItems,
                affectedRecords = affectedRecords
            )
        }
    }

    suspend fun moveCategory(category: ExpenseCategory, type: String, newPosition: Int) {
        val currentCategories = when (type) {
            "primary" -> _primaryCategories.value.toMutableList()
            "secondary" -> _secondaryCategories.value.toMutableList()
            "tertiary" -> _tertiaryCategories.value.toMutableList()
            else -> return
        }
        val currentPosition = currentCategories.indexOf(category)
        if (currentPosition == -1 || currentPosition == newPosition ||
            newPosition < 0 || newPosition >= currentCategories.size
        ) {
            return
        }
        _lastCategoryAction.value = CategoryAction.Moved(
            category = category,
            type = type,
            oldPosition = currentPosition,
            newPosition = newPosition
        )
        currentCategories.removeAt(currentPosition)
        currentCategories.add(newPosition, category)
        saveCategories(type, currentCategories)
        Log.d("CategoryMove", "Moved category '${category.name}' from position $currentPosition to $newPosition")
    }

    suspend fun addCategory(category: ExpenseCategory, type: String) {
        val currentCategories = when (type) {
            "primary" -> _primaryCategories.value.toMutableList()
            "secondary" -> _secondaryCategories.value.toMutableList()
            "tertiary" -> _tertiaryCategories.value.toMutableList()
            else -> mutableListOf()
        }
        currentCategories.add(category)
        saveCategories(type, currentCategories)
        _lastCategoryAction.value = CategoryAction.Added(category, type)
        Log.d("CategoryAdd", "Added category '${category.name}' to $type categories")
    }

    fun hasUndoableItemsForCurrentDate(): Boolean {
        val currentDate = _selectedDate.value
        return (_undoableDeletedItemsByDate.value[currentDate]?.isNotEmpty() == true)
    }

    suspend fun undoLastCategoryAction() {
        val action = _lastCategoryAction.value ?: return
        when (action) {
            is CategoryAction.Added -> {
                val currentCategories = when (action.type) {
                    "primary" -> _primaryCategories.value.toMutableList()
                    "secondary" -> _secondaryCategories.value.toMutableList()
                    "tertiary" -> _tertiaryCategories.value.toMutableList()
                    else -> mutableListOf()
                }
                currentCategories.remove(action.category)
                saveCategories(action.type, currentCategories)
                Log.d("CategoryUndo", "Undid addition of category '${action.category.name}'")
            }
            is CategoryAction.Edited -> {
                updateCategory(action.newCategory, action.oldCategory, action.type)
                Log.d("CategoryUndo", "Undid edit of category '${action.newCategory.name}' back to '${action.oldCategory.name}'")
            }
            is CategoryAction.Deleted -> {
                val currentCategories = when (action.type) {
                    "primary" -> _primaryCategories.value.toMutableList()
                    "secondary" -> _secondaryCategories.value.toMutableList()
                    "tertiary" -> _tertiaryCategories.value.toMutableList()
                    else -> mutableListOf()
                }
                currentCategories.add(action.category)
                saveCategories(action.type, currentCategories)
                withContext(Dispatchers.IO) {
                    for (item in action.affectedItems) {
                        val updatedCategories = (item.categories ?: emptyList()) + action.category.name
                        val (hasPrimary, hasSecondary, hasTertiary) = determineCategoryTypes(updatedCategories)
                        val updatedItem = item.copy(
                            categories = updatedCategories,
                            hasPrimaryCategory = hasPrimary,
                            hasSecondaryCategory = hasSecondary,
                            hasTertiaryCategory = hasTertiary
                        )
                        repository.update(updatedItem)
                    }
                    for (record in action.affectedRecords) {
                        var recordNeedsUpdate = false
                        val updatedItems = record.items.map { recordItem ->
                            if (recordItem.categories != null) {
                                recordNeedsUpdate = true
                                recordItem.copy(categories = recordItem.categories + action.category.name)
                            } else {
                                recordNeedsUpdate = true
                                recordItem.copy(categories = listOf(action.category.name))
                            }
                        }
                        if (recordNeedsUpdate) {
                            val updatedRecord = record.copy(items = updatedItems)
                            repository.updateCalculationRecord(updatedRecord)
                        }
                    }
                }
                Log.d("CategoryUndo", "Undid deletion of category '${action.category.name}'")
            }
            is CategoryAction.Moved -> {
                val currentCategories = when (action.type) {
                    "primary" -> _primaryCategories.value.toMutableList()
                    "secondary" -> _secondaryCategories.value.toMutableList()
                    "tertiary" -> _tertiaryCategories.value.toMutableList()
                    else -> mutableListOf()
                }
                if (currentCategories.remove(action.category)) {
                    val targetPosition = if (action.oldPosition < currentCategories.size)
                        action.oldPosition else currentCategories.size
                    currentCategories.add(targetPosition, action.category)
                    saveCategories(action.type, currentCategories)
                    Log.d("CategoryUndo", "Undid move of category '${action.category.name}' from position ${action.newPosition} back to ${action.oldPosition}")
                }
            }
        }
        _lastCategoryAction.value = null
    }

    suspend fun deleteCategoryWithOptions(
        categoryToDelete: ExpenseCategory,
        type: String,
        removeFromExpenses: Boolean
    ) {
        val affectedItems = mutableListOf<TodoItem>()
        val affectedRecords = mutableListOf<CalculationRecord>()

        if (removeFromExpenses) {
            withContext(Dispatchers.IO) {
                val allItems = repository.getAllItems()
                val itemsToUpdate = allItems.filter { it.categories?.contains(categoryToDelete.name) == true }
                Log.d("CategoryDelete", "Found ${itemsToUpdate.size} TodoItems with category '${categoryToDelete.name}' to update")

                for (item in itemsToUpdate) {
                    affectedItems.add(item)
                    val updatedCategories = item.categories?.filter { it != categoryToDelete.name }
                    val (hasPrimary, hasSecondary, hasTertiary) = determineCategoryTypes(updatedCategories)
                    val updatedItem = item.copy(
                        categories = updatedCategories,
                        hasPrimaryCategory = hasPrimary,
                        hasSecondaryCategory = hasSecondary,
                        hasTertiaryCategory = hasTertiary
                    )
                    repository.update(updatedItem)
                    Log.d("CategoryDelete", "Removed category '${categoryToDelete.name}' from TodoItem ID: ${item.id}")
                }

                val allRecords = repository.getAllCalculationRecordsForExport()
                var updatedRecordsCount = 0
                for (record in allRecords) {
                    var recordNeedsUpdate = false
                    val updatedItems = record.items.map { recordItem ->
                        if (recordItem.categories?.contains(categoryToDelete.name) == true) {
                            if (!recordNeedsUpdate) {
                                affectedRecords.add(record)
                            }
                            recordNeedsUpdate = true
                            val updatedCategories = recordItem.categories.filter { it != categoryToDelete.name }
                            recordItem.copy(categories = if (updatedCategories.isEmpty()) null else updatedCategories)
                        } else {
                            recordItem
                        }
                    }
                    if (recordNeedsUpdate) {
                        val updatedRecord = record.copy(items = updatedItems)
                        repository.updateCalculationRecord(updatedRecord)
                        updatedRecordsCount++
                        Log.d("CategoryDelete", "Removed category '${categoryToDelete.name}' from CalculationRecord ID: ${record.id}")
                    }
                }
                Log.d("CategoryDelete", "Updated a total of $updatedRecordsCount calculation records")
            }
        }

        val currentCategories = when (type) {
            "primary" -> _primaryCategories.value.toMutableList()
            "secondary" -> _secondaryCategories.value.toMutableList()
            "tertiary" -> _tertiaryCategories.value.toMutableList()
            else -> mutableListOf()
        }
        if (currentCategories.remove(categoryToDelete)) {
            saveCategories(type, currentCategories)
            Log.d("CategoryDelete", "Removed category '${categoryToDelete.name}' from preferences")
            _lastCategoryAction.value = CategoryAction.Deleted(
                category = categoryToDelete,
                type = type,
                affectedItems = affectedItems,
                affectedRecords = affectedRecords
            )
        }
    }

    fun getUndoableDeletedItemsByDate(): Map<LocalDate, List<TodoItem>> {
        return _undoableDeletedItemsByDate.value
    }

    fun restoreUndoableDeletedItemsByDate(undoableItemsByDate: Map<LocalDate, List<TodoItem>>) = viewModelScope.launch(Dispatchers.IO) {
        _undoableDeletedItemsByDate.value = undoableItemsByDate
    }

    fun setLastCategoryAction(action: CategoryAction) = viewModelScope.launch(Dispatchers.IO) {
        _lastCategoryAction.value = action
    }

    fun updateItems(items: List<TodoItem>) = viewModelScope.launch {
        repository.updateItems(items)
    }

    /**
     * Get the actual master records for a specific month
     * This is used by the Monthly Report screen to show master record details when clicking on a month
     */
    suspend fun getMasterRecordsForMonth(startMillis: Long, endMillis: Long): List<CalculationRecord> {
        return withContext(Dispatchers.IO) {
            repository.getMasterRecordsForDateRange(startMillis, endMillis)
        }
    }

    /**
     * Provides all expenses from master-saved records across all dates.
     * Flattens each CalculationRecord.items into display-ready entries with the record's date.
     */
    fun getAllMasterSavedExpenseDisplayItems(): Flow<List<ExpenseDisplayItem>> {
        return repository.allCalculationRecords
            .map { records ->
                records.asSequence()
                    .filter { it.isMasterSave }
                    .flatMap { record ->
                        val recordDate = record.recordDate.toLocalDate()
                        record.items.asSequence().map { item ->
                            val priceAsDouble = item.price.toDoubleOrNull() ?: 0.0
                            ExpenseDisplayItem(
                                id = item.sourceItemId ?: 0,
                                date = recordDate,
                                description = item.description,
                                quantity = item.quantity,
                                price = priceAsDouble
                            )
                        }
                    }
                    .sortedByDescending { it.date }
                    .toList()
            }
    }

    /**
     * Categories that exist only in master-saved records and are not present in current TodoItems.
     */
    fun getMasterOnlyCategories(): Flow<Set<String>> {
        // Collect master categories keeping original casing but deduping by lowercase
        val masterFlow = repository.allCalculationRecords.map { records ->
            val seen = mutableSetOf<String>()
            records.asSequence()
                .filter { it.isMasterSave }
                .flatMap { it.items.asSequence() }
                .flatMap { (it.categories ?: emptyList()).asSequence() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filter { seen.add(it.lowercase()) }
                .toSet()
        }

        // Collect current categories from both the structured field and parsed text, normalized to lowercase
        val currentFlow = todoItems.map { items ->
            val fromField = items.asSequence().flatMap { (it.categories ?: emptyList()).asSequence() }
            val fromText = items.asSequence().flatMap { parseCategoryInfo(it.text).second.asSequence() }
            (fromField + fromText)
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
        }

        // Return display-cased master categories whose lowercase form is not used in current items
        return masterFlow.combine(currentFlow) { masterDisplay, currentLower ->
            masterDisplay.filter { it.lowercase() !in currentLower }.toSet()
        }
    }

    /**
     * All expenses from master-saved records for a specific category, with record date.
     */
    fun getMasterExpensesByCategory(category: String): Flow<List<ExpenseDisplayItem>> {
        val normalized = category.trim()
        return repository.allCalculationRecords.map { records ->
            records.asSequence()
                .filter { it.isMasterSave }
                .flatMap { record ->
                    val date = record.recordDate.toLocalDate()
                    record.items.asSequence()
                        .filter { it.categories?.any { c -> c.equals(normalized, ignoreCase = true) } == true }
                        .map { item ->
                            val priceAsDouble = item.price.toDoubleOrNull() ?: 0.0
                            ExpenseDisplayItem(
                                id = item.sourceItemId ?: 0,
                                date = date,
                                description = item.description,
                                quantity = item.quantity,
                                price = priceAsDouble
                            )
                        }
                }
                .sortedWith(compareByDescending<ExpenseDisplayItem> { it.date }.thenBy { it.description })
                .toList()
        }
    }
}

private val LocalDate.toEpochMilli: Long
    get() = this.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private val Long.toLocalDate: LocalDate
    get() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
