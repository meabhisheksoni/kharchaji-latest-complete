package com.example.monday.domain

import android.util.Log
import com.example.monday.core.utils.*
import com.example.monday.data.models.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

/**
 * Use case for creating a master record from a list of items
 * This moves complex business logic out of the ViewModel for better separation of concerns
 */
class CreateMasterRecordUseCase(private val repository: ITodoRepository) {
    
    /**
     * Create a master record from a list of items
     * @param date The date for the record
     * @param items The list of items to include in the record
     * @return Pair<Boolean, Boolean> - First boolean indicates if a regular record was created,
     *         second indicates if a master record was created or updated
     */
    suspend operator fun invoke(date: LocalDate, allItems: List<TodoItem>): Pair<Boolean, Boolean> {
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
        
        // Get all existing non-master records for this date directly from DB
        val startOfDayMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDayMillis = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        val existingRegularRecords: List<CalculationRecord> = repository.getAllCalculationRecordsForDateRangeDirect(
            startOfDayMillis, endOfDayMillis
        ).filter { !it.isMasterSave }
        
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
        }
        
        // Now handle the master record
        val existingMasterRecords = repository.getMasterSaveRecordsForDateRange(
            startOfDayMillis, endOfDayMillis
        )
        
        if (existingMasterRecords.isNotEmpty()) {
            // Update existing master record
            val existingMaster = existingMasterRecords.first()
            
            // Create new master items list by combining existing and new items
            val updatedMasterItems = mergeRecordItems(existingMaster.items, recordItems)
            
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
            } else {
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
        }
        
        return Pair(regularRecordCreated, masterRecordCreatedOrUpdated)
    }
    
    /**
     * Merge two lists of record items, intelligent updating existing items with new data
     */
    private fun mergeRecordItems(existingItems: List<RecordItem>, newItems: List<RecordItem>): List<RecordItem> {
        val updatedMasterItems = mutableListOf<RecordItem>()
            
        // Create maps for efficient lookups
        // 1. Map of existing items by sourceItemId (for direct matches)
        val existingItemsBySourceId = existingItems
            .filter { it.sourceItemId != null }
            .associateBy { it.sourceItemId }
        
        // 2. Map of existing items by name (case-insensitive) for fallback matching
        val existingItemsByName = existingItems
            .groupBy { it.description.trim().lowercase() }
        
        // Track which existing items have been processed
        val processedExistingItems = mutableSetOf<RecordItem>()
        
        // Process each new item
        for (newItem in newItems) {
            var matchFound = false
            var bestMatchItem: RecordItem? = null
            
            // FIRST MATCHING STRATEGY: Match by sourceItemId (most reliable)
            if (newItem.sourceItemId != null) {
                bestMatchItem = existingItemsBySourceId[newItem.sourceItemId]
                if (bestMatchItem != null) {
                    matchFound = true
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
                    }
                }
            }
            
            // THIRD MATCHING STRATEGY: Try fuzzy matching if still no match
            if (!matchFound) {
                // Try to find a match by price and similar name
                for (existingItem in existingItems) {
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
                updatedMasterItems.add(newItem)
            }
        }
        
        // Add any remaining existing items that weren't matched
        val remainingItems = existingItems.filter { it !in processedExistingItems }
        if (remainingItems.isNotEmpty()) {
            updatedMasterItems.addAll(remainingItems)
        }
        
        return updatedMasterItems
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
} 
