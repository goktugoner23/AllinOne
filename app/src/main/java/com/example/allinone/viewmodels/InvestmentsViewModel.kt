package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Investment
import com.example.allinone.data.TransactionDatabase
import com.example.allinone.data.Transaction
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Date

class InvestmentsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = TransactionDatabase.getDatabase(application)
    private val investmentDao = database.investmentDao()
    private val transactionDao = database.transactionDao()

    val allInvestments: LiveData<List<Investment>> = investmentDao.getAllInvestments().asLiveData()
    
    val totalInvestment: LiveData<Double> = investmentDao.getTotalInvestment()
        .map { total -> total ?: 0.0 }
        .asLiveData()

    val totalProfitLoss: LiveData<Double> = investmentDao.getTotalProfitLoss()
        .map { total -> total ?: 0.0 }
        .asLiveData()

    fun addInvestment(
        name: String,
        amount: Double,
        type: String,
        currentValue: Double,
        description: String?,
        imageUri: String?
    ) {
        viewModelScope.launch {
            val investment = Investment(
                name = name,
                amount = amount,
                type = type,
                currentValue = currentValue,
                description = description,
                imageUri = imageUri,
                date = Date()
            )
            investmentDao.insertInvestment(investment)

            val transaction = Transaction(
                amount = amount,
                type = "Investment",
                description = "Investment in $name",
                isIncome = false,
                date = Date(),
                category = type
            )
            transactionDao.insertTransaction(transaction)
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
} 