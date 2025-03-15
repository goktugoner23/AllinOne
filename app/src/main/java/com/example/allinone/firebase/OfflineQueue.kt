package com.example.allinone.firebase

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * A queue for storing operations that need to be performed when the device comes back online.
 */
class OfflineQueue(context: Context) {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "offline_queue", Context.MODE_PRIVATE
    )
    private val gson = Gson()
    
    // Operation types
    enum class Operation {
        INSERT, UPDATE, DELETE
    }
    
    // Data types
    enum class DataType {
        TRANSACTION,
        INVESTMENT,
        NOTE,
        STUDENT,
        EVENT,
        WT_LESSON,
        REGISTRATION
    }
    
    // Queue item
    data class QueueItem(
        val id: String = UUID.randomUUID().toString(),
        val operation: Operation,
        val dataType: DataType,
        val jsonData: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Add an item to the queue
     */
    fun enqueue(dataType: DataType, operation: Operation, jsonData: String?) {
        val queueItem = QueueItem(
            operation = operation,
            dataType = dataType,
            jsonData = jsonData
        )
        
        val queue = getQueue().toMutableList()
        queue.add(queueItem)
        saveQueue(queue)
    }
    
    /**
     * Get all operations in the queue
     */
    fun getQueue(): List<QueueItem> {
        val queueJson = sharedPreferences.getString("queue", null) ?: return emptyList()
        val type = object : TypeToken<List<QueueItem>>() {}.type
        return gson.fromJson(queueJson, type) ?: emptyList()
    }
    
    /**
     * Save the queue to SharedPreferences
     */
    private fun saveQueue(queue: List<QueueItem>) {
        val queueJson = gson.toJson(queue)
        sharedPreferences.edit().putString("queue", queueJson).apply()
    }
    
    /**
     * Remove an operation from the queue
     */
    fun removeOperation(id: String) {
        val queue = getQueue().toMutableList()
        queue.removeIf { it.id == id }
        saveQueue(queue)
    }
    
    /**
     * Clear the entire queue
     */
    fun clearQueue() {
        sharedPreferences.edit().remove("queue").apply()
    }
    
    /**
     * Process the queue with the given processor function
     */
    fun processQueue(processor: suspend (QueueItem) -> Boolean) {
        val queue = getQueue()
        if (queue.isEmpty()) return
        
        CoroutineScope(Dispatchers.IO).launch {
            queue.forEach { item ->
                val success = processor(item)
                if (success) {
                    removeOperation(item.id)
                }
            }
        }
    }
} 