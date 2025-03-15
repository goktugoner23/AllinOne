package com.example.allinone.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.allinone.data.db.converters.DateConverter
import java.util.Date
import java.util.UUID

/**
 * Entity representing a pending operation for offline synchronization.
 * 
 * This entity stores operations that need to be synchronized with
 * the Firebase backend when the device goes online.
 */
@Entity(tableName = "pending_operations")
@TypeConverters(DateConverter::class)
data class PendingOperationEntity(
    /**
     * Unique identifier for the pending operation
     */
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    /**
     * Type of operation (e.g., "INSERT", "UPDATE", "DELETE")
     */
    val operationType: String,
    
    /**
     * Type of entity being operated on (e.g., "Event", "Student")
     */
    val entityType: String,
    
    /**
     * ID of the entity being operated on
     */
    val entityId: String,
    
    /**
     * JSON serialized data of the entity
     */
    val entityData: String,
    
    /**
     * Timestamp when the operation was created
     */
    val timestamp: Date = Date(),
    
    /**
     * Number of retry attempts
     */
    val retryCount: Int = 0,
    
    /**
     * Last error message, if any
     */
    val lastError: String? = null
) {
    /**
     * Possible operation types
     */
    companion object {
        const val OPERATION_INSERT = "INSERT"
        const val OPERATION_UPDATE = "UPDATE"
        const val OPERATION_DELETE = "DELETE"
    }
} 