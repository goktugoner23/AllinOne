package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.*
import com.example.allinone.data.Transaction
import com.example.allinone.data.TransactionDatabase
import com.example.allinone.data.TransactionRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = TransactionDatabase.getDatabase(application)
    private val transactionDao = database.transactionDao()
    private val repository = TransactionRepository(transactionDao)

    private val _filterType = MutableStateFlow(TransactionFilter.ALL)
    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)

    val filteredTransactions = combine(
        _filterType,
        _sortOrder,
        transactionDao.getAllTransactions()
    ) { filter, sort, transactions ->
        var filtered = when (filter) {
            TransactionFilter.ALL -> transactions
            TransactionFilter.INCOME -> transactions.filter { it.isIncome }
            TransactionFilter.EXPENSE -> transactions.filter { !it.isIncome }
        }

        filtered = when (sort) {
            SortOrder.DATE_DESC -> filtered.sortedByDescending { it.date }
            SortOrder.DATE_ASC -> filtered.sortedBy { it.date }
            SortOrder.AMOUNT_DESC -> filtered.sortedByDescending { it.amount }
            SortOrder.AMOUNT_ASC -> filtered.sortedBy { it.amount }
        }
        
        filtered
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

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionDao.deleteTransaction(transaction)
        }
    }

    enum class TransactionFilter {
        ALL, INCOME, EXPENSE
    }

    enum class SortOrder {
        DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC
    }
} 