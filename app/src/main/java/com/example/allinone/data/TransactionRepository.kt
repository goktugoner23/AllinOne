package com.example.allinone.data

import kotlinx.coroutines.flow.Flow
import java.util.Date

class TransactionRepository(private val transactionDao: TransactionDao) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    suspend fun insertTransaction(
        amount: Double,
        type: String,
        description: String?,
        isIncome: Boolean,
        category: String
    ) {
        val transaction = Transaction(
            amount = amount,
            type = type,
            description = description ?: "",
            isIncome = isIncome,
            date = Date(),
            category = category
        )
        transactionDao.insertTransaction(transaction)
    }

    fun getTransactionsByType(isIncome: Boolean): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByType(isIncome)
    }

    fun getTotalByType(isIncome: Boolean): Flow<Double?> {
        return transactionDao.getTotalByType(isIncome)
    }

    suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction)
    }
} 