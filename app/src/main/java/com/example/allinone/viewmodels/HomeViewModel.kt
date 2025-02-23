package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.*
import com.example.allinone.data.*
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = TransactionDatabase.getDatabase(application)
    private val transactionDao = database.transactionDao()
    private val repository = TransactionRepository(transactionDao)

    val allTransactions: LiveData<List<Transaction>> = repository.allTransactions.asLiveData()

    fun addTransaction(
        amount: Double,
        type: String,
        description: String?,
        isIncome: Boolean,
        category: String
    ) {
        viewModelScope.launch {
            repository.insertTransaction(
                amount = amount,
                type = type,
                description = description,
                isIncome = isIncome,
                category = category
            )
        }
    }

    // Remove or fix investment-related code if not needed
    private val _selectedInvestment = MutableLiveData<Investment?>()
    val selectedInvestment: LiveData<Investment?> = _selectedInvestment

    fun setSelectedInvestment(investment: Investment) {
        _selectedInvestment.value = investment
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionDao.deleteTransaction(transaction)
        }
    }
} 