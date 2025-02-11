package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.*
import com.example.allinone.data.Transaction
import com.example.allinone.data.TransactionDatabase
import com.example.allinone.data.TransactionRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TransactionRepository
    private val _filterType = MutableLiveData<TransactionFilter>(TransactionFilter.ALL)
    private val _sortOrder = MutableLiveData(SortOrder.DATE_DESC)

    init {
        val transactionDao = TransactionDatabase.getDatabase(application).transactionDao()
        repository = TransactionRepository(transactionDao)
    }

    val allTransactions: LiveData<List<Transaction>> = combine(
        _filterType.asFlow(),
        _sortOrder.asFlow()
    ) { filter, sort ->
        Pair(filter, sort)
    }.map { (filter, sort) ->
        val transactions = when (filter) {
            TransactionFilter.ALL -> repository.allTransactions.first()
            TransactionFilter.INCOME -> repository.getTransactionsByType(true).first()
            TransactionFilter.EXPENSE -> repository.getTransactionsByType(false).first()
        }

        when (sort) {
            SortOrder.DATE_DESC -> transactions.sortedByDescending { it.date }
            SortOrder.DATE_ASC -> transactions.sortedBy { it.date }
            SortOrder.AMOUNT_DESC -> transactions.sortedByDescending { it.amount }
            SortOrder.AMOUNT_ASC -> transactions.sortedBy { it.amount }
        }
    }.asLiveData()

    val totalIncome: LiveData<Double> = repository.getTotalByType(true)
        .map { it ?: 0.0 }
        .asLiveData()

    val totalExpense: LiveData<Double> = repository.getTotalByType(false)
        .map { it ?: 0.0 }
        .asLiveData()

    val balance: LiveData<Double> = combine(
        totalIncome.asFlow(),
        totalExpense.asFlow()
    ) { income, expense ->
        income - expense
    }.asLiveData()

    fun setFilter(filter: TransactionFilter) {
        _filterType.value = filter
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    enum class TransactionFilter {
        ALL, INCOME, EXPENSE
    }

    enum class SortOrder {
        DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC
    }
} 