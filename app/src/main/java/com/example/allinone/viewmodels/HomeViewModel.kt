package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.*
import com.example.allinone.data.*
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application)
    
    private val _allTransactions = MutableLiveData<List<Transaction>>(emptyList())
    val allTransactions: LiveData<List<Transaction>> = _allTransactions
    
    init {
        // Collect transactions from the repository flow
        viewModelScope.launch {
            repository.transactions.collect { transactions ->
                _allTransactions.value = transactions
            }
        }
    }

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

    // Investment-related code
    private val _selectedInvestment = MutableLiveData<Investment?>()
    val selectedInvestment: LiveData<Investment?> = _selectedInvestment

    fun setSelectedInvestment(investment: Investment) {
        _selectedInvestment.value = investment
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }
    
    fun refreshData() {
        viewModelScope.launch {
            repository.refreshAllData()
        }
    }
} 