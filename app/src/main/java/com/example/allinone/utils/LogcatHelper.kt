package com.example.allinone.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class LogcatHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "LogcatHelper"
        private const val MAX_STORED_LOGS = 500 // Maximum number of log entries to store
        private const val LOG_FILE_NAME = "app_error_logs.txt"
    }
    
    // Thread-safe list to store log entries
    private val logEntries = CopyOnWriteArrayList<LogEntry>()
    
    // Data class to represent a log entry
    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String
    ) {
        val formattedTimestamp: String
            get() {
                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                return formatter.format(Date(timestamp))
            }
            
        override fun toString(): String {
            return "$formattedTimestamp [$level/$tag]: $message"
        }
    }
    
    init {
        // Load saved logs on initialization
        loadLogsFromFile()
    }
    
    /**
     * Captures logcat output and saves it to the log entries list
     */
    suspend fun captureLogcat() = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("logcat -d -v threadtime")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            
            var line: String?
            val regex = Regex("^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+\\s+\\d+\\s+\\d+\\s+([VDIWEF])\\s+([^:]+):\\s+(.+)$")
            
            while (bufferedReader.readLine().also { line = it } != null) {
                line?.let {
                    val matchResult = regex.find(it)
                    if (matchResult != null) {
                        val (level, tag, message) = matchResult.destructured
                        
                        // Only capture errors and warnings
                        if (level == "E" || level == "W") {
                            addLogEntry(LogEntry(
                                System.currentTimeMillis(),
                                level,
                                tag.trim(),
                                message.trim()
                            ))
                        }
                    }
                }
            }
            
            bufferedReader.close()
            saveLogsToFile()
        } catch (e: IOException) {
            Log.e(TAG, "Error capturing logcat: ${e.message}", e)
        }
    }
    
    /**
     * Adds a log entry to the list and trims if necessary
     */
    fun addLogEntry(entry: LogEntry) {
        logEntries.add(entry)
        
        // Trim the list if it exceeds the maximum size
        if (logEntries.size > MAX_STORED_LOGS) {
            val itemsToRemove = logEntries.size - MAX_STORED_LOGS
            for (i in 0 until itemsToRemove) {
                logEntries.removeAt(0)
            }
        }
    }
    
    /**
     * Get all log entries
     */
    fun getLogEntries(): List<LogEntry> {
        return logEntries.toList()
    }
    
    /**
     * Clear all log entries
     */
    fun clearLogs() {
        logEntries.clear()
        saveLogsToFile()
    }
    
    /**
     * Save logs to file
     */
    private fun saveLogsToFile() {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            file.bufferedWriter().use { writer ->
                for (entry in logEntries) {
                    writer.write("${entry.timestamp}|${entry.level}|${entry.tag}|${entry.message}")
                    writer.newLine()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error saving logs to file: ${e.message}", e)
        }
    }
    
    /**
     * Load logs from file
     */
    private fun loadLogsFromFile() {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) {
                logEntries.clear()
                file.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.split("|", limit = 4)
                        if (parts.size == 4) {
                            try {
                                val timestamp = parts[0].toLong()
                                val level = parts[1]
                                val tag = parts[2]
                                val message = parts[3]
                                
                                logEntries.add(LogEntry(timestamp, level, tag, message))
                            } catch (e: NumberFormatException) {
                                Log.e(TAG, "Error parsing log timestamp: ${e.message}", e)
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading logs from file: ${e.message}", e)
        }
    }
} 