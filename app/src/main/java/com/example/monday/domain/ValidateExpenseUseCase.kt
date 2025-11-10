package com.example.monday.domain

import android.util.Log

/**
 * Use case for validating expense inputs
 * This separates validation logic from the ViewModel
 */
class ValidateExpenseUseCase {
    companion object {
        // Error codes for different validation failures
        const val ERROR_EMPTY_DESCRIPTION = 1
        const val ERROR_EMPTY_PRICE = 2
        const val ERROR_INVALID_PRICE_FORMAT = 3
        const val ERROR_PRICE_TOO_LARGE = 4
        const val ERROR_NEGATIVE_PRICE = 5
    }
    
    /**
     * Data class to hold validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorCode: Int? = null,
        val errorMessage: String? = null
    )
    
    /**
     * Validate expense input and return a ValidationResult
     */
    operator fun invoke(description: String, price: String): ValidationResult {
        Log.d("ExpenseValidation", "Validating expense: '$description', price: '$price'")
        
        // Check for empty description
        if (description.isBlank()) {
            return ValidationResult(
                isValid = false,
                errorCode = ERROR_EMPTY_DESCRIPTION,
                errorMessage = "Description cannot be empty"
            )
        }
        
        // Check for empty price
        if (price.isBlank()) {
            return ValidationResult(
                isValid = false,
                errorCode = ERROR_EMPTY_PRICE,
                errorMessage = "Price cannot be empty"
            )
        }
        
        // Check if price is a valid number
        val priceValue = try {
            price.toDouble()
        } catch (e: NumberFormatException) {
            return ValidationResult(
                isValid = false,
                errorCode = ERROR_INVALID_PRICE_FORMAT,
                errorMessage = "Price must be a valid number"
            )
        }
        
        // Check for negative price
        if (priceValue < 0) {
            return ValidationResult(
                isValid = false,
                errorCode = ERROR_NEGATIVE_PRICE,
                errorMessage = "Price cannot be negative"
            )
        }
        
        // Check for unreasonably large price (a billion)
        if (priceValue > 1_000_000_000) {
            return ValidationResult(
                isValid = false,
                errorCode = ERROR_PRICE_TOO_LARGE,
                errorMessage = "Price is unreasonably large"
            )
        }
        
        // All checks passed
        return ValidationResult(isValid = true)
    }
} 