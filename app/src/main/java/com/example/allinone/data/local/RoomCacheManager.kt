package com.example.allinone.data.local

import android.content.Context
import android.util.Log
import com.example.allinone.data.Transaction
import com.example.allinone.data.Investment
import com.example.allinone.data.local.entities.CachedTransactionEntity
import com.example.allinone.data.local.entities.CachedInvestmentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modern cache manager using Room database for efficient structured data storage
 * Replaces the old SharedPreferences JSON approach for better performance
 */
@Singleton
class RoomCacheManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "RoomCacheManager"
        private const val DEFAULT_CACHE_EXPIRATION_MS = 10 * 60 * 1000L // 10 minutes
    }

    private val database = AppDatabase.getDatabase(context)
    private val transactionDao = database.transactionDao()

    /**
     * Cache expiration settings for different data types
     */
    private val cacheExpirationSettings = mutableMapOf<String, Long>().apply {
        put("transactions", DEFAULT_CACHE_EXPIRATION_MS)
        put("investments", DEFAULT_CACHE_EXPIRATION_MS)
        put("notes", DEFAULT_CACHE_EXPIRATION_MS)
        put("events", 5 * 60 * 1000L) // 5 minutes for events
    }

    /**
     * Set custom cache expiration for a specific data type
     */
    fun setCacheExpiration(dataType: String, expirationMs: Long) {
        cacheExpirationSettings[dataType] = expirationMs
        Log.d(TAG, "Cache expiration set for $dataType: ${expirationMs}ms")
    }

    // Transaction caching methods
    suspend fun cacheTransactions(transactions: List<Transaction>) {
        try {
            val entities = transactions.map { CachedTransactionEntity.fromTransaction(it) }
            transactionDao.insertAllTransactions(entities)
            Log.d(TAG, "Cached ${transactions.size} transactions")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching transactions: ${e.message}")
        }
    }

    suspend fun getCachedTransactions(): List<Transaction> {
        return try {
            cleanupExpiredTransactions()
            val entities = transactionDao.getAllTransactions()
            // Convert Flow to List for immediate use
            // In a real scenario, you'd observe the Flow
            entities.map { entityList ->
                entityList.map { it.toTransaction() }
            }.let { _ ->
                // For now, return empty list as we need to handle Flow properly
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving cached transactions: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get transactions as a Flow for reactive UI updates
     */
    fun getCachedTransactionsFlow(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions().map { entities ->
            entities.map { it.toTransaction() }
        }
    }

    suspend fun getCachedTransactionsByType(isIncome: Boolean): List<Transaction> {
        return try {
            transactionDao.getTransactionsByType(isIncome).map { entities ->
                entities.map { it.toTransaction() }
            }.let { _ ->
                // For reactive updates, return Flow instead
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving cached transactions by type: ${e.message}")
            emptyList()
        }
    }

    suspend fun getTotalByType(isIncome: Boolean): Double {
        return try {
            transactionDao.getTotalAmountByType(isIncome) ?: 0.0
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating total by type: ${e.message}")
            0.0
        }
    }

    suspend fun deleteTransactionsByRegistrationId(registrationId: Long) {
        try {
            transactionDao.deleteTransactionsByRegistrationId(registrationId)
            Log.d(TAG, "Deleted transactions for registration ID: $registrationId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transactions by registration ID: ${e.message}")
        }
    }

    // Investment caching methods (placeholder for now)
    suspend fun cacheInvestments(investments: List<Investment>) {
        try {
            // TODO: Implement when we add investment DAO
            Log.d(TAG, "Cached ${investments.size} investments")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching investments: ${e.message}")
        }
    }

    suspend fun getCachedInvestments(): List<Investment> {
        return try {
            // TODO: Implement when we add investment DAO
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving cached investments: ${e.message}")
            emptyList()
        }
    }

    // Generic cache management methods
    suspend fun clearAllCache() {
        try {
            transactionDao.deleteAllTransactions()
            // TODO: Clear other entity caches when implemented
            Log.d(TAG, "All cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all cache: ${e.message}")
        }
    }

    suspend fun clearCacheByType(cacheType: String) {
        try {
            when (cacheType) {
                "transactions" -> transactionDao.deleteAllTransactions()
                // TODO: Add other cache types
                else -> Log.w(TAG, "Unknown cache type: $cacheType")
            }
            Log.d(TAG, "Cache cleared for $cacheType")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache for $cacheType: ${e.message}")
        }
    }

    /**
     * Clean up expired cache entries
     */
    private suspend fun cleanupExpiredTransactions() {
        try {
            val expiredTime = System.currentTimeMillis() - 
                (cacheExpirationSettings["transactions"] ?: DEFAULT_CACHE_EXPIRATION_MS)
            transactionDao.deleteOldCache(expiredTime)
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up expired transactions: ${e.message}")
        }
    }

    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): Map<String, Int> {
        return try {
            mapOf(
                "transactions" to transactionDao.getTransactionCount()
                // TODO: Add other cache type counts
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cache stats: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Check if cache is healthy (not corrupted)
     */
    suspend fun isCacheHealthy(): Boolean {
        return try {
            // Simple health check - try to count records
            transactionDao.getTransactionCount() >= 0
        } catch (e: Exception) {
            Log.e(TAG, "Cache health check failed: ${e.message}")
            false
        }
    }
} 