package com.example.monday.data.models

data class ExportHistoryItem(
    val id: String,
    val filePath: String,
    val timestamp: Long,
    val totalSum: Double,
    val itemCount: Int,
    val type: String, // "pdf" or "image"
    val items: List<String>? = emptyList(), // Format: "Item Name|Price"
    val customName: String? = null,
    val isPinned: Boolean = false
)
