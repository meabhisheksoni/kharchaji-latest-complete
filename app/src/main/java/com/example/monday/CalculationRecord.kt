package com.example.monday

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

// For storing the list of items as JSON
data class RecordItem(
    val description: String,
    val quantity: String?, // e.g., "1kg", "2 items"
    val price: String, // e.g., "100", "50.50"
    val isChecked: Boolean, // if this specific item was part of the 'checked sum'
    val categories: List<String>? = null, // Added categories field to store item categories
    val imageUris: List<String>? = null, // Added to store image URIs
    val sourceItemId: Int? = null // Added to track the source TodoItem ID
)

@Entity(tableName = "calculation_records")
@TypeConverters(CalculationRecordConverters::class) // We'll define this later for List<RecordItem>
data class CalculationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val recordDate: Long,
    val items: List<RecordItem>, // Store as JSON string or use a separate related table
    val totalSum: Double,
    val checkedItemsCount: Int,
    val checkedItemsSum: Double,
    val isMasterSave: Boolean = false // Flag to identify master save records
)

// Placeholder for TypeConverters - will need to implement this
// to convert List<RecordItem> to/from String (JSON) for Room storage.
// For a more robust solution, a separate table for RecordItems with a foreign key
// to CalculationRecord would be better, but JSON is simpler for now.
class CalculationRecordConverters {
    private val gson = com.google.gson.Gson()

    @androidx.room.TypeConverter
    fun fromRecordItemList(value: List<RecordItem>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @androidx.room.TypeConverter
    fun toRecordItemList(value: String?): List<RecordItem>? {
        return value?.let { gson.fromJson(it, object : com.google.gson.reflect.TypeToken<List<RecordItem>>() {}.type) }
    }
    
    @androidx.room.TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @androidx.room.TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let { gson.fromJson(it, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type) }
    }
} 