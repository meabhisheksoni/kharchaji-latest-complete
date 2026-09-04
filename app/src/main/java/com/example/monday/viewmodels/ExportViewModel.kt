package com.example.monday.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monday.managers.PreferenceManager
import com.example.monday.data.TodoRepository
import com.example.monday.data.models.ExportHistoryItem
import com.example.monday.data.models.TodoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: TodoRepository,
    private val prefManager: PreferenceManager
) : ViewModel() {

    // ── Cross-Date Export Buffer ──────────────────────────────────────
    // Survives config changes (lives in ViewModel). Caps at 200 items to prevent unbounded memory growth.
    private val _exportBuffer = MutableStateFlow<Map<LocalDate, List<TodoItem>>>(emptyMap())
    val exportBuffer: StateFlow<Map<LocalDate, List<TodoItem>>> = _exportBuffer
    val exportBufferCount: StateFlow<Int> = _exportBuffer
        .map { buf -> buf.values.sumOf { it.size } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ── Export History ───────────────────────────────────────────────
    private val _exportHistory = MutableStateFlow<List<ExportHistoryItem>>(emptyList())
    val exportHistory: StateFlow<List<ExportHistoryItem>> = _exportHistory

    init {
        // Run AFTER all MutableStateFlow properties have been initialized
        loadAndCleanupHistory()
    }

    fun addToExportBuffer(date: LocalDate, items: List<TodoItem>) {
        if (items.isEmpty()) return
        val currentTotal = _exportBuffer.value.values.sumOf { it.size }
        // Cap buffer at 200 items to prevent entropy accumulation
        if (currentTotal + items.size > 200) return
        _exportBuffer.update { current ->
            current.toMutableMap().apply { put(date, items) }
        }
    }

    fun removeFromExportBuffer(date: LocalDate) {
        _exportBuffer.update { current ->
            current.toMutableMap().apply { remove(date) }
        }
    }

    fun isDateInExportBuffer(date: LocalDate): Boolean = _exportBuffer.value.containsKey(date)

    fun clearExportBuffer() {
        _exportBuffer.update { emptyMap() }
    }

    // Uncheck (isDone=false) all items that were in the export buffer — called after successful share
    fun uncheckExportedItems() {
        val bufferedItems = _exportBuffer.value.values.flatten()
        if (bufferedItems.isEmpty()) return
        viewModelScope.launch {
            bufferedItems.forEach { item ->
                // Re-validate: only uncheck if item still exists in DB
                repository.getItemById(item.id)?.let { dbItem ->
                    repository.update(dbItem.copy(isDone = false))
                }
            }
            clearExportBuffer()
        }
    }

    fun addExportToHistory(id: String, filePath: String, totalSum: Double, itemCount: Int, type: String, items: List<String>) {
        val newItem = ExportHistoryItem(
            id = id,
            filePath = filePath,
            timestamp = System.currentTimeMillis(),
            totalSum = totalSum,
            itemCount = itemCount,
            type = type,
            items = items
        )
        _exportHistory.update { current ->
            // Prepend new item, drop older items to keep it fresh
            (listOf(newItem) + current).take(50) // Increased max slightly to accommodate pinned items
        }
        prefManager.saveExportHistory(_exportHistory.value)
    }

    fun updateExportHistoryItem(updatedItem: ExportHistoryItem) {
        _exportHistory.update { current ->
            current.map { if (it.id == updatedItem.id) updatedItem else it }
        }
        prefManager.saveExportHistory(_exportHistory.value)
    }

    fun deleteExportHistoryItem(item: ExportHistoryItem) {
        _exportHistory.update { current ->
            current.filter { it.id != item.id }
        }
        prefManager.saveExportHistory(_exportHistory.value)
        viewModelScope.launch(Dispatchers.IO) {
            try { File(item.filePath).delete() } catch (_: Exception) {}
        }
    }

    private fun loadAndCleanupHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val history = prefManager.getExportHistory()
                val now = System.currentTimeMillis()
                val twoDaysMillis = 48 * 60 * 60 * 1000L
                val validHistory = history.filter { item ->
                    val isValid = item.isPinned || (now - item.timestamp) < twoDaysMillis
                    if (!isValid) {
                        // Try to delete the orphaned file if it's expired and not pinned
                        try { File(item.filePath).delete() } catch (_: Exception) {}
                    }
                    isValid
                }
                _exportHistory.value = validHistory
                prefManager.saveExportHistory(validHistory)
            } catch (_: Exception) {}
        }
    }
}
