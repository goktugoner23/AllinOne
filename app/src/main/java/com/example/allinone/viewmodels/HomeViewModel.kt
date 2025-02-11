package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Investment
import com.example.allinone.data.Transaction
import com.example.allinone.data.TransactionDatabase
import com.example.allinone.data.TransactionRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Date

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = TransactionDatabase.getDatabase(application)
    private val transactionDao = database.transactionDao()
    private val investmentDao = database.investmentDao()
    private val repository: TransactionRepository

    init {
        repository = TransactionRepository(transactionDao)
    }

    val totalIncome: LiveData<Double> = transactionDao.getTotalIncome()
        .map { total -> total ?: 0.0 }
        .asLiveData()
        
    val totalExpense: LiveData<Double> = transactionDao.getTotalExpense()
        .map { total -> total ?: 0.0 }
        .asLiveData()
        
    val balance: LiveData<Double> = combine(
        transactionDao.getTotalIncome(),
        transactionDao.getTotalExpense()
    ) { income, expense ->
        (income ?: 0.0) - (expense ?: 0.0)
    }.asLiveData()

    val allTransactions: LiveData<List<Transaction>> = transactionDao.getAllTransactions().asLiveData()

    fun addIncome(amount: Double, type: String, description: String?) {
        viewModelScope.launch {
            val transaction = Transaction(
                amount = amount,
                type = type,
                description = description,
                isIncome = true,
                date = Date(),
                category = type
            )
            transactionDao.insertTransaction(transaction)

            // If this is an investment return, update the investment
            if (type == "Investment" && description?.startsWith("Return from") == true) {
                val investmentName = description.removePrefix("Return from").trim()
                val investments = investmentDao.getAllInvestments().first()
                investments.find { it.name == investmentName }?.let { investment ->
                    val updatedInvestment = investment.copy(
                        currentValue = investment.currentValue + amount
                    )
                    investmentDao.updateInvestment(updatedInvestment)
                }
            }
        }
    }

    fun addExpense(amount: Double, type: String, description: String?) {
        viewModelScope.launch {
            val transaction = Transaction(
                amount = amount,
                type = type,
                description = description,
                isIncome = false,
                date = Date(),
                category = type
            )
            transactionDao.insertTransaction(transaction)
        }
    }

    fun getAllInvestments(): LiveData<List<Investment>> {
        return investmentDao.getAllInvestments().asLiveData()
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionDao.deleteTransaction(transaction)
        }
    }
} 