package com.example.monday

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "todo_table")
@TypeConverters(CalculationRecordConverters::class)
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    var isDone: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val categories: List<String>? = null,
    val hasPrimaryCategory: Boolean = false,
    val hasSecondaryCategory: Boolean = false,
    val hasTertiaryCategory: Boolean = false,
    val imageUris: List<String>? = null
)


