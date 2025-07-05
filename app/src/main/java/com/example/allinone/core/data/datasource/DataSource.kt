package com.example.allinone.core.data.datasource

import kotlinx.coroutines.flow.Flow

/**
 * Generic DataSource interface for consistent data access patterns
 * Supports both remote (Firebase) and local (Room) implementations
 */
interface DataSource<T> {
    suspend fun getAll(): List<T>
    suspend fun getById(id: Long): T?
    suspend fun save(item: T)
    suspend fun saveAll(items: List<T>)
    suspend fun delete(item: T)
    suspend fun deleteById(id: Long)
    suspend fun deleteAll()
    suspend fun getCount(): Int
}

/**
 * Reactive DataSource interface for real-time updates
 */
interface ReactiveDataSource<T> : DataSource<T> {
    fun getAllAsFlow(): Flow<List<T>>
    fun getByIdAsFlow(id: Long): Flow<T?>
}

/**
 * SearchableDataSource for entities that support text search
 */
interface SearchableDataSource<T> : ReactiveDataSource<T> {
    fun search(query: String): Flow<List<T>>
}

/**
 * Remote DataSource marker interface
 * Implementations should handle network operations and Firebase communication
 */
interface RemoteDataSource<T> : DataSource<T>

/**
 * Local DataSource marker interface  
 * Implementations should handle Room database operations
 */
interface LocalDataSource<T> : ReactiveDataSource<T> {
    suspend fun clearExpiredCache(expiredTime: Long)
    suspend fun isHealthy(): Boolean
} 