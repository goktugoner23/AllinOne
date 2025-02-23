package com.example.allinone.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE isIncome = :isIncome ORDER BY date DESC")
    fun getTransactionsByType(isIncome: Boolean): Flow<List<Transaction>>

    @Query("SELECT SUM(amount) FROM transactions WHERE isIncome = :isIncome")
    fun getTotalByType(isIncome: Boolean): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE isIncome = 1")
    fun getTotalIncome(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE isIncome = 0")
    fun getTotalExpense(): Flow<Double?>

    @Insert
    suspend fun insertTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Query("SELECT COALESCE(SUM(CASE WHEN isIncome = 1 THEN amount ELSE -amount END), 0.0) FROM transactions")
    fun getBalance(): Flow<Double>

    @Query("UPDATE transactions SET amount = :newAmount, description = :newDescription WHERE description = :oldDescription")
    suspend fun updateTransactionByDescription(oldDescription: String, newAmount: Double, newDescription: String)
    
    @Query("DELETE FROM transactions WHERE description = :description")
    suspend fun deleteTransactionByDescription(description: String)
} 