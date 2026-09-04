package com.example.monday.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monday.core.cache.ScanMemoryCache
import com.example.monday.MasterSaveHelper
import com.example.monday.core.utils.parseCategoryInfo
import com.example.monday.core.utils.parseItemText
import com.example.monday.core.utils.parsePrice
import com.example.monday.core.utils.recordItemToTodoItemText
import com.example.monday.data.TodoRepository
import com.example.monday.data.models.CartItem
import com.example.monday.data.models.RecordItem
import com.example.monday.data.models.TodoItem
import com.example.monday.managers.CategoryManager
import com.example.monday.managers.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import android.net.Uri
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import com.example.monday.core.utils.copyUriToInternalStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: Application,
    private val repository: TodoRepository,
    val prefManager: PreferenceManager,
    val categoryManager: CategoryManager
) : ViewModel() {

    private val _zone = ZoneId.systemDefault()

    // ── Date Management ────────────────────────────────────────────────
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    fun updateSelectedDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun moveToPreviousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun moveToNextDay() {
        _selectedDate.value = _selectedDate.value.plusDays(1)
    }

    // ── Expense Data Sources ───────────────────────────────────────────
    private val _todoItems = MutableStateFlow<List<TodoItem>>(emptyList())
    val todoItems: StateFlow<List<TodoItem>> = _todoItems

    init {
        viewModelScope.launch {
            try {
                repository.getTodoItems().collectLatest { items ->
                    _todoItems.value = items.sortedBy { it.id }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("KharchaJi", "Error collecting todo items", e)
                _todoItems.value = emptyList()
            }
        }
    }

    val uniqueItemNames: StateFlow<List<String>> = _todoItems
        .map { items ->
            items.map { parseItemText(it.text).first }
                .distinct()
                .filter { it.isNotBlank() }
                .sortedBy { it.lowercase() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val expensesForSelectedDate: StateFlow<List<TodoItem>> = combine(
        _selectedDate, _todoItems
    ) { date, allItems ->
        date to allItems
    }.mapLatest { (date, allItems) ->
        val start = date.atStartOfDay(_zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(_zone).toInstant().toEpochMilli() - 1
        allItems.filter { it.timestamp in start..end }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    val dailySpendMapForSelectedDate: StateFlow<Map<LocalDate, Double>> = combine(
        _selectedDate, _todoItems
    ) { date, allItems ->
        date to allItems
    }.mapLatest { (centerDate, allItems) ->
        val windowDays = 15
        val rangeStart = centerDate.minusDays(windowDays.toLong())
        val rangeEnd = centerDate.plusDays(windowDays.toLong())
        val startMillis = rangeStart.atStartOfDay(_zone).toInstant().toEpochMilli()
        val endMillis = rangeEnd.plusDays(1).atStartOfDay(_zone).toInstant().toEpochMilli() - 1

        val bounded = if (allItems.size > 5000) {
            Log.w("KharchaJi", "dailySpendMap: truncating ${allItems.size} items to 5000")
            allItems.takeLast(5000)
        } else allItems

        bounded
            .filter { it.timestamp in startMillis..endMillis }
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(_zone).toLocalDate() }
            .mapValues { (_, items) -> items.sumOf { parsePrice(it.text) } }
            .filter { it.value > 0.0 }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val monthlySpendMapForSelectedDate: StateFlow<Map<LocalDate, Double>> = combine(
        _selectedDate, _todoItems
    ) { date, allItems ->
        date to allItems
    }.mapLatest { (targetDate, allItems) ->
        val firstDay = targetDate.withDayOfMonth(1)
        val lastDay = targetDate.withDayOfMonth(targetDate.lengthOfMonth())
        val startMillis = firstDay.atStartOfDay(_zone).toInstant().toEpochMilli()
        val endMillis = lastDay.atTime(23, 59, 59).atZone(_zone).toInstant().toEpochMilli()

        allItems
            .filter { it.timestamp in startMillis..endMillis }
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(_zone).toLocalDate() }
            .mapValues { (_, items) -> items.sumOf { parsePrice(it.text) } }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ── CRUD Operations ─────────────────────────────────────────────────
    fun addItem(item: TodoItem) = viewModelScope.launch(Dispatchers.IO) {
        try {
            repository.insert(item)

            val autoMasterSave = prefManager.getPaymentMonitorSetting("auto_master_save") ?: false
            if (autoMasterSave) {
                MasterSaveHelper.appendToMasterAsync(application, item)
            }
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error adding item: ${item.text}", e)
        }
    }

    suspend fun addItemAndGetId(item: TodoItem): Int {
        val insertedId = repository.insertAndGetId(item)
        
        val autoMasterSave = prefManager.getPaymentMonitorSetting("auto_master_save") ?: false
        if (autoMasterSave) {
            val itemWithId = item.copy(id = insertedId)
            MasterSaveHelper.appendToMasterAsync(application, itemWithId)
        }
        
        return insertedId
    }

    fun updateItem(updatedItem: TodoItem) = viewModelScope.launch(Dispatchers.IO) {
        try {
            repository.update(updatedItem)
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error updating item: ${updatedItem.id}", e)
        }
    }

    fun updateItems(items: List<TodoItem>) = viewModelScope.launch(Dispatchers.IO) {
        try {
            repository.updateItems(items)
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error updating items", e)
        }
    }

    fun removeItem(item: TodoItem) = viewModelScope.launch(Dispatchers.IO) {
        try {
            addDeletedItemForCurrentDate(item)
            syncCacheAfterDeletion(listOf(item))
            repository.delete(item)
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error removing item: ${item.text}", e)
        }
    }

    fun deleteSelectedItemsAndEnableUndo(itemsToDelete: List<TodoItem>) = viewModelScope.launch(Dispatchers.IO) {
        try {
            if (itemsToDelete.isEmpty()) return@launch
            addDeletedItemsForCurrentDate(itemsToDelete)
            syncCacheAfterDeletion(itemsToDelete)
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
        ScanMemoryCache.clear()
        _undoableDeletedItemsByDate.value = emptyMap()
    }

    fun deleteItemById(itemId: Int) = viewModelScope.launch(Dispatchers.IO) {
        val item = repository.getItemById(itemId)
        if (item != null) syncCacheAfterDeletion(listOf(item))
        repository.deleteItemById(itemId)
        val updatedMap = _undoableDeletedItemsByDate.value.mapValues { (_, items) ->
            items.filter { it.id != itemId }
        }.filter { (_, items) -> items.isNotEmpty() }
        _undoableDeletedItemsByDate.value = updatedMap
    }

    private val imageUpdateMutex = Mutex()

    fun deleteImageFromItem(item: TodoItem, imageUrl: String) = viewModelScope.launch {
        imageUpdateMutex.withLock {
            val currentItem = repository.getItemById(item.id)
            if (currentItem == null) {
                Log.e("ImageDeletion", "Item with ID ${item.id} not found in repository. Aborting deletion.")
                return@withLock
            }
            val updatedUris = currentItem.imageUris?.toMutableList() ?: mutableListOf()
            val removed = updatedUris.remove(imageUrl)
            if (!removed) {
                Log.w("ImageDeletion", "Image URL not found in item's list, might have been already deleted: $imageUrl")
            }
            val updatedItem = currentItem.copy(imageUris = updatedUris)
            repository.update(updatedItem)
            
            if (removed) {
                try {
                    val uri = Uri.parse(imageUrl)
                    when (uri.scheme) {
                        "content" -> {
                            val contentResolver = application.contentResolver
                            try {
                                contentResolver.delete(uri, null, null)
                            } catch (e: Exception) {
                                Log.e("ImageDeletion", "Error deleting via ContentResolver", e)
                            }
                        }
                        "file" -> {
                            val file = File(uri.path ?: "")
                            if (file.exists()) {
                                file.delete()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ImageDeletion", "Error deleting image file", e)
                }
            }
        }
    }

    fun addImageToItem(item: TodoItem, imageUri: Uri) = viewModelScope.launch {
        try {
            val tempUris = item.imageUris.orEmpty() + imageUri.toString()
            val tempUpdatedItem = item.copy(imageUris = tempUris)
            
            withContext(Dispatchers.IO) {
                val internalUri = copyUriToInternalStorage(application, imageUri)
                if (internalUri != null) {
                    val currentItem = repository.getItemById(item.id)
                    val currentUris = currentItem?.imageUris.orEmpty()
                    val updatedUris = currentUris + internalUri.toString()
                    val updatedItem = currentItem?.copy(imageUris = updatedUris) ?: item.copy(imageUris = updatedUris)
                    repository.update(updatedItem)
                } else {
                    Log.e("ImageDebug", "Failed to copy URI to internal storage")
                }
            }
        } catch (e: Exception) {
            Log.e("ImageDebug", "Error in addImageToItem", e)
        }
    }

    // ── Undo Operations ─────────────────────────────────────────────────
    private val _undoableDeletedItemsByDate = MutableStateFlow<Map<LocalDate, List<TodoItem>>>(emptyMap())

    private val _currentDateUndoItems = MutableStateFlow<List<TodoItem>>(emptyList())
    val undoableDeletedItems: StateFlow<List<TodoItem>> = _currentDateUndoItems

    private val _undoRestoredItemEvent = MutableSharedFlow<Int>(replay = 0)
    val undoRestoredItemEvent: SharedFlow<Int> = _undoRestoredItemEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                combine(_undoableDeletedItemsByDate, _selectedDate) { itemsByDate, date ->
                    itemsByDate[date] ?: emptyList()
                }.collect { items ->
                    _currentDateUndoItems.value = items
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("KharchaJi", "Error collecting undo items", e)
                _currentDateUndoItems.value = emptyList()
            }
        }
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
                
                _undoRestoredItemEvent.emit(itemToRestore.id)
            }
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error undoing last delete", e)
        }
    }

    fun clearLastDeletedItem() = viewModelScope.launch {
        try {
            val currentDate = _selectedDate.value
            val updatedMap = _undoableDeletedItemsByDate.value.toMutableMap()
            updatedMap.remove(currentDate)
            _undoableDeletedItemsByDate.value = updatedMap
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error clearing last deleted item", e)
        }
    }

    private fun syncCacheAfterDeletion(items: List<TodoItem>) {
        val cartItemsToRemove = items.mapNotNull { item ->
            try {
                val (name, quantityStr, priceStr) = parseItemText(item.text)
                val quantity = quantityStr?.filter { it.isDigit() }?.toIntOrNull() ?: 1
                val price = priceStr.replace(",", "").toDoubleOrNull() ?: 0.0
                if (price > 0) CartItem(name.trim(), price, quantity) else null
            } catch (e: Exception) {
                null
            }
        }
        if (cartItemsToRemove.isNotEmpty()) {
            ScanMemoryCache.removeItems(cartItemsToRemove)
        }
    }

    // ── Utility Queries ───────────────────────────────────────────────
    fun getExpensesForDate(date: LocalDate): StateFlow<List<TodoItem>> {
        val startOfDayMillis = date.atStartOfDay(_zone).toInstant().toEpochMilli()
        val endOfDayMillis = date.plusDays(1).atStartOfDay(_zone).toInstant().toEpochMilli() - 1
        return _todoItems.map { allItems ->
            allItems.filter { it.timestamp in startOfDayMillis..endOfDayMillis }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun getExpensesForCurrentMonthUpToDate(currentDate: LocalDate): StateFlow<List<TodoItem>> {
        val firstDayOfMonth = currentDate.withDayOfMonth(1)
        val startOfMonthMillis = firstDayOfMonth.atStartOfDay(_zone).toInstant().toEpochMilli()
        val endOfCurrentDateMillis = currentDate.plusDays(1).atStartOfDay(_zone).toInstant().toEpochMilli() - 1
        return _todoItems.map { allItems ->
            allItems.filter { it.timestamp in startOfMonthMillis..endOfCurrentDateMillis }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun getDailySpendMap(centerDate: LocalDate, windowDays: Int = 15): StateFlow<Map<LocalDate, Double>> {
        val rangeStart = centerDate.minusDays(windowDays.toLong())
        val rangeEnd = centerDate.plusDays(windowDays.toLong())
        val startMillis = rangeStart.atStartOfDay(_zone).toInstant().toEpochMilli()
        val endMillis = rangeEnd.plusDays(1).atStartOfDay(_zone).toInstant().toEpochMilli() - 1

        return _todoItems.map { allItems ->
            val bounded = if (allItems.size > 5000) {
                allItems.takeLast(5000)
            } else allItems

            bounded
                .filter { it.timestamp in startMillis..endMillis }
                .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(_zone).toLocalDate() }
                .mapValues { (_, items) -> items.sumOf { parsePrice(it.text) } }
                .filter { it.value > 0.0 }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    }

    fun getMonthlySpendMap(targetDate: LocalDate): StateFlow<Map<LocalDate, Double>> {
        val firstDay = targetDate.withDayOfMonth(1)
        val lastDay = targetDate.withDayOfMonth(targetDate.lengthOfMonth())
        val startMillis = firstDay.atStartOfDay(_zone).toInstant().toEpochMilli()
        val endMillis = lastDay.atTime(23, 59, 59).atZone(_zone).toInstant().toEpochMilli()

        return _todoItems.map { allItems ->
            allItems
                .filter { it.timestamp in startMillis..endMillis }
                .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(_zone).toLocalDate() }
                .mapValues { (_, items) -> items.sumOf { parsePrice(it.text) } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
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
        return repository.getAllItems()
    }

    suspend fun getAllItemsCached(): List<TodoItem> = repository.getAllItems()

    fun clearAndInsertAllData(todoItems: List<TodoItem>, calculationRecords: List<com.example.monday.data.models.CalculationRecord>) = viewModelScope.launch(Dispatchers.IO) {
        repository.clearAndInsertAllData(todoItems, calculationRecords)
    }

    fun mergeAllData(todoItems: List<TodoItem>, calculationRecords: List<com.example.monday.data.models.CalculationRecord>) = viewModelScope.launch(Dispatchers.IO) {
        repository.mergeAllData(todoItems, calculationRecords)
    }

    suspend fun getAllItemsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): List<TodoItem> {
        return withContext(Dispatchers.IO) {
            repository.getAllItemsForDateRange(startOfDayMillis, endOfDayMillis)
        }
    }

    suspend fun getItemsForMonthRange(startMillis: Long, endMillis: Long): List<TodoItem> {
        return withContext(Dispatchers.IO) {
            repository.getAllItemsForDateRange(startMillis, endMillis)
        }
    }

    fun getItemById(id: Int): TodoItem {
        return _todoItems.value.find { it.id == id } ?: throw IllegalArgumentException("Item with ID $id not found")
    }

    fun hasUndoableItemsForCurrentDate(): Boolean {
        val currentDate = _selectedDate.value
        return (_undoableDeletedItemsByDate.value[currentDate]?.isNotEmpty() == true)
    }

    fun getUndoableDeletedItemsByDate(): Map<LocalDate, List<TodoItem>> {
        return _undoableDeletedItemsByDate.value
    }

    fun restoreUndoableDeletedItemsByDate(undoableItemsByDate: Map<LocalDate, List<TodoItem>>) = viewModelScope.launch(Dispatchers.IO) {
        _undoableDeletedItemsByDate.value = undoableItemsByDate
    }

    fun loadRecordItemsAsCurrentExpenses(recordItems: List<RecordItem>, targetDate: LocalDate) = viewModelScope.launch(Dispatchers.IO) {
        val startOfDayMillis = targetDate.atStartOfDay(_zone).toInstant().toEpochMilli()
        val existingItems = repository.getAllItemsForDateRange(startOfDayMillis, startOfDayMillis + 86400000 - 1)
        val existingItemsMap = existingItems.associateBy {
            val (name, quantity, price) = parseItemText(it.text)
            "$name|$price|${quantity ?: ""}"
        }
        val batchUpdates = mutableListOf<TodoItem>()
        val batchInserts = mutableListOf<TodoItem>()
        for (recordItem in recordItems) {
            val key = "${recordItem.description}|${recordItem.price}|${recordItem.quantity ?: ""}"
            val existingItem = existingItemsMap[key]
            if (existingItem != null) {
                val needsUpdate = (!recordItem.categories.isNullOrEmpty() && existingItem.categories != recordItem.categories) ||
                        (!recordItem.imageUris.isNullOrEmpty() && existingItem.imageUris != recordItem.imageUris)
                if (needsUpdate) {
                    val itemText = recordItemToTodoItemText(recordItem)
                    val (hasPrimaryCategory, hasSecondaryCategory, hasTertiaryCategory) = categoryManager.determineCategoryTypes(recordItem.categories)
                    batchUpdates.add(existingItem.copy(
                        text = itemText,
                        categories = recordItem.categories,
                        imageUris = recordItem.imageUris,
                        hasPrimaryCategory = hasPrimaryCategory,
                        hasSecondaryCategory = hasSecondaryCategory,
                        hasTertiaryCategory = hasTertiaryCategory
                    ))
                }
            } else {
                val itemText = recordItemToTodoItemText(recordItem)
                val finalCategories = recordItem.categories ?: parseCategoryInfo(itemText).second
                val (hasPrimaryCategory, hasSecondaryCategory, hasTertiaryCategory) = categoryManager.determineCategoryTypes(finalCategories)
                batchInserts.add(TodoItem(
                    text = itemText,
                    isDone = recordItem.isChecked,
                    timestamp = targetDate.atTime(java.time.LocalTime.now()).atZone(_zone).toInstant().toEpochMilli(),
                    categories = finalCategories,
                    imageUris = recordItem.imageUris,
                    hasPrimaryCategory = hasPrimaryCategory,
                    hasSecondaryCategory = hasSecondaryCategory,
                    hasTertiaryCategory = hasTertiaryCategory
                ))
            }
        }
        if (batchUpdates.isNotEmpty()) repository.updateItems(batchUpdates)
        if (batchInserts.isNotEmpty()) repository.insertItems(batchInserts)
        _undoableDeletedItemsByDate.value = _undoableDeletedItemsByDate.value.mapValues { it.value.filter { it.id != -1 } }
        updateSelectedDate(targetDate)
    }

    fun clearAndSetRecordItems(recordItems: List<RecordItem>, targetDate: LocalDate) = viewModelScope.launch(Dispatchers.IO) {
        val startOfDayMillis = targetDate.atStartOfDay(_zone).toInstant().toEpochMilli()
        val itemsToDelete = repository.getAllItemsForDateRange(startOfDayMillis, startOfDayMillis + 86400000 - 1)
        val idsToDelete = itemsToDelete.map { it.id }
        if (idsToDelete.isNotEmpty()) {
            repository.deleteItemsByIds(idsToDelete)
        }
        val newTodoItems = recordItems.map {
            val itemText = recordItemToTodoItemText(it)
            val finalCategories = it.categories ?: parseCategoryInfo(itemText).second
            val (hasPrimaryCategory, hasSecondaryCategory, hasTertiaryCategory) = categoryManager.determineCategoryTypes(finalCategories)
            TodoItem(
                text = itemText,
                isDone = it.isChecked,
                timestamp = targetDate.atTime(java.time.LocalTime.now()).atZone(_zone).toInstant().toEpochMilli(),
                categories = finalCategories,
                imageUris = it.imageUris,
                hasPrimaryCategory = hasPrimaryCategory,
                hasSecondaryCategory = hasSecondaryCategory,
                hasTertiaryCategory = hasTertiaryCategory
            )
        }
        repository.insertItems(newTodoItems)
        val targetDateMap = _undoableDeletedItemsByDate.value.toMutableMap()
        targetDateMap.remove(targetDate)
        _undoableDeletedItemsByDate.value = targetDateMap
        updateSelectedDate(targetDate)
    }
}
