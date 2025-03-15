package com.example.allinone.data.common

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.allinone.utils.ErrorHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Base ViewModel class that provides common functionality for all ViewModels.
 */
abstract class BaseViewModel : ViewModel() {
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error state
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage
    
    /**
     * Launches a coroutine with the specified dispatcher and error handling.
     * 
     * @param dispatcher The coroutine dispatcher to use
     * @param errorHandler Optional error handler for custom error handling
     * @param showLoading Whether to show loading state
     * @param block The suspend function to execute
     */
    protected fun launchWithHandler(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        errorHandler: ErrorHandler? = null,
        showLoading: Boolean = true,
        block: suspend () -> Unit
    ) {
        if (showLoading) {
            _isLoading.value = true
        }
        
        viewModelScope.launch(dispatcher) {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Don't handle cancellation exceptions
                    Timber.d("Coroutine was cancelled")
                    throw e
                }
                
                Timber.e(e, "Error in ViewModel coroutine")
                
                // Use provided error handler or show generic error
                val message = errorHandler?.handle(e) ?: e.message ?: "An unknown error occurred"
                _errorMessage.postValue(message)
            } finally {
                if (showLoading) {
                    _isLoading.postValue(false)
                }
            }
        }
    }
    
    /**
     * Collects a Flow with error handling.
     * 
     * @param flow The flow to collect
     * @param errorHandler Optional error handler for custom error handling
     * @param showLoading Whether to show loading state
     * @param collector Function to handle emitted values
     */
    protected fun <T> collectWithHandler(
        flow: Flow<T>,
        errorHandler: ErrorHandler? = null,
        showLoading: Boolean = true,
        collector: suspend (T) -> Unit
    ) {
        if (showLoading) {
            _isLoading.value = true
        }
        
        viewModelScope.launch {
            flow
                .catch { e ->
                    if (e is CancellationException) {
                        throw e
                    }
                    
                    Timber.e(e, "Error in flow")
                    
                    // Use provided error handler or show generic error
                    val message = errorHandler?.handle(e) ?: e.message ?: "An unknown error occurred"
                    _errorMessage.postValue(message)
                    
                    if (showLoading) {
                        _isLoading.postValue(false)
                    }
                }
                .flowOn(Dispatchers.IO)
                .collect {
                    try {
                        collector(it)
                    } finally {
                        if (showLoading) {
                            _isLoading.postValue(false)
                        }
                    }
                }
        }
    }
    
    /**
     * Clears the current error message.
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
} 