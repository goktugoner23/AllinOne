package com.example.allinone.data.common

/**
 * A sealed class representing the different states of a UI component that loads data.
 * 
 * This provides a type-safe way to handle loading, success, error, and empty states
 * in the UI layer.
 * 
 * @param T The type of data being loaded
 */
sealed class UiState<out T> {
    /**
     * Initial loading state
     */
    data object Loading : UiState<Nothing>()
    
    /**
     * Success state with the loaded data
     */
    data class Success<T>(val data: T) : UiState<T>()
    
    /**
     * Error state with an error message
     */
    data class Error(val message: String) : UiState<Nothing>()
    
    /**
     * Empty state when no data is available
     */
    data object Empty : UiState<Nothing>()
    
    /**
     * Helper function to transform success data if needed
     */
    inline fun <R> map(transform: (T) -> R): UiState<R> {
        return when (this) {
            is Loading -> Loading
            is Success -> Success(transform(data))
            is Error -> Error(message)
            is Empty -> Empty
        }
    }
    
    /**
     * Gets the value from Success state or returns null for other states
     */
    fun getValueOrNull(): T? = (this as? Success)?.data
} 