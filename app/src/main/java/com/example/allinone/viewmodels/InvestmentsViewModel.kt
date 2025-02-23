package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.*
import com.example.allinone.data.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class InvestmentsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = TransactionDatabase.getDatabase(application)
    private val investmentDao = database.investmentDao()
    private val transactionDao = database.transactionDao()

    val allInvestments: LiveData<List<Investment>> = investmentDao.getAllInvestments().asLiveData()
    
    val totalInvestment: LiveData<Double> = investmentDao.getTotalInvestment()
        .map { it ?: 0.0 }
        .asLiveData()
        
    val totalProfitLoss: LiveData<Double> = investmentDao.getTotalProfitLoss()
        .map { it ?: 0.0 }
        .asLiveData()

    fun addInvestment(investment: Investment) {
        viewModelScope.launch {
            investmentDao.insertInvestment(investment)
        }
    }

    fun updateInvestment(investment: Investment) {
        viewModelScope.launch {
            investmentDao.updateInvestment(investment)
        }
    }

    fun deleteInvestment(investment: Investment) {
        viewModelScope.launch {
            investmentDao.deleteInvestment(investment)
        }
    }

    fun updateInvestmentAndTransaction(oldInvestment: Investment, newInvestment: Investment) {
        viewModelScope.launch {
            investmentDao.updateInvestment(newInvestment)
            
            val transaction = Transaction(
                amount = newInvestment.amount,
                type = "Investment",
                description = "Investment in ${newInvestment.name}",
                isIncome = false,
                date = newInvestment.date,
                category = newInvestment.type
            )
            transactionDao.updateTransactionByDescription(
                oldDescription = "Investment in ${oldInvestment.name} (${oldInvestment.type})",
                newAmount = newInvestment.amount,
                newDescription = "Investment in ${newInvestment.name}"
            )
        }
    }
    
    fun deleteInvestmentAndTransaction(investment: Investment) {
        viewModelScope.launch {
            investmentDao.deleteInvestment(investment)
            
            transactionDao.deleteTransactionByDescription(
                "Investment in ${investment.name} (${investment.type})"
            )
        }
    }
} 