package com.example.allinone.feature.transactions.domain.repository

import androidx.lifecycle.LiveData
import com.example.allinone.data.Transaction
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository interface for transaction operations.
 * This defines the contract that the data layer must implement.
 */
interface TransactionRepository {
    
    // Reactive data streams
    val transactions: StateFlow<List<Transaction>>
    val isLoading: LiveData<Boolean>
    val errorMessage: LiveData<String?>
    
    // CRUD operations
    suspend fun refreshTransactions()
    suspend fun insertTransaction(transaction: Transaction)
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(transaction: Transaction)
    suspend fun deleteTransactionsByRegistrationId(registrationId: Long)
    
    // Query operations
    fun getTransactionsByType(isIncome: Boolean): List<Transaction>
    suspend fun getTotalByType(isIncome: Boolean): Double
    fun getTransactionById(id: Long): Transaction?
    
    // Utility operations
    fun clearErrorMessage()
} 