package com.example.allinone.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Helper class to capture and manage logcat entries
 */
class LogcatHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "LogcatHelper"
        private const val MAX_STORED_LOGS = 50 // Further reduced to minimize memory usage
        private const val LOG_FILE_NAME = "app_error_logs.txt"
        private const val LINES_TO_CAPTURE = 50 // Capture just enough logs
        
        // Precompile the regex pattern as static constant
        private val LOG_PATTERN = Regex("^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+\\s+\\d+\\s+\\d+\\s+([VDIWEF])\\s+([^:]+):\\s+(.+)$")
        
        // Thread-safe date formatter - create once and reuse
        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }
    
    // Thread-safe list to store log entries
    private val logEntries = CopyOnWriteArrayList<LogEntry>()
    
    // Flag to prevent concurrent file operations
    private val isWritingToFile = AtomicBoolean(false)
    
    // Create a coroutine scope for background operations
    private val backgroundScope = CoroutineScope(Dispatchers.IO)
    
    // Data class to represent a log entry
    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String
    ) {
        val formattedTimestamp: String
            get() = DATE_FORMATTER.format(Date(timestamp))
            
        override fun toString(): String = "$formattedTimestamp [$level/$tag]: $message"
    }
    
    init {
        // Load saved logs on initialization - do this lazily
        backgroundScope.launch {
            loadLogsFromFile()
        }
    }
    
    /**
     * Captures logcat output and saves it to the log entries list
     */
    suspend fun captureLogcat() = withContext(Dispatchers.IO) {
        try {
            // Use -t limit to only get recent logs with minimal memory footprint
            val process = Runtime.getRuntime().exec("logcat -d -t $LINES_TO_CAPTURE -v threadtime *:W")
            
            // Process using try-with-resources pattern for auto-closing
            process.inputStream.bufferedReader().use { reader ->
                processLogLines(reader)
            }
            
            // Explicitly destroy the process
            process.destroy()
            
            // Save logs periodically, but don't block the current thread
            saveLogsToFileAsync()
        } catch (e: IOException) {
            Log.e(TAG, "Error capturing logcat: ${e.message}", e)
        }
    }
    
    /**
     * Process log lines more efficiently
     */
    private fun processLogLines(reader: BufferedReader) {
        // Read all lines at once into a list to minimize IO processing
        val logLines = reader.readLines()
        
        // Process lines in batch for better performance
        logLines.asSequence()
            .mapNotNull { LOG_PATTERN.find(it) }
            .map { it.destructured }
            .filter { (level, _, _) -> level == "E" || level == "W" }
            .forEach { (level, tag, message) ->
                addLogEntry(
                    LogEntry(
                        System.currentTimeMillis(),
                        level,
                        tag.trim(),
                        message.trim()
                    )
                )
            }
    }
    
    /**
     * Adds a log entry to the list and trims if necessary
     */
    fun addLogEntry(entry: LogEntry) {
        // Add to front for faster trimming (newer entries first)
        logEntries.add(0, entry)
        
        // Trim the list if it exceeds the maximum size
        if (logEntries.size > MAX_STORED_LOGS) {
            logEntries.subList(MAX_STORED_LOGS, logEntries.size).clear()
        }
    }
    
    /**
     * Get all log entries
     */
    fun getLogEntries(): List<LogEntry> = logEntries.toList()
    
    /**
     * Clear all log entries
     */
    fun clearLogs() {
        logEntries.clear()
        saveLogsToFileAsync()
    }
    
    /**
     * Save logs to file asynchronously
     */
    private fun saveLogsToFileAsync() {
        // Only one write operation at a time
        if (isWritingToFile.compareAndSet(false, true)) {
            backgroundScope.launch {
                try {
                    val file = File(context.filesDir, LOG_FILE_NAME)
                    file.bufferedWriter().use { writer ->
                        for (entry in logEntries) {
                            writer.write("${entry.timestamp}|${entry.level}|${entry.tag}|${entry.message}")
                            writer.newLine()
                        }
                        writer.flush()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error saving logs to file: ${e.message}", e)
                } finally {
                    isWritingToFile.set(false)
                }
            }
        }
    }
    
    /**
     * Load logs from file
     */
    private fun loadLogsFromFile() {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) {
                val entries = mutableListOf<LogEntry>()
                
                file.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.split("|", limit = 4)
                        if (parts.size == 4) {
                            try {
                                val timestamp = parts[0].toLong()
                                val level = parts[1]
                                val tag = parts[2]
                                val message = parts[3]
                                
                                entries.add(LogEntry(timestamp, level, tag, message))
                            } catch (e: NumberFormatException) {
                                Log.e(TAG, "Error parsing log timestamp: ${e.message}", e)
                            }
                        }
                    }
                }
                
                // Replace all entries at once for thread safety
                logEntries.clear()
                logEntries.addAll(entries.take(MAX_STORED_LOGS))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading logs from file: ${e.message}", e)
        }
    }
} 