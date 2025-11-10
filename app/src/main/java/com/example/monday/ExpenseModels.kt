package com.example.monday

import java.time.LocalDate

// Basic expense display item for showing expenses in lists
data class ExpenseDisplayItem(
    val id: Int,
    val date: LocalDate,
    val description: String,
    val quantity: String?,
    val price: Double
)

// Extended version with additional fields needed for updating/replacing
data class ExtendedExpenseDisplayItem(
    val id: Int,
    val date: LocalDate,
    val description: String,
    val quantity: String?,
    val price: Double,
    val originalText: String,
    val isDone: Boolean,
    val categories: List<String>?,
    val imageUris: List<String>?,
    val timestamp: Long
) 