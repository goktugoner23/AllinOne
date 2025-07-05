package com.example.allinone.feature.transactions.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.allinone.data.Transaction
import com.example.allinone.data.local.RoomCacheManager
import com.example.allinone.feature.transactions.domain.repository.TransactionRepository
import com.example.allinone.firebase.FirebaseManager
import com.example.allinone.firebase.OfflineQueue
import com.example.allinone.utils.NetworkUtils
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TransactionRepository that handles all transaction-related data operations.
 * This replaces the transaction methods from the monolithic FirebaseRepository.
 */
@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val context: Context,
    private val firebaseManager: FirebaseManager,
    private val networkUtils: NetworkUtils,
    private val roomCacheManager: RoomCacheManager,
    private val offlineQueue: OfflineQueue
) : TransactionRepository {

    companion object {
        private const val TAG = "TransactionRepository"
    }

    private val gson = Gson()

    // State management
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    override val transactions: StateFlow<List<Transaction>> = _transactions

    private val _isLoading = MutableLiveData<Boolean>(false)
    override val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    override val errorMessage: LiveData<String?> = _errorMessage

    init {
        // Load initial data from cache
        CoroutineScope(Dispatchers.IO).launch {
            loadFromCache()
        }
    }

    override suspend fun refreshTransactions() {
        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                val transactions = firebaseManager.getTransactions()
                _transactions.value = transactions
                
                // Cache the data
                roomCacheManager.cacheTransactions(transactions)
                
                _isLoading.postValue(false)
                Log.d(TAG, "Refreshed ${transactions.size} transactions")
            } catch (e: Exception) {
                _errorMessage.postValue("Error refreshing transactions: ${e.message}")
                _isLoading.postValue(false)
                Log.e(TAG, "Error refreshing transactions", e)
            }
        }
    }

    override suspend fun insertTransaction(transaction: Transaction) {
        try {
            // Update local state immediately for responsiveness
            val currentTransactions = _transactions.value.toMutableList()
            currentTransactions.add(transaction)
            _transactions.value = currentTransactions

            // Save to cache
            roomCacheManager.cacheTransactions(listOf(transaction))

            // Then update Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.saveTransaction(transaction)
                Log.d(TAG, "Transaction saved to Firebase: ${transaction.id}")
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.TRANSACTION,
                    OfflineQueue.Operation.INSERT,
                    gson.toJson(transaction)
                )
                _errorMessage.postValue("Transaction saved locally. Will sync when network is available.")
                Log.d(TAG, "Transaction queued for offline sync: ${transaction.id}")
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error saving transaction: ${e.message}")
            Log.e(TAG, "Error inserting transaction", e)
        }
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        try {
            // Update local state
            val currentList = _transactions.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == transaction.id }

            if (index != -1) {
                currentList[index] = transaction
            } else {
                currentList.add(transaction)
            }

            _transactions.value = currentList

            // Update cache
            roomCacheManager.cacheTransactions(listOf(transaction))

            // Save to Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.saveTransaction(transaction)
                Log.d(TAG, "Transaction updated in Firebase: ${transaction.id}")
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.TRANSACTION,
                    OfflineQueue.Operation.UPDATE,
                    gson.toJson(transaction)
                )
                _errorMessage.postValue("Transaction updated locally. Will sync when network is available.")
                Log.d(TAG, "Transaction update queued for offline sync: ${transaction.id}")
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error updating transaction: ${e.message}")
            Log.e(TAG, "Error updating transaction", e)
        }
    }

    override suspend fun deleteTransaction(transaction: Transaction) {
        try {
            // Update local state immediately
            val currentTransactions = _transactions.value.toMutableList()
            currentTransactions.removeIf { it.id == transaction.id }
            _transactions.value = currentTransactions

            // Then delete from Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.deleteTransaction(transaction)
                Log.d(TAG, "Transaction deleted from Firebase: ${transaction.id}")
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.TRANSACTION,
                    OfflineQueue.Operation.DELETE,
                    gson.toJson(transaction)
                )
                _errorMessage.postValue("Transaction deleted locally. Will sync when network is available.")
                Log.d(TAG, "Transaction deletion queued for offline sync: ${transaction.id}")
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error deleting transaction: ${e.message}")
            Log.e(TAG, "Error deleting transaction", e)
        }
    }

    override suspend fun deleteTransactionsByRegistrationId(registrationId: Long) {
        try {
            Log.d(TAG, "Deleting transactions for registration ID: $registrationId")

            // Find transactions related to this registration
            val transactionsToDelete = _transactions.value.filter {
                it.relatedRegistrationId == registrationId
            }

            if (transactionsToDelete.isEmpty()) {
                Log.d(TAG, "No transactions found for registration ID: $registrationId")
                return
            }

            Log.d(TAG, "Found ${transactionsToDelete.size} transactions to delete")

            // Delete each transaction
            transactionsToDelete.forEach { transaction ->
                deleteTransaction(transaction)
            }

            // Also clean up from cache
            roomCacheManager.deleteTransactionsByRegistrationId(registrationId)

        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transactions by registration ID: ${e.message}", e)
            _errorMessage.postValue("Error deleting related transactions: ${e.message}")
        }
    }

    override fun getTransactionsByType(isIncome: Boolean): List<Transaction> {
        return _transactions.value.filter { it.isIncome == isIncome }
    }

    override suspend fun getTotalByType(isIncome: Boolean): Double {
        return try {
            // Use Room for efficient calculation
            roomCacheManager.getTotalByType(isIncome)
        } catch (e: Exception) {
            // Fallback to in-memory calculation
            _transactions.value
                .filter { it.isIncome == isIncome }
                .sumOf { it.amount }
        }
    }

    override fun getTransactionById(id: Long): Transaction? {
        return _transactions.value.find { it.id == id }
    }

    override fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * Load transactions from local cache
     */
    private suspend fun loadFromCache() {
        try {
            // Use Room cache with reactive Flow
            roomCacheManager.getCachedTransactionsFlow().collect { cachedTransactions ->
                if (cachedTransactions.isNotEmpty()) {
                    _transactions.value = cachedTransactions
                    Log.d(TAG, "Loaded ${cachedTransactions.size} transactions from cache")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading transactions from cache: ${e.message}")
        }
    }
} 