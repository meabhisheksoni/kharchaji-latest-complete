package com.example.monday.data.models

/**
 * Data class representing a parsed cart item.
 */
data class CartItem(
    val rawName: String,
    val price: Double,
    val quantity: Int
)
