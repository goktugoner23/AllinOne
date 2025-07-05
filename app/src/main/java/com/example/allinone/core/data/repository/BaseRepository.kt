package com.example.allinone.core.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.allinone.firebase.OfflineQueue
import com.example.allinone.utils.NetworkUtils
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Base repository class that provides common functionality for all feature repositories.
 * This helps reduce code duplication and provides consistent error handling.
 */
abstract class BaseRepository<T> {
    
    protected val gson = Gson()
    
    // Common state management
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    // Abstract properties that must be implemented by child classes
    abstract val data: StateFlow<List<T>>
    abstract val tag: String
    
    /**
     * Set loading state
     */
    protected fun setLoading(loading: Boolean) {
        _isLoading.postValue(loading)
    }
    
    /**
     * Set error message
     */
    protected fun setError(message: String) {
        _errorMessage.postValue(message)
        Log.e(tag, message)
    }
    
    /**
     * Clear error message
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    /**
     * Handle offline operation by adding to queue
     */
    protected fun handleOfflineOperation(
        offlineQueue: OfflineQueue,
        dataType: OfflineQueue.DataType,
        operation: OfflineQueue.Operation,
        data: T,
        message: String
    ) {
        try {
            offlineQueue.enqueue(dataType, operation, gson.toJson(data))
            setError(message)
            Log.d(tag, "Operation queued for offline sync: $operation")
        } catch (e: Exception) {
            setError("Error queueing offline operation: ${e.message}")
        }
    }
    
    /**
     * Check if network is available and execute online operation or queue for offline
     */
    protected suspend fun executeWithNetworkCheck(
        networkUtils: NetworkUtils,
        offlineQueue: OfflineQueue,
        dataType: OfflineQueue.DataType,
        operation: OfflineQueue.Operation,
        data: T,
        offlineMessage: String,
        onlineOperation: suspend () -> Unit
    ) {
        try {
            if (networkUtils.isActiveNetworkConnected()) {
                onlineOperation()
                Log.d(tag, "Operation completed online: $operation")
            } else {
                handleOfflineOperation(offlineQueue, dataType, operation, data, offlineMessage)
            }
        } catch (e: Exception) {
            setError("Error executing operation: ${e.message}")
            Log.e(tag, "Error in executeWithNetworkCheck", e)
        }
    }
} 