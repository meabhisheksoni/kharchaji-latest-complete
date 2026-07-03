package com.example.monday

import com.example.monday.data.models.TodoItem
import com.example.monday.data.models.CalculationRecord
import com.example.monday.data.models.RecordItem
import com.example.monday.managers.PreferenceManager
import com.example.monday.managers.CategoryManager
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val application: Application,
    val prefManager: PreferenceManager,
    val categoryManager: CategoryManager
) : ViewModel() {

    val primaryCategories: StateFlow<List<ExpenseCategory>> get() = categoryManager.primaryCategories
    val secondaryCategories: StateFlow<List<ExpenseCategory>> get() = categoryManager.secondaryCategories
    val tertiaryCategories: StateFlow<List<ExpenseCategory>> get() = categoryManager.tertiaryCategories
    val lastCategoryAction: StateFlow<CategoryManager.CategoryAction?> get() = categoryManager.lastCategoryAction

    private val _directMasterEditMode = MutableStateFlow(false)
    val directMasterEditMode: StateFlow<Boolean> = _directMasterEditMode

    init {
        categoryManager.loadAllCategories()
        _directMasterEditMode.value = getPaymentMonitorSetting("direct_master_edit_mode") ?: false
    }

    suspend fun getUncategorizedExpenses(): List<TodoItem> = categoryManager.getUncategorizedExpenses()
    suspend fun getExpensesWithLessThanThreeCategories(): List<TodoItem> = categoryManager.getExpensesWithLessThanThreeCategories()
    suspend fun getExpensesWithMoreThanThreeCategories(): List<TodoItem> = categoryManager.getExpensesWithMoreThanThreeCategories()
    suspend fun getExpensesWithExactlyThreeCategories(): List<TodoItem> = categoryManager.getExpensesWithExactlyThreeCategories()
    suspend fun getAllUniqueCategories(): List<String> = categoryManager.getAllUniqueCategories()
    suspend fun getPrimaryCategories(): List<String> = categoryManager.getPrimaryCategories()
    suspend fun getAllCategoriesByType(): Map<String, List<String>> = categoryManager.getAllCategoriesByType()

    suspend fun saveCategories(type: String, categories: List<ExpenseCategory>) = categoryManager.saveCategories(type, categories)
    fun getSavedCategories(type: String): List<ExpenseCategory>? = categoryManager.getSavedCategories(type)

    suspend fun saveCategoryVisibilitySetting(type: String, isVisible: Boolean) = prefManager.saveCategoryVisibilitySetting(type, isVisible)
    fun getCategoryVisibilitySetting(type: String): Boolean? = prefManager.getCategoryVisibilitySetting(type)

    suspend fun savePaymentMonitorSetting(key: String, isEnabled: Boolean) = prefManager.savePaymentMonitorSetting(key, isEnabled)
    suspend fun setDirectMasterEditMode(enabled: Boolean) {
        prefManager.savePaymentMonitorSetting("direct_master_edit_mode", enabled)
        _directMasterEditMode.value = enabled
    }
    fun getPaymentMonitorSetting(key: String): Boolean? = prefManager.getPaymentMonitorSetting(key)

    fun getDashboardViewMode(): String = prefManager.getDashboardViewMode()
    suspend fun saveDashboardViewMode(mode: String) = prefManager.saveDashboardViewMode(mode)

    fun getDateBarPosition(): String = prefManager.getDateBarPosition()
    suspend fun saveDateBarPosition(position: String) = prefManager.saveDateBarPosition(position)

    fun saveRecentlySelectedCategory(categoryType: String, categoryName: String) = prefManager.saveRecentlySelectedCategory(categoryType, categoryName)
    fun getRecentlySelectedCategory(categoryType: String): String? = prefManager.getRecentlySelectedCategory(categoryType)

    suspend fun updateCategory(oldCategory: ExpenseCategory, newCategory: ExpenseCategory, type: String) = categoryManager.updateCategory(oldCategory, newCategory, type)
    suspend fun deleteCategory(categoryToDelete: ExpenseCategory, type: String) = categoryManager.deleteCategory(categoryToDelete, type)
    suspend fun moveCategory(category: ExpenseCategory, type: String, newPosition: Int) = categoryManager.moveCategory(category, type, newPosition)
    suspend fun moveCategoryTo(type: String, fromIndex: Int, toIndex: Int) = categoryManager.moveCategoryTo(type, fromIndex, toIndex)
    suspend fun addCategory(category: ExpenseCategory, type: String) = categoryManager.addCategory(category, type)

    suspend fun undoLastCategoryAction() = categoryManager.undoLastCategoryAction()
    suspend fun deleteCategoryWithOptions(categoryToDelete: ExpenseCategory, type: String, removeFromExpenses: Boolean) = categoryManager.deleteCategoryWithOptions(categoryToDelete, type, removeFromExpenses)

    fun setLastCategoryAction(action: CategoryManager.CategoryAction) = viewModelScope.launch(Dispatchers.IO) {
        categoryManager.setLastCategoryAction(action)
    }

    fun getMasterOnlyCategories(todoItemsFlow: StateFlow<List<TodoItem>>): Flow<Set<String>> = categoryManager.getMasterOnlyCategories(todoItemsFlow)
    fun getMasterExpensesByCategory(category: String): Flow<List<ExpenseDisplayItem>> = categoryManager.getMasterExpensesByCategory(category)
}
