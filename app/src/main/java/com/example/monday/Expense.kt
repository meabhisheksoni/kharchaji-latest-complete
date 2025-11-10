package com.example.monday

/**
 * Represents an expense item for the calendar view
 */
data class Expense(
    val description: String,
    val amount: Double,
    val quantity: String? = null,
    val category: String? = null,
    val timestamp: Long
)