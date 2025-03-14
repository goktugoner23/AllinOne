package com.example.allinone.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.example.allinone.data.*
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Date

class InvestmentsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application)
    
    private val _allInvestments = MutableLiveData<List<Investment>>(emptyList())
    val allInvestments: LiveData<List<Investment>> = _allInvestments
    
    private val _totalInvestment = MutableLiveData<Double>(0.0)
    val totalInvestment: LiveData<Double> = _totalInvestment
    
    private val _totalProfitLoss = MutableLiveData<Double>(0.0)
    val totalProfitLoss: LiveData<Double> = _totalProfitLoss
    
    init {
        // Collect investments from the repository flow
        viewModelScope.launch {
            repository.investments.collect { investments ->
                _allInvestments.value = investments
                _totalInvestment.value = investments.sumOf { it.amount }
                _totalProfitLoss.value = investments.sumOf { it.profitLoss }
            }
        }
    }

    fun addInvestment(investment: Investment) {
        viewModelScope.launch {
            repository.insertInvestment(investment)
        }
    }

    fun updateInvestment(investment: Investment) {
        viewModelScope.launch {
            repository.updateInvestment(investment)
        }
    }

    fun deleteInvestment(investment: Investment) {
        viewModelScope.launch {
            repository.deleteInvestment(investment)
        }
    }

    fun updateInvestmentAndTransaction(oldInvestment: Investment, newInvestment: Investment) {
        viewModelScope.launch {
            repository.updateInvestment(newInvestment)
            
            // For backward compatibility: find and update any corresponding transaction
            // This handles investments created before the code change
            val transactions = repository.transactions.value
            val matchingTransaction = transactions.find { 
                it.description.contains(oldInvestment.name) && 
                it.type == "Investment" 
            }
            
            if (matchingTransaction != null) {
                val updatedTransaction = matchingTransaction.copy(
                    amount = newInvestment.amount,
                    description = "Investment in ${newInvestment.name}",
                    category = newInvestment.type
                )
                repository.updateTransaction(updatedTransaction)
            }
        }
    }
    
    fun deleteInvestmentAndTransaction(investment: Investment) {
        viewModelScope.launch {
            // First step: Delete the investment
            repository.deleteInvestment(investment)
            
            // Second step: Find and delete the corresponding transaction with exact matching
            // This handles investments created before the code change
            val transactions = repository.transactions.value
            
            // Find a transaction that exactly matches this investment (using exact ID or exact name)
            val matchingTransaction = transactions.find { transaction ->
                transaction.type == "Investment" && 
                (transaction.description == "Investment in ${investment.name}" || 
                 transaction.description == "Investment: ${investment.name}")
            }
            
            // Only delete the transaction if we found an exact match
            if (matchingTransaction != null) {
                repository.deleteTransaction(matchingTransaction)
                
                // Log the deletion to make troubleshooting easier
                Log.d("InvestmentsViewModel", "Deleted matching transaction: ${matchingTransaction.description} with ID: ${matchingTransaction.id}")
            }
            
            // Explicitly trigger data refresh to ensure UI is updated correctly
            refreshData()
        }
    }
    
    fun refreshData() {
        viewModelScope.launch {
            repository.refreshAllData()
        }
    }
} 