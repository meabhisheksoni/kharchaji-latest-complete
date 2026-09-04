package com.example.monday.managers

import android.util.Log
import com.example.monday.core.utils.intelligentlyCategorize
import com.example.monday.core.utils.todoItemToRecordItem
import com.example.monday.core.utils.toEpochMilli
import com.example.monday.data.TodoRepository
import com.example.monday.data.models.CalculationRecord
import com.example.monday.data.models.RecordItem
import com.example.monday.data.models.TodoItem
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encapsulates all master-record save/sync/compare logic.
 * Extracted from TodoViewModel to isolate the most complex business logic
 * (150-line save function, fuzzy matching, duplicate detection).
 */
class MasterRecordManager(private val repository: TodoRepository) {

    /**
     * Saves items as both a regular record and a master record for a given date.
     * Handles duplicate detection, fuzzy item matching, and incremental master updates.
     * Returns (regularRecordCreated, masterRecordCreatedOrUpdated).
     */
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

        val startOfDayMillis = date.toEpochMilli()
        val endOfDayMillis = date.plusDays(1).toEpochMilli() - 1

        // ── Regular Record (duplicate-aware) ──────────────────────────
        val existingRegularRecords = repository.getAllCalculationRecordsForDateRangeDirect(
            startOfDayMillis, endOfDayMillis
        ).filter { !it.isMasterSave }

        val newRecordFingerprints = recordItems.map { itemFingerprint(it) }.sorted()
        var duplicateFound = false
        for (existingRecord in existingRegularRecords) {
            val existingFingerprints = existingRecord.items.map { itemFingerprint(it) }.sorted()
            if (newRecordFingerprints.size == existingFingerprints.size && newRecordFingerprints == existingFingerprints) {
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
        }

        // ── Master Record (fuzzy-merge) ──────────────────────────────
        val existingMasterRecords = repository.getMasterSaveRecordsForDateRange(
            startOfDayMillis, endOfDayMillis
        )
        if (existingMasterRecords.isNotEmpty()) {
            val existingMaster = existingMasterRecords.first()
            val updatedMasterItems = mergeIntoMaster(existingMaster, recordItems)
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
            } else {
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
        }
        return Pair(regularRecordCreated, masterRecordCreatedOrUpdated)
    }

    /**
     * Force-replaces the master record for a date with the given items.
     * No fuzzy matching — a hard overwrite.
     */
    suspend fun forceSyncToMasterRecord(date: LocalDate, allItems: List<TodoItem>) {
        val startOfDayMillis = date.toEpochMilli()
        val endOfDayMillis = date.plusDays(1).toEpochMilli() - 1

        val recordItems = allItems.map { todoItemToRecordItem(it) }
        val totalSum = recordItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
        val checkedItemsCount = recordItems.count { it.isChecked }
        val checkedItemsSum = recordItems.filter { it.isChecked }.sumOf { it.price.toDoubleOrNull() ?: 0.0 }

        val existingMasterRecords = repository.getMasterSaveRecordsForDateRange(startOfDayMillis, endOfDayMillis)

        if (existingMasterRecords.isNotEmpty()) {
            val existingMaster = existingMasterRecords.first()
            val updatedMaster = existingMaster.copy(
                items = recordItems,
                totalSum = totalSum,
                checkedItemsCount = checkedItemsCount,
                checkedItemsSum = checkedItemsSum,
                timestamp = System.currentTimeMillis()
            )
            repository.updateCalculationRecord(updatedMaster)
        } else {
            if (recordItems.isNotEmpty()) {
                val masterRecord = CalculationRecord(
                    items = recordItems,
                    totalSum = totalSum,
                    checkedItemsCount = checkedItemsCount,
                    checkedItemsSum = checkedItemsSum,
                    recordDate = startOfDayMillis,
                    isMasterSave = true
                )
                repository.insertCalculationRecord(masterRecord)
            }
        }
    }

    /**
     * Get master record totals for a specific month, grouped by category.
     */
    suspend fun getMasterRecordTotalsForMonth(yearMonth: YearMonth): Map<String, Double> {
        return withContext(Dispatchers.IO) {
            try {
                val startOfMonthMillis = yearMonth.atDay(1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfMonthMillis = yearMonth.atEndOfMonth()
                    .plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                val records = repository.getMasterRecordsForDateRange(startOfMonthMillis, endOfMonthMillis)
                return@withContext calculateMasterRecordTotals(records)
            } catch (e: Exception) {
                Log.e("MasterRecords", "Error processing master records", e)
                return@withContext emptyMap()
            }
        }
    }

    /**
     * Pure function to calculate category totals from a list of records.
     * Prevents double-counting by attributing each item to exactly one category bucket,
     * and maps uncategorized items to "Uncategorized".
     */
    fun calculateMasterRecordTotals(records: List<CalculationRecord>): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        records.forEach { record ->
            record.items.forEach { item ->
                val price = item.price.toDoubleOrNull() ?: return@forEach

                // Credit exactly ONE category per item to prevent stacked-bar inflation
                val categoryToCredit = if (item.categories.isNullOrEmpty()) {
                    "Uncategorized"
                } else {
                    val (primary, secondary, tertiary) = intelligentlyCategorize(item.categories.toSet())
                    primary.firstOrNull() 
                        ?: secondary.firstOrNull() 
                        ?: tertiary.firstOrNull() 
                        ?: item.categories.first()
                }
                
                result[categoryToCredit] = (result[categoryToCredit] ?: 0.0) + price
            }
        }
        return result
    }

