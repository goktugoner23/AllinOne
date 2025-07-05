package com.example.allinone.data.local.dao

import androidx.room.*
import com.example.allinone.data.local.entities.CachedTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedTransactionDao {
    
    @Query("SELECT * FROM cached_transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<CachedTransactionEntity>>
    
    @Query("SELECT * FROM cached_transactions WHERE isIncome = :isIncome ORDER BY date DESC")
    fun getTransactionsByType(isIncome: Boolean): Flow<List<CachedTransactionEntity>>
    
    @Query("SELECT * FROM cached_transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): CachedTransactionEntity?
    
    @Query("SELECT * FROM cached_transactions WHERE relatedRegistrationId = :registrationId")
    suspend fun getTransactionsByRegistrationId(registrationId: Long): List<CachedTransactionEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: CachedTransactionEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTransactions(transactions: List<CachedTransactionEntity>)
    
    @Update
    suspend fun updateTransaction(transaction: CachedTransactionEntity)
    
    @Delete
    suspend fun deleteTransaction(transaction: CachedTransactionEntity)
    
    @Query("DELETE FROM cached_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)
    
    @Query("DELETE FROM cached_transactions WHERE relatedRegistrationId = :registrationId")
    suspend fun deleteTransactionsByRegistrationId(registrationId: Long)
    
    @Query("DELETE FROM cached_transactions")
    suspend fun deleteAllTransactions()
    
    @Query("SELECT COUNT(*) FROM cached_transactions")
    suspend fun getTransactionCount(): Int
    
    @Query("SELECT SUM(amount) FROM cached_transactions WHERE isIncome = :isIncome")
    suspend fun getTotalAmountByType(isIncome: Boolean): Double?
    
    @Query("DELETE FROM cached_transactions WHERE cachedAt < :cutoffTime")
    suspend fun deleteOldCache(cutoffTime: Long)
} 