package com.example.allinone.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.allinone.data.db.entities.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for managing pending operations.
 * 
 * This DAO provides methods to insert, query, update, and delete pending
 * operations for offline synchronization.
 */
@Dao
interface PendingOperationDao {
    
    /**
     * Inserts a new pending operation
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: PendingOperationEntity): Long
    
    /**
     * Updates an existing pending operation
     */
    @Update
    suspend fun update(operation: PendingOperationEntity)
    
    /**
     * Deletes a pending operation
     */
    @Delete
    suspend fun delete(operation: PendingOperationEntity)
    
    /**
     * Gets all pending operations, ordered by timestamp (oldest first)
     */
    @Query("SELECT * FROM pending_operations ORDER BY timestamp ASC")
    fun getAllPendingOperations(): Flow<List<PendingOperationEntity>>
    
    /**
     * Gets pending operations for a specific entity
     */
    @Query("SELECT * FROM pending_operations WHERE entityType = :entityType AND entityId = :entityId ORDER BY timestamp ASC")
    fun getPendingOperationsForEntity(entityType: String, entityId: String): Flow<List<PendingOperationEntity>>
    
    /**
     * Gets pending operations by type
     */
    @Query("SELECT * FROM pending_operations WHERE operationType = :operationType ORDER BY timestamp ASC")
    fun getPendingOperationsByType(operationType: String): Flow<List<PendingOperationEntity>>
    
    /**
     * Increments the retry count for a pending operation
     */
    @Query("UPDATE pending_operations SET retryCount = retryCount + 1, lastError = :errorMessage WHERE id = :operationId")
    suspend fun incrementRetryCount(operationId: String, errorMessage: String)
    
    /**
     * Deletes all pending operations
     */
    @Query("DELETE FROM pending_operations")
    suspend fun deleteAllPendingOperations()
    
    /**
     * Gets the count of pending operations
     */
    @Query("SELECT COUNT(*) FROM pending_operations")
    fun getPendingOperationCount(): Flow<Int>
} 