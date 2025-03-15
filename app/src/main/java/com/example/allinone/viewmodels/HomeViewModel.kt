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
    
    private val _allInvestments = MutableLiveData<List<Investment>>(emptyList())
    val allInvestments: LiveData<List<Investment>> = _allInvestments
    
    // Combined balance that includes both transactions and investments
    private val _combinedBalance = MediatorLiveData<Triple<Double, Double, Double>>()
    val combinedBalance: LiveData<Triple<Double, Double, Double>> = _combinedBalance
    
    init {
        // Collect transactions from the repository flow
        viewModelScope.launch {
            repository.transactions.collect { transactions ->
                _allTransactions.value = transactions
            }
        }
        
        // Collect investments from the repository flow
        viewModelScope.launch {
            repository.investments.collect { investments ->
                _allInvestments.value = investments
            }
        }
        
        // Calculate combined balance whenever transactions or investments change
        _combinedBalance.addSource(_allTransactions) { updateCombinedBalance() }
        _combinedBalance.addSource(_allInvestments) { updateCombinedBalance() }
    }
    
    private fun updateCombinedBalance() {
        val transactions = _allTransactions.value ?: emptyList()
        val investments = _allInvestments.value ?: emptyList()
        
        val totalIncome = transactions.filter { it.isIncome }.sumOf { it.amount }
        val totalExpense = transactions.filter { !it.isIncome }.sumOf { it.amount }
        
        // Include investments in total expense
        val totalInvestments = investments.sumOf { it.amount }
        val adjustedTotalExpense = totalExpense + totalInvestments
        
        val balance = totalIncome - adjustedTotalExpense
        
        _combinedBalance.value = Triple(totalIncome, adjustedTotalExpense, balance)
    }

    fun addTransaction(
        amount: Double,
        type: String,
        description: String?,
        isIncome: Boolean,
        category: String
    ) {
        viewModelScope.launch {
            // Add transaction to Firebase through repository
            repository.insertTransaction(
                amount = amount,
                type = type,
                description = description,
                isIncome = isIncome,
                category = category
            )
            
            // Force refresh data to ensure UI consistency
            repository.refreshTransactions()
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
            // Delete from repository
            repository.deleteTransaction(transaction)
            
            // Force refresh to ensure UI consistency
            repository.refreshTransactions()
        }
    }
    
    fun refreshData() {
        viewModelScope.launch {
            repository.refreshAllData()
        }
    }
} 