package com.example.monday.managers

import android.util.Log
import com.example.monday.ExpenseCategory
import com.example.monday.data.TodoRepository
import com.example.monday.data.models.CalculationRecord
import com.example.monday.data.models.TodoItem
import com.example.monday.core.utils.parseCategoryInfo
import com.example.monday.core.utils.toLocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/**
 * Owns all category CRUD, queries, undo operations, and type determination.
 * Extracted from TodoViewModel to reduce its size by ~400 lines.
 *
 * Dependencies: TodoRepository (DB access), PreferenceManager (category persistence),
 * category StateFlows (owned by this manager, exposed to ViewModel).
 */
class CategoryManager(
    private val repository: TodoRepository,
    private val prefManager: PreferenceManager
) {
    // Category StateFlows — single source of truth for category lists
    private val _primaryCategories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    val primaryCategories: StateFlow<List<ExpenseCategory>> = _primaryCategories

    private val _secondaryCategories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    val secondaryCategories: StateFlow<List<ExpenseCategory>> = _secondaryCategories

    private val _tertiaryCategories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    val tertiaryCategories: StateFlow<List<ExpenseCategory>> = _tertiaryCategories

    // Undo state for category actions
    private val _lastCategoryAction = MutableStateFlow<CategoryAction?>(null)
    val lastCategoryAction: StateFlow<CategoryAction?> = _lastCategoryAction

    sealed class CategoryAction {
        data class Added(val category: ExpenseCategory, val type: String) : CategoryAction()
        data class Edited(val oldCategory: ExpenseCategory, val newCategory: ExpenseCategory, val type: String) : CategoryAction()
        data class Deleted(val category: ExpenseCategory, val type: String, val affectedItems: List<TodoItem>, val affectedRecords: List<CalculationRecord>) : CategoryAction()
        data class Moved(val category: ExpenseCategory, val type: String, val oldPosition: Int, val newPosition: Int) : CategoryAction()
    }

    /** Load categories from SharedPreferences into StateFlows on init. */
    fun loadAllCategories() {
        _primaryCategories.value = prefManager.getSavedCategories("primary") ?: com.example.monday.ui.components.DefaultCategories.primaryCategories
        _secondaryCategories.value = prefManager.getSavedCategories("secondary") ?: com.example.monday.ui.components.DefaultCategories.secondaryCategories
        _tertiaryCategories.value = prefManager.getSavedCategories("tertiary") ?: com.example.monday.ui.components.DefaultCategories.tertiaryCategories
    }

    /**
     * Determine which category tiers an item belongs to.
     * Uses cached StateFlow values — no JSON deserialization per call.
     */
    fun determineCategoryTypes(categories: List<String>?): Triple<Boolean, Boolean, Boolean> {
        if (categories.isNullOrEmpty()) return Triple(false, false, false)
        val primaryNames = _primaryCategories.value.map { it.name }
        val secondaryNames = _secondaryCategories.value.map { it.name }
        val tertiaryNames = _tertiaryCategories.value.map { it.name }
        val hasPrimary = categories.any { it in primaryNames }
        val hasSecondary = categories.any { it in secondaryNames }
        val hasTertiary = categories.any { it in tertiaryNames }
        val hasUnknown = categories.isNotEmpty() && !hasPrimary && !hasSecondary && !hasTertiary
        return Triple(hasPrimary || hasUnknown, hasSecondary, hasTertiary)
    }

    // ── Save / Get wrappers (update StateFlow after persisting) ──────

    suspend fun saveCategories(type: String, categories: List<ExpenseCategory>) {
        prefManager.saveCategories(type, categories)
        when (type) {
            "primary" -> _primaryCategories.value = categories
            "secondary" -> _secondaryCategories.value = categories
            "tertiary" -> _tertiaryCategories.value = categories
        }
    }

    fun getSavedCategories(type: String): List<ExpenseCategory>? = prefManager.getSavedCategories(type)

    // ── Category CRUD ────────────────────────────────────────────────

    suspend fun addCategory(category: ExpenseCategory, type: String) {
        val currentCategories = getCategoryListByType(type).toMutableList()
        currentCategories.add(category)
        saveCategories(type, currentCategories)
        _lastCategoryAction.value = CategoryAction.Added(category, type)
    }

    suspend fun updateCategory(oldCategory: ExpenseCategory, newCategory: ExpenseCategory, type: String) {
        _lastCategoryAction.value = CategoryAction.Edited(oldCategory, newCategory, type)
        withContext(Dispatchers.IO) {
            val allItems = repository.getAllItems()
            val itemsToUpdate = allItems.filter { it.categories?.contains(oldCategory.name) == true }
            val batchUpdatedItems = itemsToUpdate.map { item ->
                val newCategories = item.categories?.map { if (it == oldCategory.name) newCategory.name else it }
                item.copy(categories = newCategories)
            }
            if (batchUpdatedItems.isNotEmpty()) repository.updateItems(batchUpdatedItems)

            val allRecords = repository.getAllCalculationRecordsForExport()
            val batchUpdatedRecords = mutableListOf<CalculationRecord>()
            for (record in allRecords) {
                var needsUpdate = false
                val updatedItems = record.items.map { recordItem ->
                    if (recordItem.categories?.contains(oldCategory.name) == true) {
                        needsUpdate = true
                        recordItem.copy(categories = recordItem.categories.map { if (it == oldCategory.name) newCategory.name else it })
                    } else recordItem
                }
                if (needsUpdate) batchUpdatedRecords.add(record.copy(items = updatedItems))
            }
            if (batchUpdatedRecords.isNotEmpty()) repository.updateCalculationRecords(batchUpdatedRecords)
        }
        val currentCategories = getCategoryListByType(type).toMutableList()
        val index = currentCategories.indexOf(oldCategory)
        if (index != -1) {
            currentCategories[index] = newCategory
            saveCategories(type, currentCategories)
        }
    }

    suspend fun deleteCategory(categoryToDelete: ExpenseCategory, type: String) {
        val affectedItems = mutableListOf<TodoItem>()
        val affectedRecords = mutableListOf<CalculationRecord>()
        withContext(Dispatchers.IO) {
            val allItems = repository.getAllItems()
            val itemsToUpdate = allItems.filter { it.categories?.contains(categoryToDelete.name) == true }
            affectedItems.addAll(itemsToUpdate)
            val batchUpdatedItems = itemsToUpdate.map { item ->
                val updatedCategories = item.categories?.filter { it != categoryToDelete.name }
                val (hasPrimary, hasSecondary, hasTertiary) = determineCategoryTypes(updatedCategories)
                item.copy(categories = updatedCategories, hasPrimaryCategory = hasPrimary, hasSecondaryCategory = hasSecondary, hasTertiaryCategory = hasTertiary)
            }
            if (batchUpdatedItems.isNotEmpty()) repository.updateItems(batchUpdatedItems)

            val allRecords = repository.getAllCalculationRecordsForExport()
            val batchUpdatedRecords = mutableListOf<CalculationRecord>()
            for (record in allRecords) {
                var needsUpdate = false
                val updatedItems = record.items.map { recordItem ->
                    if (recordItem.categories?.contains(categoryToDelete.name) == true) {
                        if (!needsUpdate) affectedRecords.add(record)
                        needsUpdate = true
                        recordItem.copy(categories = recordItem.categories.filter { it != categoryToDelete.name }.ifEmpty { null })
                    } else recordItem
                }
                if (needsUpdate) batchUpdatedRecords.add(record.copy(items = updatedItems))
            }
            if (batchUpdatedRecords.isNotEmpty()) repository.updateCalculationRecords(batchUpdatedRecords)
        }
        val currentCategories = getCategoryListByType(type).toMutableList()
        if (currentCategories.remove(categoryToDelete)) {
            saveCategories(type, currentCategories)
            _lastCategoryAction.value = CategoryAction.Deleted(categoryToDelete, type, affectedItems, affectedRecords)
        }
    }

    suspend fun deleteCategoryWithOptions(categoryToDelete: ExpenseCategory, type: String, removeFromExpenses: Boolean) {
        val affectedItems = mutableListOf<TodoItem>()
        val affectedRecords = mutableListOf<CalculationRecord>()
        if (removeFromExpenses) {
            withContext(Dispatchers.IO) {
                val allItems = repository.getAllItems()
                val itemsToUpdate = allItems.filter { it.categories?.contains(categoryToDelete.name) == true }
                for (item in itemsToUpdate) {
                    affectedItems.add(item)
                    val updatedCategories = item.categories?.filter { it != categoryToDelete.name }
                    val (hasPrimary, hasSecondary, hasTertiary) = determineCategoryTypes(updatedCategories)
                    repository.update(item.copy(categories = updatedCategories, hasPrimaryCategory = hasPrimary, hasSecondaryCategory = hasSecondary, hasTertiaryCategory = hasTertiary))
                }
                val allRecords = repository.getAllCalculationRecordsForExport()
                for (record in allRecords) {
                    var needsUpdate = false
                    val updatedItems = record.items.map { recordItem ->
                        if (recordItem.categories?.contains(categoryToDelete.name) == true) {
                            if (!needsUpdate) affectedRecords.add(record)
                            needsUpdate = true
                            recordItem.copy(categories = recordItem.categories.filter { it != categoryToDelete.name }.ifEmpty { null })
                        } else recordItem
                    }
                    if (needsUpdate) repository.updateCalculationRecord(record.copy(items = updatedItems))
                }
            }
        }
        val currentCategories = getCategoryListByType(type).toMutableList()
        if (currentCategories.remove(categoryToDelete)) {
            saveCategories(type, currentCategories)
            _lastCategoryAction.value = CategoryAction.Deleted(categoryToDelete, type, affectedItems, affectedRecords)
        }
    }

    suspend fun moveCategory(category: ExpenseCategory, type: String, newPosition: Int) {
        val currentCategories = getCategoryListByType(type).toMutableList()
        val currentPosition = currentCategories.indexOf(category)
        if (currentPosition == -1 || currentPosition == newPosition || newPosition < 0 || newPosition >= currentCategories.size) return
        _lastCategoryAction.value = CategoryAction.Moved(category, type, currentPosition, newPosition)
        currentCategories.removeAt(currentPosition)
        currentCategories.add(newPosition, category)
        saveCategories(type, currentCategories)
    }

    suspend fun moveCategoryTo(type: String, fromIndex: Int, toIndex: Int) {
        val currentCategories = getCategoryListByType(type).toMutableList()
        if (fromIndex < 0 || fromIndex >= currentCategories.size || toIndex < 0 || toIndex >= currentCategories.size || fromIndex == toIndex) return
        
        val category = currentCategories[fromIndex]
        _lastCategoryAction.value = CategoryAction.Moved(category, type, fromIndex, toIndex)
        
        currentCategories.removeAt(fromIndex)
        currentCategories.add(toIndex, category)
        saveCategories(type, currentCategories)
    }

    // ── Undo ─────────────────────────────────────────────────────────

    suspend fun undoLastCategoryAction() {
        val action = _lastCategoryAction.value ?: return
        when (action) {
            is CategoryAction.Added -> {
                val cats = getCategoryListByType(action.type).toMutableList()
                cats.remove(action.category)
                saveCategories(action.type, cats)
            }
            is CategoryAction.Edited -> {
                updateCategory(action.newCategory, action.oldCategory, action.type)
            }
            is CategoryAction.Deleted -> {
                val cats = getCategoryListByType(action.type).toMutableList()
                cats.add(action.category)
                saveCategories(action.type, cats)
                withContext(Dispatchers.IO) {
                    for (item in action.affectedItems) {
                        val updatedCategories = (item.categories ?: emptyList()) + action.category.name
                        val (hasPrimary, hasSecondary, hasTertiary) = determineCategoryTypes(updatedCategories)
                        repository.update(item.copy(categories = updatedCategories, hasPrimaryCategory = hasPrimary, hasSecondaryCategory = hasSecondary, hasTertiaryCategory = hasTertiary))
                    }
                    for (record in action.affectedRecords) {
                        var needsUpdate = false
                        val updatedItems = record.items.map { recordItem ->
                            needsUpdate = true
                            recordItem.copy(categories = (recordItem.categories ?: emptyList()) + action.category.name)
                        }
                        if (needsUpdate) repository.updateCalculationRecord(record.copy(items = updatedItems))
                    }
                }
            }
            is CategoryAction.Moved -> {
                val cats = getCategoryListByType(action.type).toMutableList()
                if (cats.remove(action.category)) {
                    val targetPosition = if (action.oldPosition < cats.size) action.oldPosition else cats.size
                    cats.add(targetPosition, action.category)
                    saveCategories(action.type, cats)
                }
            }
        }
        _lastCategoryAction.value = null
    }

    fun setLastCategoryAction(action: CategoryAction) {
        _lastCategoryAction.value = action
    }

    // ── Query functions ──────────────────────────────────────────────

    suspend fun getUncategorizedExpenses(): List<TodoItem> {
        return repository.getAllItems().filter { it.categories.isNullOrEmpty() }
    }

    suspend fun getExpensesWithLessThanThreeCategories(): List<TodoItem> {
        return repository.getAllItems().filter { !it.categories.isNullOrEmpty() && it.categories!!.size < 3 }
    }

    suspend fun getExpensesWithMoreThanThreeCategories(): List<TodoItem> {
        return repository.getAllItems().filter { !it.categories.isNullOrEmpty() && it.categories!!.size > 3 }
    }

    suspend fun getExpensesWithExactlyThreeCategories(): List<TodoItem> {
        return repository.getAllItems().filter { !it.categories.isNullOrEmpty() && it.categories!!.size == 3 }
    }

    suspend fun getAllUniqueCategories(): List<String> {
        return repository.getAllItems().mapNotNull { it.categories }.flatten().distinct().sorted()
    }

    suspend fun getPrimaryCategories(): List<String> {
        return try {
            val primaryCats = getSavedCategories("primary")?.map { it.name } ?: return emptyList()
            val usedCategories = repository.getAllItems().mapNotNull { it.categories }.flatten().distinct()
            usedCategories.filter { primaryCats.contains(it) }.sorted()
        } catch (e: Exception) {
            Log.e("Categories", "Error fetching primary categories", e)
            emptyList()
        }
    }

    suspend fun getAllCategoriesByType(): Map<String, List<String>> {
        val primaryCats = getSavedCategories("primary")?.map { it.name } ?: emptyList()
        val secondaryCats = getSavedCategories("secondary")?.map { it.name } ?: emptyList()
        val tertiaryCats = getSavedCategories("tertiary")?.map { it.name } ?: emptyList()
        val usedCategories = repository.getAllItems().mapNotNull { it.categories }.flatten().distinct()
        return mapOf(
            "primary" to usedCategories.filter { primaryCats.contains(it) }.sorted(),
            "secondary" to usedCategories.filter { secondaryCats.contains(it) }.sorted(),
            "tertiary" to usedCategories.filter { tertiaryCats.contains(it) }.sorted(),
            "other" to usedCategories.filter { !primaryCats.contains(it) && !secondaryCats.contains(it) && !tertiaryCats.contains(it) }.sorted()
        )
    }

    /**
     * Categories that exist only in master-saved records and not in current TodoItems.
     */
    fun getMasterOnlyCategories(todoItems: StateFlow<List<TodoItem>>): Flow<Set<String>> {
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
        val currentFlow = todoItems.map { items ->
            val fromField = items.asSequence().flatMap { (it.categories ?: emptyList()).asSequence() }
            val fromText = items.asSequence().flatMap { parseCategoryInfo(it.text).second.asSequence() }
            (fromField + fromText).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        }
        return masterFlow.combine(currentFlow) { masterDisplay, currentLower ->
            masterDisplay.filter { it.lowercase() !in currentLower }.toSet()
        }
    }

    /**
     * All expenses from master records for a specific category.
     */
    fun getMasterExpensesByCategory(category: String): Flow<List<com.example.monday.ExpenseDisplayItem>> {
        val normalized = category.trim()
        return repository.allCalculationRecords.map { records ->
            records.asSequence()
                .filter { it.isMasterSave }
                .flatMap { record ->
                    val date = record.recordDate.toLocalDate()
                    record.items.asSequence()
                        .filter { it.categories?.any { c -> c.equals(normalized, ignoreCase = true) } == true }
                        .map { item ->
                            com.example.monday.ExpenseDisplayItem(
                                id = item.sourceItemId ?: 0,
                                date = date,
                                description = item.description,
                                quantity = item.quantity,
                                price = item.price.toDoubleOrNull() ?: 0.0
                            )
                        }
                }
                .sortedWith(compareByDescending<com.example.monday.ExpenseDisplayItem> { it.date }.thenBy { it.description })
                .toList()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun getCategoryListByType(type: String): List<ExpenseCategory> {
        return when (type) {
            "primary" -> _primaryCategories.value
            "secondary" -> _secondaryCategories.value
            "tertiary" -> _tertiaryCategories.value
            else -> emptyList()
        }
    }
}
