package com.example.allinone.data

import kotlinx.coroutines.flow.Flow
import java.util.Date

class TransactionRepository(private val transactionDao: TransactionDao) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    suspend fun insertTransaction(
        type: String,
        category: String,
        amount: Double,
        description: String?,
        isIncome: Boolean
    ) {
        val transaction = Transaction(
            type = type,
            category = category,
            amount = amount,
            description = description,
            date = Date(),
            isIncome = isIncome
        )
        transactionDao.insertTransaction(transaction)
    }

    fun getTransactionsByType(isIncome: Boolean): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByType(isIncome)
    }

    fun getTotalByType(isIncome: Boolean): Flow<Double?> {
        return transactionDao.getTotalByType(isIncome)
    }
} 