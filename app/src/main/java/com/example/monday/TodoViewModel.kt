package com.example.monday

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import java.time.ZoneId
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import com.example.monday.parseCategoryInfo
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TodoRepository
    private val _todoItems = MutableStateFlow<List<TodoItem>>(emptyList())
    val todoItems: StateFlow<List<TodoItem>> = _todoItems

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _undoableDeletedItems = MutableStateFlow<List<TodoItem>>(emptyList())
    val undoableDeletedItems: StateFlow<List<TodoItem>> = _undoableDeletedItems
    
    private val imageUpdateMutex = Mutex()

    // StateFlows for categories
    private val _primaryCategories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    val primaryCategories: StateFlow<List<ExpenseCategory>> = _primaryCategories

    private val _secondaryCategories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    val secondaryCategories: StateFlow<List<ExpenseCategory>> = _secondaryCategories

    private val _tertiaryCategories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    val tertiaryCategories: StateFlow<List<ExpenseCategory>> = _tertiaryCategories

    init {
        val todoDao = AppDatabase.getDatabase(application).todoDao()
        repository = TodoRepository(todoDao)
        viewModelScope.launch {
            repository.getTodoItems().collectLatest { items ->
                _todoItems.value = items.sortedBy { it.id }
            }
        }
        // Load categories on init
        loadAllCategories()
    }

    private fun loadAllCategories() {
        _primaryCategories.value = getSavedCategories("primary") ?: com.example.monday.ui.components.DefaultCategories.primaryCategories
        _secondaryCategories.value = getSavedCategories("secondary") ?: com.example.monday.ui.components.DefaultCategories.secondaryCategories
        _tertiaryCategories.value = getSavedCategories("tertiary") ?: com.example.monday.ui.components.DefaultCategories.tertiaryCategories
    }

    fun addItem(item: TodoItem) = viewModelScope.launch {
        repository.insert(item)
    }

    /**
     * Adds an item to the database and returns its ID immediately
     * This is useful when we need to get the ID right away for further operations
     */
    suspend fun addItemAndGetId(item: TodoItem): Int {
        return repository.insertAndGetId(item)
    }

    fun updateItem(updatedItem: TodoItem) = viewModelScope.launch {
        Log.d("CategoryDebug", "Updating item with ID: ${updatedItem.id}")
        Log.d("CategoryDebug", "Categories: ${updatedItem.categories}")
        Log.d("CategoryDebug", "Primary: ${updatedItem.hasPrimaryCategory}, Secondary: ${updatedItem.hasSecondaryCategory}, Tertiary: ${updatedItem.hasTertiaryCategory}")
        repository.update(updatedItem)
    }

    fun removeItem(item: TodoItem) = viewModelScope.launch {
        _undoableDeletedItems.value = listOf(item) + _undoableDeletedItems.value
        repository.delete(item)
    }

    fun deleteSelectedItemsAndEnableUndo(itemsToDelete: List<TodoItem>) = viewModelScope.launch {
        if (itemsToDelete.isEmpty()) return@launch
        _undoableDeletedItems.value = itemsToDelete.reversed() + _undoableDeletedItems.value
        val idsToDelete = itemsToDelete.map { it.id }
        repository.deleteItemsByIds(idsToDelete)
    }

    fun setAllItemsChecked(checked: Boolean) = viewModelScope.launch {
        val currentItems = _todoItems.value
        val updatedItems = currentItems.map { it.copy(isDone = checked) }
        updatedItems.forEach { repository.update(it) }
    }

    fun deleteAllItems() = viewModelScope.launch {
        repository.deleteAll()
        _undoableDeletedItems.value = emptyList()
    }

    fun deleteItemById(itemId: Int) = viewModelScope.launch {
        repository.deleteItemById(itemId)
        _undoableDeletedItems.value = emptyList()
    }

    /**
     * Load record items as current expenses (adds to existing expenses)
     */
    fun loadRecordItemsAsCurrentExpenses(recordItems: List<RecordItem>, targetDate: LocalDate) = viewModelScope.launch {
        Log.d("CategoryFix", "==== MERGE STARTED ====")
        Log.d("CategoryFix", "Total record items: ${recordItems.size}")
        
        // Log categories and images in each record item
        recordItems.forEachIndexed { index, item ->
            Log.d("CategoryFix", "RecordItem #$index: ${item.description}, Categories: ${item.categories}, Images: ${item.imageUris?.size ?: 0}")
        }

        val startOfDayMillis = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Get existing items
        val existingItems = withContext(Dispatchers.IO) {
            repository.getAllItemsForDateRange(startOfDayMillis, startOfDayMillis + 86400000 - 1)
        }
        
        // Create a map of existing items by description+price+quantity for quick lookup
        val existingItemsMap = existingItems.associateBy { 
            val (name, quantity, price) = parseItemText(it.text)
            "$name|$price|${quantity ?: ""}"
        }
        
        Log.d("CategoryFix", "Found ${existingItems.size} existing items")
        
        // Process each record item
        for (recordItem in recordItems) {
            // Generate key for lookup
            val key = "${recordItem.description}|${recordItem.price}|${recordItem.quantity ?: ""}"
            
            // Check if we have a matching item
            val existingItem = existingItemsMap[key]
            
            if (existingItem != null) {
                // Item exists - update its categories and images if needed
                Log.d("CategoryFix", "Found existing item: ${existingItem.text}")
                
                // If the record item has categories/images and they're different from existing ones
                val needsUpdate = (!recordItem.categories.isNullOrEmpty() && existingItem.categories != recordItem.categories) ||
                                  (!recordItem.imageUris.isNullOrEmpty() && existingItem.imageUris != recordItem.imageUris)
                
                if (needsUpdate) {
                    Log.d("CategoryFix", "Updating item: ${existingItem.text}")
                    
                    // Generate the text with categories
                    val itemText = recordItemToTodoItemText(recordItem)
                    
                    val (hasPrimaryCategory, hasSecondaryCategory, hasTertiaryCategory) = determineCategoryTypes(recordItem.categories)
                    
                    // Update the item with new categories and images
                    val updatedItem = existingItem.copy(
                        text = itemText,
                        categories = recordItem.categories,
                        imageUris = recordItem.imageUris,
                        hasPrimaryCategory = hasPrimaryCategory,
                        hasSecondaryCategory = hasSecondaryCategory,
                        hasTertiaryCategory = hasTertiaryCategory
                    )
                    
                    withContext(Dispatchers.IO) {
                        repository.update(updatedItem)
                    }
                    Log.d("CategoryFix", "Updated item with new data")
                } else {
                    Log.d("CategoryFix", "No update needed")
                }
            } else {
                // Item doesn't exist - create a new one
                Log.d("CategoryFix", "Creating new item: ${recordItem.description}")
                
                // Generate the text with categories
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
                
                withContext(Dispatchers.IO) {
                    repository.insert(newItem)
                }
                Log.d("CategoryFix", "Inserted new item")
            }
        }

        _undoableDeletedItems.value = emptyList()
        updateSelectedDate(targetDate)
        Log.d("CategoryFix", "==== MERGE COMPLETED ====")
    }
    
    /**
     * Clear all existing expenses and set only the record items
     */
    fun clearAndSetRecordItems(recordItems: List<RecordItem>, targetDate: LocalDate) = viewModelScope.launch {
        Log.d("CategoryFix", "==== CLEAR AND SET STARTED ====")
        Log.d("CategoryFix", "Total record items: ${recordItems.size}")
        
        // Log categories and images in each record item
        recordItems.forEachIndexed { index, item ->
            Log.d("CategoryFix", "RecordItem #$index: ${item.description}, Categories: ${item.categories}, Images: ${item.imageUris?.size ?: 0}")
        }
        
        val startOfDayMillis = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Step 1: Get the exact items to delete
        val itemsToDelete = withContext(Dispatchers.IO) {
            repository.getAllItemsForDateRange(startOfDayMillis, startOfDayMillis + 86400000 - 1)
        }
        val idsToDelete = itemsToDelete.map { it.id }
        Log.d("CategoryFix", "Found ${idsToDelete.size} items to delete.")

        // Step 2: Perform deletion by IDs
        if (idsToDelete.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                repository.deleteItemsByIds(idsToDelete)
            }
            Log.d("CategoryFix", "Deleted items with IDs: $idsToDelete")
        }

        // Step 3: Prepare and insert new items
        val newTodoItems = recordItems.map {
            val itemText = recordItemToTodoItemText(it)
            val finalCategories = it.categories ?: parseCategoryInfo(itemText).second
            val (hasPrimaryCategory, hasSecondaryCategory, hasTertiaryCategory) = determineCategoryTypes(finalCategories)
            
            TodoItem(
                text = itemText,
                isDone = it.isChecked,
                timestamp = startOfDayMillis,
                categories = finalCategories,
                imageUris = it.imageUris, // Carry over the image URIs
                hasPrimaryCategory = hasPrimaryCategory,
                hasSecondaryCategory = hasSecondaryCategory,
                hasTertiaryCategory = hasTertiaryCategory
            )
        }
        
        // Log the new TodoItems
        newTodoItems.forEachIndexed { index, item ->
            Log.d("CategoryFix", "New TodoItem #$index: Text: ${item.text}, Images: ${item.imageUris?.size ?: 0}")
        }
        
        withContext(Dispatchers.IO) {
            newTodoItems.forEach { repository.insert(it) }
        }
        Log.d("CategoryFix", "Finished inserting ${newTodoItems.size} new items.")

        // Step 4: Update UI state
        _undoableDeletedItems.value = emptyList()
        updateSelectedDate(targetDate)
        Log.d("CategoryFix", "==== CLEAR AND SET COMPLETED ====")
    }
    
    // Helper function to determine category types
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

    /**
     * Convert a TodoItem to display text (for duplicate detection)
     */
    private fun todoItemToDisplayText(todoItem: TodoItem): String {
        return todoItem.text.trim()
    }

    fun undoLastDelete() = viewModelScope.launch {
        if (_undoableDeletedItems.value.isNotEmpty()) {
            val itemToRestore = _undoableDeletedItems.value.first()
            repository.insert(itemToRestore)
            _undoableDeletedItems.value = _undoableDeletedItems.value.drop(1)
        }
    }

    fun clearLastDeletedItem() = viewModelScope.launch {
        _undoableDeletedItems.value = emptyList()
    }

    // CalculationRecord methods
    val allCalculationRecords: kotlinx.coroutines.flow.StateFlow<List<CalculationRecord>> = repository.allCalculationRecords
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertCalculationRecord(record: CalculationRecord) = viewModelScope.launch {
        repository.insertCalculationRecord(record)
    }

    fun getCalculationRecordById(id: Int): kotlinx.coroutines.flow.StateFlow<CalculationRecord?> {
        return repository.getCalculationRecordById(id)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)
    }

    fun deleteCalculationRecord(record: CalculationRecord) = viewModelScope.launch {
        repository.deleteCalculationRecord(record)
    }

    fun deleteCalculationRecordById(recordId: Int) = viewModelScope.launch {
        repository.deleteCalculationRecordById(recordId)
    }

    fun deleteAllCalculationRecords() = viewModelScope.launch {
        repository.deleteAllCalculationRecords()
    }

    fun getCalculationRecordsForDate(date: LocalDate): kotlinx.coroutines.flow.Flow<List<CalculationRecord>> {
        val startOfDayMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDayMillis = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        return repository.getCalculationRecordsForDateRange(startOfDayMillis, endOfDayMillis)
    }

    fun getMasterRecordForDate(date: LocalDate): kotlinx.coroutines.flow.Flow<CalculationRecord?> {
        val startOfDayMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDayMillis = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        Log.d("CalendarDebug", "VM: getMasterRecordForDate for $date (Millis: $startOfDayMillis to $endOfDayMillis)")
        return repository.getMasterSaveRecordForDate(startOfDayMillis, endOfDayMillis)
    }

    // Function to get expenses for a specific date
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

    // Alias for updateSelectedDate for cleaner code
    fun setDate(date: LocalDate) {
        updateSelectedDate(date)
    }

    // Get all expenses for export
    suspend fun getAllExpensesForExport(): List<TodoItem> {
        Log.d("ExportBackup", "Fetching all expenses directly from database")
        return withContext(Dispatchers.IO) {
            val items = repository.getAllItems()
            Log.d("ExportBackup", "Retrieved ${items.size} expenses from database")
            
            // Log items with categories
            val itemsWithCategories = items.filter { !it.categories.isNullOrEmpty() }
            Log.d("ExportBackup", "Found ${itemsWithCategories.size} items with categories")
            
            items
        }
    }

    suspend fun getAllCalculationRecordsForExport(): List<CalculationRecord> {
        Log.d("ExportBackup", "Fetching all calculation records directly from database")
        val records = repository.getAllCalculationRecordsForExport()
        
        // Log records with items that have categories
        val recordsWithCategories = records.filter { record -> 
            record.items.any { !it.categories.isNullOrEmpty() }
        }
        Log.d("ExportBackup", "Found ${recordsWithCategories.size} records containing items with categories")
        
        return records
    }

    fun clearAndInsertAllData(todoItems: List<TodoItem>, calculationRecords: List<CalculationRecord>) = viewModelScope.launch(Dispatchers.IO) {
        Log.d("ImportBackup", "Starting clearAndInsertAllData")
        Log.d("ImportBackup", "Inserting ${todoItems.size} todo items and ${calculationRecords.size} calculation records")
        
        // Log items with categories
        val itemsWithCategories = todoItems.filter { !it.categories.isNullOrEmpty() }
        Log.d("ImportBackup", "Found ${itemsWithCategories.size} items with categories")
        
        // Log records with items that have categories
        val recordsWithCategories = calculationRecords.filter { record -> 
            record.items.any { !it.categories.isNullOrEmpty() }
        }
        Log.d("ImportBackup", "Found ${recordsWithCategories.size} records containing items with categories")
        
        // Execute the database transaction
        repository.clearAndInsertAllData(todoItems, calculationRecords)
        Log.d("ImportBackup", "Completed clearAndInsertAllData")
    }

    /**
     * Gets all TodoItems for a date range directly from the database.
     * This is a suspend function that fetches data synchronously for the batch save process.
     */
    suspend fun getAllItemsForDateRange(startOfDayMillis: Long, endOfDayMillis: Long): List<TodoItem> {
        return repository.getAllItemsForDateRange(startOfDayMillis, endOfDayMillis)
    }
    
    /**
     * Non-suspend version of insertCalculationRecordIfNotDuplicate for compatibility
     */
    fun insertCalculationRecordIfNotDuplicateAsync(record: CalculationRecord) = viewModelScope.launch {
        insertCalculationRecordIfNotDuplicate(record)
    }

    /**
     * Inserts a calculation record only if it's not a duplicate of an existing record.
     * Returns true if a new record was created, false if a duplicate was found.
     */
    suspend fun insertCalculationRecordIfNotDuplicate(record: CalculationRecord): Boolean {
        Log.d("DuplicateCheck", "====== CHECKING FOR DUPLICATES ======")
        
        // Get all existing non-master records for this date
        val recordDate = record.recordDate.toLocalDate()
        val startOfDay = recordDate.toEpochMilli()
        val endOfDay = recordDate.plusDays(1).toEpochMilli() - 1
        
        // Get records directly from the database to avoid any caching issues
        val existingRecords = repository.getAllCalculationRecordsForDateRangeDirect(startOfDay, endOfDay)
            .filter { !it.isMasterSave }
        
        Log.d("DuplicateCheck", "Found ${existingRecords.size} existing non-master records for date: $recordDate")
        
        // Simplify each item to a basic string representation (description + price + quantity)
        val newRecordItems = record.items.map { 
            "${it.description.trim()}|${it.price.trim()}|${it.quantity?.trim() ?: ""}"
        }.sorted()
        
        Log.d("DuplicateCheck", "New record has ${newRecordItems.size} items")
        
        // Check each existing record for duplicate items
        for (existingRecord in existingRecords) {
            val existingItems = existingRecord.items.map { 
                "${it.description.trim()}|${it.price.trim()}|${it.quantity?.trim() ?: ""}"
            }.sorted()
            
            Log.d("DuplicateCheck", "Comparing with record #${existingRecord.id} (${existingItems.size} items)")
            
            // Check if all items match
            if (newRecordItems.size == existingItems.size && newRecordItems == existingItems) {
                Log.d("DuplicateCheck", "DUPLICATE FOUND: Record #${existingRecord.id} has identical items")
                return false
            }
        }
        
        // No duplicate found, insert the new record
        Log.d("DuplicateCheck", "No duplicates found, creating new record")
        repository.insertCalculationRecord(record)
        return true
    }

    /**
     * Saves all expenses for a date to a master save record.
     * If a master save record already exists for this date, it will be updated.
     * Additionally creates a regular save record only if no identical record exists.
     * 
     * @return Pair<Boolean, Boolean> - First boolean indicates if a regular record was created,
     * second indicates if a master record was created or updated
     */
    suspend fun saveToMasterRecord(date: LocalDate, allItems: List<TodoItem>): Pair<Boolean, Boolean> {
        val recordItems = allItems.map { todoItemToRecordItem(it) }
        if (recordItems.isEmpty()) {
            return Pair(false, false)
        }
        
        // Calculate totals
        val totalSum = recordItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
        val checkedItems = recordItems.filter { it.isChecked }
        val checkedItemsCount = checkedItems.size
        val checkedItemsSum = checkedItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
        
        // Variable to track if we created/updated records
        var regularRecordCreated = false
        var masterRecordCreatedOrUpdated = false
        
        // First check if we should create a regular record using our improved duplicate detection
        Log.d("MasterSave", "Checking for existing regular records with identical items")
        
        // Get all existing non-master records for this date directly from DB
        val startOfDayMillis = date.toEpochMilli()
        val endOfDayMillis = date.plusDays(1).toEpochMilli() - 1
        val existingRegularRecords = repository.getAllCalculationRecordsForDateRangeDirect(
            startOfDayMillis, endOfDayMillis
        ).filter { !it.isMasterSave }
        
        Log.d("MasterSave", "Found ${existingRegularRecords.size} existing regular records for date: $date")
        
        // Check if any existing record has identical items
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
        
        // Create regular record only if no duplicate found
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
        
        // Now handle the master record
        val existingMasterRecords = repository.getMasterSaveRecordsForDateRange(
            startOfDayMillis, endOfDayMillis
        )
        
        if (existingMasterRecords.isNotEmpty()) {
            // Update existing master record
            val existingMaster = existingMasterRecords.first()
            Log.d("MasterSave", "Found existing master record #${existingMaster.id}")
            
            // Create new master items list by combining existing and new items
            val updatedMasterItems = mutableListOf<RecordItem>()
            
            // Create maps for efficient lookups
            // 1. Map of existing items by sourceItemId (for direct matches)
            val existingItemsBySourceId = existingMaster.items
                .filter { it.sourceItemId != null }
                .associateBy { it.sourceItemId }
            
            // 2. Map of existing items by name (case-insensitive) for fallback matching
            val existingItemsByName = existingMaster.items
                .groupBy { it.description.trim().lowercase() }
            
            // Track which existing items have been processed
            val processedExistingItems = mutableSetOf<RecordItem>()
            
            // Process each new item
            for (newItem in recordItems) {
                var matchFound = false
                var bestMatchItem: RecordItem? = null
                
                // FIRST MATCHING STRATEGY: Match by sourceItemId (most reliable)
                if (newItem.sourceItemId != null) {
                    bestMatchItem = existingItemsBySourceId[newItem.sourceItemId]
                    if (bestMatchItem != null) {
                        matchFound = true
                        Log.d("MasterSave", "Found match by sourceItemId: ${newItem.sourceItemId} for '${newItem.description}'")
                    }
                }
                
                // SECOND MATCHING STRATEGY: Match by name if no sourceItemId match
                if (!matchFound) {
                    val normalizedName = newItem.description.trim().lowercase()
                    val matchingExistingItems = existingItemsByName[normalizedName]
                    
                    if (matchingExistingItems != null && matchingExistingItems.isNotEmpty()) {
                        // Find the first item that hasn't been processed yet
                        bestMatchItem = matchingExistingItems.firstOrNull { it !in processedExistingItems }
                        if (bestMatchItem != null) {
                            matchFound = true
                            Log.d("MasterSave", "Found match by name: '${newItem.description}'")
                        }
                    }
                }
                
                // THIRD MATCHING STRATEGY: Try fuzzy matching if still no match
                if (!matchFound) {
                    // Try to find a match by price and similar name
                    for (existingItem in existingMaster.items) {
                        // Skip items that have already been processed
                        if (existingItem in processedExistingItems) continue
                        
                        // Check if prices match
                        if (existingItem.price.trim() == newItem.price.trim()) {
                            // Check if names are similar
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
                    // Mark this existing item as processed
                    processedExistingItems.add(bestMatchItem)
                    
                    // Update the item with new values but keep the original sourceItemId if the new one is null
                    val finalSourceItemId = newItem.sourceItemId ?: bestMatchItem.sourceItemId
                    
                    val updatedItem = bestMatchItem.copy(
                        description = newItem.description, // Update the name
                        price = newItem.price,
                        quantity = newItem.quantity,
                        categories = newItem.categories,
                        imageUris = newItem.imageUris,
                        isChecked = newItem.isChecked,
                        sourceItemId = finalSourceItemId // Preserve item identity
                    )
                    
                    Log.d("MasterSave", "Updating existing item: '${bestMatchItem.description}' -> '${newItem.description}' (price: ${bestMatchItem.price} -> ${newItem.price})")
                    updatedMasterItems.add(updatedItem)
                } else {
                    // No match found, add as a new item
                    Log.d("MasterSave", "Adding new item to master record: ${newItem.description}")
                    updatedMasterItems.add(newItem)
                }
            }
            
            // Add any remaining existing items that weren't matched
            val remainingItems = existingMaster.items.filter { it !in processedExistingItems }
            if (remainingItems.isNotEmpty()) {
                Log.d("MasterSave", "Adding ${remainingItems.size} unmatched existing items to master record")
                updatedMasterItems.addAll(remainingItems)
            }
            
            // Recalculate the total sum based on all items
            val updatedTotalSum = updatedMasterItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
            
            // Compare master record items to see if they actually changed
            val masterItemsChanged = !areItemListsIdentical(existingMaster.items, updatedMasterItems)
            
            // Only update if there are changes
            if (masterItemsChanged) {
                val updatedMaster = existingMaster.copy(
                    items = updatedMasterItems,
                    totalSum = updatedTotalSum,
                    timestamp = System.currentTimeMillis() // Update timestamp
                )
                repository.updateCalculationRecord(updatedMaster)
                masterRecordCreatedOrUpdated = true
                Log.d("MasterSave", "Updated existing master record with new items")
            } else {
                Log.d("MasterSave", "No changes to master record, skipping update")
            }
            
        } else {
            // Create new master record with all items
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
    
    /**
     * Helper method to check if two lists of RecordItems have identical contents
     * (ignoring order and comparing description, price, quantity, and categories)
     */
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

    /**
     * Non-suspend version of saveToMasterRecord for compatibility with existing code
     */
    fun saveToMasterRecordAsync(date: LocalDate, allItems: List<TodoItem>) = viewModelScope.launch {
        saveToMasterRecord(date, allItems)
    }

    /**
     * Updates a calculation record in the database
     */
    fun updateCalculationRecord(record: CalculationRecord) = viewModelScope.launch {
        repository.updateCalculationRecord(record)
    }
    
    /**
     * Removes an item at the specified index from a record and returns the updated record
     */
    suspend fun removeRecordItem(record: CalculationRecord, index: Int): CalculationRecord {
        val updatedItems = record.items.toMutableList()
        if (index >= 0 && index < updatedItems.size) {
            updatedItems.removeAt(index)
        }
        
        // Recalculate totals
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
     * Adds a new item to a record and returns the updated record
     */
    suspend fun addRecordItem(
        record: CalculationRecord, 
        description: String, 
        price: String,
        quantity: String?,
        categories: List<String>? = null,
        sourceItemId: Int? = null
    ): CalculationRecord {
        val updatedItems = record.items.toMutableList()
        updatedItems.add(RecordItem(
            description = description,
            price = price,
            quantity = quantity,
            isChecked = false,
            categories = categories,
            sourceItemId = sourceItemId
        ))
        
        // Recalculate totals
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
     * Updates an item at the specified index in a record and returns the updated record
     */
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
            // Preserve categories if not explicitly provided
            val itemCategories = categories ?: updatedItems[index].categories
            
            // Preserve the sourceItemId
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
        
        // Recalculate totals
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
     * Get master record totals for all days in a month to be shown in the calendar.
     * This bypasses all caching mechanisms and directly queries the database.
     */
    suspend fun getMasterRecordTotalsForMonth(yearMonth: YearMonth): Map<String, Double> {
        return withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, Double>()
            try {
                // Calculate start and end of month in milliseconds
                val startOfMonthMillis = yearMonth.atDay(1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                
                val endOfMonthMillis = yearMonth.atDay(yearMonth.lengthOfMonth())
                    .plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                
                Log.d("CalendarFix", "-------------- BEGIN CALENDAR DATA FETCH --------------")
                Log.d("CalendarFix", "Querying for month: $yearMonth ($startOfMonthMillis - $endOfMonthMillis)")
                
                // Step 1: Get ALL calculation records for the month
                val allRecords = repository.getAllCalculationRecordsForDateRangeDirect(
                    startOfMonthMillis, endOfMonthMillis
                )
                
                Log.d("CalendarFix", "Total records found: ${allRecords.size}")
                Log.d("CalendarFix", "Master records: ${allRecords.count { it.isMasterSave }}")
                Log.d("CalendarFix", "Regular records: ${allRecords.count { !it.isMasterSave }}")
                
                // Create a map where key = date string and value = list of records for that date
                val recordsByDate = allRecords.groupBy { record ->
                    val date = Instant.ofEpochMilli(record.recordDate)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    date.toString()
                }
                
                Log.d("CalendarFix", "Dates with records: ${recordsByDate.keys}")
                
                // For each date, take the most recent master record only
                recordsByDate.forEach { (dateStr, records) ->
                    // First, try to find master records
                    val masterRecords = records.filter { it.isMasterSave }
                    
                    if (masterRecords.isNotEmpty()) {
                        // Get the master record with the highest timestamp (most recent)
                        val mostRecentMaster = masterRecords.maxBy { it.timestamp }
                        result[dateStr] = mostRecentMaster.totalSum
                        
                        Log.d("CalendarFix", "✅ DATE: $dateStr - Using MASTER record ID=${mostRecentMaster.id}, " +
                              "totalSum=${mostRecentMaster.totalSum}, timestamp=${mostRecentMaster.timestamp}")
                    } else {
                        // If no master records, don't add anything to the result map
                        // This ensures only master records are shown in the calendar
                        Log.d("CalendarFix", "❌ DATE: $dateStr - No master records found, skipping")
                    }
                }
                
                // Final validation logs
                if (result.isNotEmpty()) {
                    Log.d("CalendarFix", "Final map contains ${result.size} dates with master records")
                    result.forEach { (date, amount) ->
                        Log.d("CalendarFix", "   $date = ₹$amount")
                    }
                } else {
                    Log.d("CalendarFix", "Final map is EMPTY - no master records for month")
                }
                
                Log.d("CalendarFix", "-------------- END CALENDAR DATA FETCH --------------")
            } catch (e: Exception) {
                Log.e("CalendarFix", "Error getting master record totals", e)
            }
            
            return@withContext result
        }
    }

    // Category management methods
    suspend fun saveCategories(type: String, categories: List<ExpenseCategory>) {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("categories_prefs", android.content.Context.MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val json = gson.toJson(categories)
        sharedPreferences.edit().putString("${type}_categories", json).apply()
        // Update the corresponding StateFlow
        when (type) {
            "primary" -> _primaryCategories.value = categories
            "secondary" -> _secondaryCategories.value = categories
            "tertiary" -> _tertiaryCategories.value = categories
        }
    }
    
    fun getSavedCategories(type: String): List<ExpenseCategory>? {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("categories_prefs", android.content.Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("${type}_categories", null) ?: return null
        val gson = com.google.gson.Gson()
        val typeToken = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, ExpenseCategory::class.java).type
        val categories = gson.fromJson<List<ExpenseCategory>>(json, typeToken)
        
        // Create a map of all default icons for easy lookup
        val defaultCategories = when (type) {
            "primary" -> com.example.monday.ui.components.DefaultCategories.primaryCategories
            "secondary" -> com.example.monday.ui.components.DefaultCategories.secondaryCategories
            "tertiary" -> com.example.monday.ui.components.DefaultCategories.tertiaryCategories
            else -> emptyList()
        }
        
        val iconMap = defaultCategories.associate { it.name to it.icon }
        
        // Restore missing icons
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
    
    // Methods to save and retrieve most recently selected categories
    fun saveRecentlySelectedCategory(categoryType: String, categoryName: String) {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("categories_prefs", android.content.Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("recent_${categoryType}", categoryName).apply()
    }
    
    fun getRecentlySelectedCategory(categoryType: String): String? {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("categories_prefs", android.content.Context.MODE_PRIVATE)
        return sharedPreferences.getString("recent_${categoryType}", null)
    }

    /**
     * Ensures there's at least one calculation record for the given date.
     * If no record exists, creates an empty one.
     */
    fun insertEmptyCalculationRecordIfNeeded(date: LocalDate) = viewModelScope.launch(Dispatchers.IO) {
        val startOfDayMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDayMillis = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        
        // Check if any records exist for this date
        val existingRecords = repository.getAllCalculationRecordsForDateRangeDirect(
            startOfDayMillis, endOfDayMillis
        )
        
        // If no records exist, create an empty one
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

    // Add a method to get item by ID directly from the current state
    fun getItemById(id: Int): TodoItem {
        return _todoItems.value.find { it.id == id } ?: throw IllegalArgumentException("Item with ID $id not found")
    }

    fun deleteImageFromItem(item: TodoItem, imageUrl: String) = viewModelScope.launch {
        imageUpdateMutex.withLock {
            Log.d("ImageDeletion", "Mutex locked for deleting $imageUrl")
            Log.d("ImageDeletion", "Starting image deletion for item ID ${item.id}, URL: $imageUrl")

            // Get the latest version of the item from the database to prevent stale data issues
            val currentItem = repository.getItemById(item.id)
            if (currentItem == null) {
                Log.e("ImageDeletion", "Item with ID ${item.id} not found in repository. Aborting deletion.")
                return@withLock
            }

            // Create a mutable copy of image URIs from the LATEST item state
            val updatedUris = currentItem.imageUris?.toMutableList() ?: mutableListOf()
            Log.d("ImageDeletion", "Original image URIs from repo: $updatedUris")

            // Remove the specified URI
            val removed = updatedUris.remove(imageUrl)
            if (!removed) {
                Log.w("ImageDeletion", "Image URL not found in item's list, might have been already deleted: $imageUrl")
            }
            Log.d("ImageDeletion", "Updated image URIs after removal: $updatedUris")

            // Create a new TodoItem with updated URIs
            val updatedItem = currentItem.copy(imageUris = updatedUris)
            Log.d("ImageDeletion", "Updating item ID=${currentItem.id} with new URIs size: ${updatedUris.size}")

            // Update the item in the repository
            repository.update(updatedItem)

            // Also update the in-memory list for immediate UI feedback
            _todoItems.value = _todoItems.value.map {
                if (it.id == currentItem.id) updatedItem else it
            }

            // Delete the actual file only if it was successfully removed from the database record
            if (removed) {
                try {
                    val uri = Uri.parse(imageUrl)
                    Log.d("ImageDeletion", "Parsed URI for file deletion: $uri")
                    
                    // Special handling based on URI scheme
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
            // First update the UI with the temporary URI
            val tempUris = item.imageUris.orEmpty() + imageUri.toString()
            val tempUpdatedItem = item.copy(imageUris = tempUris)
            
            // Update the item immediately with the temporary URI for instant feedback
            _todoItems.value = _todoItems.value.map { 
                if (it.id == item.id) tempUpdatedItem else it 
            }
            
            // Then process the image in the background
            withContext(Dispatchers.IO) {
                val internalUri = copyUriToInternalStorage(getApplication(), imageUri)
                Log.d("ImageDebug", "After copyUriToInternalStorage, result: $internalUri")
                
                if (internalUri != null) {
                    // Get the latest version of the item from the database to avoid conflicts
                    val currentItem = repository.getItemById(item.id)
                    val currentUris = currentItem?.imageUris.orEmpty()
                    Log.d("ImageDebug", "Current image URIs from database: $currentUris")
                    
                    val updatedUris = currentUris + internalUri.toString()
                    Log.d("ImageDebug", "Updated image URIs: $updatedUris")
                    
                    val updatedItem = currentItem?.copy(imageUris = updatedUris) ?: item.copy(imageUris = updatedUris)
                    Log.d("ImageDebug", "Updating item in repository with new URIs")
                    repository.update(updatedItem)
                    
                    // Update the in-memory list with the final URIs
                    withContext(Dispatchers.Main) {
                        _todoItems.value = _todoItems.value.map { 
                            if (it.id == item.id) updatedItem else it 
                        }
                    }
                    Log.d("ImageDebug", "Item updated successfully")
                } else {
                    Log.e("ImageDebug", "Failed to copy URI to internal storage")
                    // Revert the temporary update if we failed
                    withContext(Dispatchers.Main) {
                        _todoItems.value = _todoItems.value.map { 
                            if (it.id == item.id) item else it 
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ImageDebug", "Error in addImageToItem", e)
            // Revert the temporary update if we failed
            _todoItems.value = _todoItems.value.map { 
                if (it.id == item.id) item else it 
            }
        }
    }

    suspend fun updateCategory(oldCategory: ExpenseCategory, newCategory: ExpenseCategory, type: String) {
        // 1. Update all items using the old category name
        withContext(Dispatchers.IO) {
            val allItems = repository.getAllItems()
            val itemsToUpdate = allItems.filter { it.categories?.contains(oldCategory.name) == true }

            for (item in itemsToUpdate) {
                val newCategories = item.categories?.map { if (it == oldCategory.name) newCategory.name else it }
                val updatedItem = item.copy(categories = newCategories)
                repository.update(updatedItem)
            }
        }

        // 2. Update the category list in SharedPreferences
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
        }
    }

    suspend fun deleteCategory(categoryToDelete: ExpenseCategory, type: String) {
        // 1. Remove the category from the list in SharedPreferences
        val currentCategories = when (type) {
            "primary" -> _primaryCategories.value.toMutableList()
            "secondary" -> _secondaryCategories.value.toMutableList()
            "tertiary" -> _tertiaryCategories.value.toMutableList()
            else -> mutableListOf()
        }
        if (currentCategories.remove(categoryToDelete)) {
            saveCategories(type, currentCategories)
        }
    }
}

private val LocalDate.toEpochMilli: Long
    get() = this.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()