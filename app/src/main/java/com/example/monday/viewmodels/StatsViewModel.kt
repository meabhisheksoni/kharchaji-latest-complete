package com.example.monday.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monday.data.TodoRepository
import com.example.monday.data.models.CalculationRecord
import com.example.monday.data.models.TodoItem
import com.example.monday.managers.MasterRecordManager
import com.example.monday.managers.RecordItemManager
import com.example.monday.ExpenseDisplayItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import com.example.monday.core.utils.toLocalDate

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: TodoRepository,
    private val masterRecordManager: MasterRecordManager
) : ViewModel() {

    val allCalculationRecords: StateFlow<List<CalculationRecord>> = repository.allCalculationRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertCalculationRecord(record: CalculationRecord) = viewModelScope.launch(Dispatchers.IO) {
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

    fun deleteCalculationRecord(record: CalculationRecord) = viewModelScope.launch(Dispatchers.IO) {
        try {
            repository.deleteCalculationRecord(record)
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error deleting calculation record: ${record.id}", e)
        }
    }

    fun deleteCalculationRecordById(recordId: Int) = viewModelScope.launch(Dispatchers.IO) {
        try {
            repository.deleteCalculationRecordById(recordId)
        } catch (e: Exception) {
            Log.e("KharchaJi", "Error deleting calculation record by ID: $recordId", e)
        }
    }

    fun deleteAllCalculationRecords() = viewModelScope.launch(Dispatchers.IO) {
        try {
            repository.deleteAllCalculationRecords()
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

    suspend fun getAllCalculationRecordsForExport(): List<CalculationRecord> {
        return repository.getAllCalculationRecordsForExport()
    }

    fun insertCalculationRecordIfNotDuplicateAsync(record: CalculationRecord) = viewModelScope.launch {
        insertCalculationRecordIfNotDuplicate(record)
    }

    suspend fun insertCalculationRecordIfNotDuplicate(record: CalculationRecord): Boolean {
        val recordDate = record.recordDate.toLocalDate()
        val startOfDay = recordDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = recordDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        val existingRecords = repository.getAllCalculationRecordsForDateRangeDirect(startOfDay, endOfDay)
            .filter { !it.isMasterSave }

        val newRecordItems = record.items.map {
            "${it.description.trim()}|${it.price.trim()}|${it.quantity?.trim() ?: ""}"
        }.sorted()

        for (existingRecord in existingRecords) {
            val existingItems = existingRecord.items.map {
                "${it.description.trim()}|${it.price.trim()}|${it.quantity?.trim() ?: ""}"
            }.sorted()
            
            if (newRecordItems.size == existingItems.size && newRecordItems == existingItems) {
                return false
            }
        }

        repository.insertCalculationRecord(record)
        return true
    }

    // Delegated to MasterRecordManager
    suspend fun saveToMasterRecord(date: LocalDate, allItems: List<TodoItem>): Pair<Boolean, Boolean> =
        masterRecordManager.saveToMasterRecord(date, allItems)

    fun saveToMasterRecordAsync(date: LocalDate, allItems: List<TodoItem>) = viewModelScope.launch(Dispatchers.IO) {
        masterRecordManager.saveToMasterRecord(date, allItems)
    }

    suspend fun forceSyncToMasterRecord(date: LocalDate, allItems: List<TodoItem>) =
        masterRecordManager.forceSyncToMasterRecord(date, allItems)

    fun updateCalculationRecord(record: CalculationRecord) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateCalculationRecord(record)
    }

    // Delegated to RecordItemManager
    suspend fun removeRecordItem(record: CalculationRecord, index: Int): CalculationRecord =
        RecordItemManager.removeRecordItem(record, index)

    suspend fun addRecordItem(
        record: CalculationRecord,
        description: String,
        price: String,
        quantity: String?,
        categories: List<String>? = null,
        sourceItemId: Int? = null
    ): CalculationRecord = RecordItemManager.addRecordItem(record, description, price, quantity, categories, sourceItemId)

    suspend fun updateRecordItem(
        record: CalculationRecord,
        index: Int,
        description: String,
        price: String,
        quantity: String?,
        categories: List<String>? = null
    ): CalculationRecord = RecordItemManager.updateRecordItem(record, index, description, price, quantity, categories)

    suspend fun getMasterRecordTotalsForMonth(yearMonth: YearMonth): Map<String, Double> =
        masterRecordManager.getMasterRecordTotalsForMonth(yearMonth)

    fun calculateMasterRecordTotals(records: List<CalculationRecord>): Map<String, Double> =
        masterRecordManager.calculateMasterRecordTotals(records)

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
        }
    }

    suspend fun getMasterRecordsForMonth(startMillis: Long, endMillis: Long): List<CalculationRecord> {
        return withContext(Dispatchers.IO) {
            repository.getMasterRecordsForDateRange(startMillis, endMillis)
        }
    }

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
                    .toList()
            }
    }
}
