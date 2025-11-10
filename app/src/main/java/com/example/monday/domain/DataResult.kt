package com.example.monday.domain

/**
 * A sealed class for wrapping data operations to provide clear success/error/loading states
 * This follows the pattern commonly used in modern Android applications to handle
 * operations that might succeed, fail, or still be in progress.
 */
sealed class DataResult<out T> {
    /**
     * Success state with data
     */
    data class Success<out T>(val data: T) : DataResult<T>()
    
    /**
     * Error state with exception
     */
    data class Error(val exception: Throwable, val message: String? = null) : DataResult<Nothing>()
    
    /**
     * Loading state
     */
    data object Loading : DataResult<Nothing>()

    /**
     * Helper method to process the result into different branches
     */
    inline fun <R> fold(
        onSuccess: (T) -> R,
        onError: (Throwable, String?) -> R,
        onLoading: () -> R
    ): R {
        return when (this) {
            is Success -> onSuccess(data)
            is Error -> onError(exception, message)
            is Loading -> onLoading()
        }
    }
    
    /**
     * Returns the success data or null if not successful
     */
    fun getOrNull(): T? {
        return when (this) {
            is Success -> data
            else -> null
        }
    }
    
    /**
     * Returns the success data or calls the defaultValue function if not successful
     */
    inline fun getOrElse(defaultValue: () -> @UnsafeVariance T): T {
        return when (this) {
            is Success -> data
            else -> defaultValue()
        }
    }
    
    /**
     * Returns true if the result is a success, false otherwise
     */
    val isSuccess: Boolean
        get() = this is Success
    
    /**
     * Returns true if the result is an error, false otherwise
     */
    val isError: Boolean
        get() = this is Error
    
    /**
     * Returns true if the result is loading, false otherwise
     */
    val isLoading: Boolean
        get() = this is Loading
} 