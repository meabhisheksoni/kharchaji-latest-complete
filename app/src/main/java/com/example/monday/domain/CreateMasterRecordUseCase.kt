package com.example.monday.domain

import android.util.Log
import com.example.monday.RecordItem
import com.example.monday.TodoItem
import com.example.monday.TodoRepository
import com.example.monday.todoItemToRecordItem
import java.time.LocalDate
import java.time.ZoneId

/**
 * Use case for creating a master record from a list of items
 * This moves complex business logic out of the ViewModel for better separation of concerns
 */
class CreateMasterRecordUseCase(private val repository: TodoRepository) {
    
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
        Log.d("MasterSave", "Checking for existing regular records with identical items")
        
        // Get all existing non-master records for this date directly from DB
        val startOfDayMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDayMillis = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
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
            val regularRecord = com.example.monday.CalculationRecord(
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
                Log.d("MasterSave", "Updated existing master record with new items")
            } else {
                Log.d("MasterSave", "No changes to master record, skipping update")
            }
            
        } else {
            // Create new master record with all items
            val masterRecord = com.example.monday.CalculationRecord(
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
        val remainingItems = existingItems.filter { it !in processedExistingItems }
        if (remainingItems.isNotEmpty()) {
            Log.d("MasterSave", "Adding ${remainingItems.size} unmatched existing items to master record")
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