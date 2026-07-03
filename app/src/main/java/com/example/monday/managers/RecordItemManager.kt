package com.example.monday.managers

import com.example.monday.data.models.CalculationRecord
import com.example.monday.data.models.RecordItem

/**
 * Pure functions for manipulating record items within a CalculationRecord.
 * No database access, no state — just data transformation.
 * Anti-fragile: since these are pure functions, they cannot cause side-effects.
 */
object RecordItemManager {

    fun removeRecordItem(record: CalculationRecord, index: Int): CalculationRecord {
        val updatedItems = record.items.toMutableList()
        if (index >= 0 && index < updatedItems.size) {
            updatedItems.removeAt(index)
        }
        return recalculateTotals(record, updatedItems)
    }

    fun addRecordItem(
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
        return recalculateTotals(record, updatedItems)
    }

    fun updateRecordItem(
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
        return recalculateTotals(record, updatedItems)
    }

    /**
     * Recalculates totalSum, checkedItemsCount, checkedItemsSum from the item list.
     * Single source of truth for record totals — prevents drift between items and summary fields.
     */
    private fun recalculateTotals(record: CalculationRecord, items: List<RecordItem>): CalculationRecord {
        val totalSum = items.sumOf { it.price.toDoubleOrNull() ?: 0.0 }
        val checkedItems = items.filter { it.isChecked }
        return record.copy(
            items = items,
            totalSum = totalSum,
            checkedItemsCount = checkedItems.size,
            checkedItemsSum = checkedItems.sumOf { it.price.toDoubleOrNull() ?: 0.0 },
            timestamp = System.currentTimeMillis()
        )
    }
}
