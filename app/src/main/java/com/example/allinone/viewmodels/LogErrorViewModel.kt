package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.utils.LogcatHelper
import kotlinx.coroutines.launch

class LogErrorViewModel(application: Application) : AndroidViewModel(application) {
    
    private val logcatHelper = LogcatHelper(application.applicationContext)
    
    // LiveData for the log entries
    private val _logEntries = MutableLiveData<List<LogcatHelper.LogEntry>>()
    val logEntries: LiveData<List<LogcatHelper.LogEntry>> = _logEntries
    
    // LiveData for error messages
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    init {
        refreshLogs()
    }
    
    /**
     * Refresh logs from the logcat
     */
    fun refreshLogs() {
        viewModelScope.launch {
            try {
                // Capture logcat output
                logcatHelper.captureLogcat()
                
                // Update LiveData with the current log entries
                _logEntries.postValue(logcatHelper.getLogEntries())
            } catch (e: Exception) {
                _errorMessage.postValue("Error refreshing logs: ${e.message}")
            }
        }
    }
    
    /**
     * Clear all stored logs
     */
    fun clearLogs() {
        viewModelScope.launch {
            try {
                logcatHelper.clearLogs()
                _logEntries.postValue(emptyList())
            } catch (e: Exception) {
                _errorMessage.postValue("Error clearing logs: ${e.message}")
            }
        }
    }
    
    /**
     * Get all log entries as a formatted string for sharing
     */
    fun getFormattedLogsForSharing(): String {
        val logs = logcatHelper.getLogEntries()
        if (logs.isEmpty()) {
            return "No error logs available."
        }
        
        val builder = StringBuilder()
        builder.append("AllinOne App Error Logs:\n\n")
        
        logs.forEach { entry ->
            builder.append(entry.toString())
            builder.append("\n")
        }
        
        return builder.toString()
    }
} 