    /**
     * Get master record totals grouped strictly by ISO Date String ("YYYY-MM-DD").
     * Uses persistent record.totalSum and idempotent assignment to prevent double-counting.
     */
    suspend fun getMasterRecordDailyTotalsForMonth(yearMonth: YearMonth): Map<String, Double> {
        return withContext(Dispatchers.IO) {
            try {
                val startOfMonthMillis = yearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfMonthMillis = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                val records = repository.getMasterRecordsForDateRange(startOfMonthMillis, endOfMonthMillis)
                
                val dailyTotals = mutableMapOf<String, Double>()
                // Records from repository are sorted by timestamp DESC
                records.forEach { record ->
                    val dateStr = java.time.Instant.ofEpochMilli(record.recordDate)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .toString()
                    
                    // Idempotent assignment: newest master revision wins, preventing duplicate accumulation
                    if (!dailyTotals.containsKey(dateStr)) {
                        dailyTotals[dateStr] = record.totalSum
                    }
                }
                dailyTotals
            } catch (e: Exception) {
                Log.e("MasterRecords", "Error calculating daily master totals", e)
                emptyMap()
            }
        }
    }

    // ── Internal Helpers ──────────────────────────────────────────────

    /**
     * Merges new record items into an existing master record using 3-tier matching:
     * 1. sourceItemId (exact), 2. name (exact), 3. fuzzy (price + substring).
     */
    private fun mergeIntoMaster(
        existingMaster: CalculationRecord,
        newItems: List<RecordItem>
    ): List<RecordItem> {
        val updatedMasterItems = mutableListOf<RecordItem>()
        val existingItemsBySourceId = existingMaster.items
            .filter { it.sourceItemId != null }
            .associateBy { it.sourceItemId }
        val existingItemsByName = existingMaster.items
            .groupBy { it.description.trim().lowercase() }
        val processedExistingItems = mutableSetOf<RecordItem>()

        for (newItem in newItems) {
            var matchFound = false
            var bestMatchItem: RecordItem? = null

            // Tier 1: Match by sourceItemId
            if (newItem.sourceItemId != null) {
                bestMatchItem = existingItemsBySourceId[newItem.sourceItemId]
                if (bestMatchItem != null) {
                    matchFound = true
                }
            }
            // Tier 2: Match by exact name
            if (!matchFound) {
                val normalizedName = newItem.description.trim().lowercase()
                val matchingExistingItems = existingItemsByName[normalizedName]
                if (matchingExistingItems != null && matchingExistingItems.isNotEmpty()) {
                    bestMatchItem = matchingExistingItems.firstOrNull { it !in processedExistingItems }
                    if (bestMatchItem != null) {
                        matchFound = true
                    }
                }
            }
            // Tier 3: Fuzzy match (price + substring)
            if (!matchFound) {
                for (existingItem in existingMaster.items) {
                    if (existingItem in processedExistingItems) continue
                    if (existingItem.price.trim() == newItem.price.trim()) {
                        val existingNameLower = existingItem.description.trim().lowercase()
                        val newNameLower = newItem.description.trim().lowercase()
                        if (existingNameLower.contains(newNameLower) || newNameLower.contains(existingNameLower)) {
                            bestMatchItem = existingItem
                            matchFound = true
                            break
                        }
                    }
                }
            }

            if (matchFound && bestMatchItem != null) {
                processedExistingItems.add(bestMatchItem)
                val finalSourceItemId = newItem.sourceItemId ?: bestMatchItem.sourceItemId
                updatedMasterItems.add(bestMatchItem.copy(
                    description = newItem.description,
                    price = newItem.price,
                    quantity = newItem.quantity,
                    categories = newItem.categories,
                    imageUris = newItem.imageUris,
                    isChecked = newItem.isChecked,
                    sourceItemId = finalSourceItemId
                ))
            } else {
                updatedMasterItems.add(newItem)
            }
        }

        // Preserve unmatched existing items — prevents silent data loss
        val remainingItems = existingMaster.items.filter { it !in processedExistingItems }
        if (remainingItems.isNotEmpty()) {
            updatedMasterItems.addAll(remainingItems)
        }
        return updatedMasterItems
    }

    /** Fingerprint for duplicate detection — includes categories and images. */
    private fun itemFingerprint(item: RecordItem): String {
        val categoriesPart = item.categories?.joinToString(",") ?: ""
        val imagesPart = item.imageUris?.sorted()?.joinToString(",") ?: ""
        return "${item.description.trim()}|${item.price.trim()}|${item.quantity?.trim() ?: ""}|$categoriesPart|$imagesPart"
    }

    /** Compares two item lists by their fingerprints (order-independent). */
    fun areItemListsIdentical(items1: List<RecordItem>, items2: List<RecordItem>): Boolean {
        if (items1.size != items2.size) return false
        return items1.map { itemFingerprint(it) }.sorted() == items2.map { itemFingerprint(it) }.sorted()
    }
}
