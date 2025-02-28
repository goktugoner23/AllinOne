package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.HistoryItem
import com.example.allinone.data.Investment
import com.example.allinone.data.Note
import com.example.allinone.data.Transaction
import com.example.allinone.data.WTStudent
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application)
    
    private val _historyItems = MutableStateFlow<List<HistoryItem>>(emptyList())
    val historyItems: StateFlow<List<HistoryItem>> = _historyItems
    
    init {
        // Combine all data sources into a single list of history items
        viewModelScope.launch {
            combine(
                repository.transactions,
                repository.investments,
                repository.notes,
                repository.students
            ) { transactions, investments, notes, students ->
                val historyItems = mutableListOf<HistoryItem>()
                
                // Add transactions
                historyItems.addAll(transactions.map { it.toHistoryItem() })
                
                // Add investments
                historyItems.addAll(investments.map { it.toHistoryItem() })
                
                // Add notes
                historyItems.addAll(notes.map { it.toHistoryItem() })
                
                // Add students
                historyItems.addAll(students.map { it.toHistoryItem() })
                
                // Sort by date (newest first)
                historyItems.sortByDescending { it.date }
                
                historyItems
            }.collect {
                _historyItems.value = it
            }
        }
        
        // Initial data load
        refreshAllData()
    }
    
    fun refreshAllData() {
        viewModelScope.launch {
            repository.refreshAllData()
        }
    }
    
    fun deleteItem(item: HistoryItem) {
        viewModelScope.launch {
            when (item.itemType) {
                HistoryItem.ItemType.TRANSACTION -> {
                    val transaction = Transaction(
                        id = item.id,
                        amount = item.amount ?: 0.0,
                        type = item.type,
                        description = item.description,
                        isIncome = item.type == "Income",
                        date = item.date,
                        category = ""
                    )
                    repository.deleteTransaction(transaction)
                }
                HistoryItem.ItemType.INVESTMENT -> {
                    val investment = Investment(
                        id = item.id,
                        name = item.title,
                        amount = item.amount ?: 0.0,
                        type = item.type,
                        description = item.description,
                        imageUri = item.imageUri,
                        date = item.date,
                        profitLoss = 0.0
                    )
                    repository.deleteInvestment(investment)
                }
                HistoryItem.ItemType.NOTE -> {
                    val note = Note(
                        id = item.id,
                        title = item.title,
                        content = item.description,
                        date = item.date,
                        imageUri = item.imageUri
                    )
                    repository.deleteNote(note)
                }
                HistoryItem.ItemType.STUDENT -> {
                    val student = WTStudent(
                        id = item.id,
                        name = item.title,
                        startDate = item.date,
                        endDate = item.date,
                        amount = item.amount ?: 0.0,
                        isPaid = false,
                        paymentDate = null,
                        attachmentUri = item.imageUri
                    )
                    repository.deleteStudent(student)
                }
            }
        }
    }
    
    // Extension functions to convert data objects to HistoryItem
    private fun Transaction.toHistoryItem(): HistoryItem {
        return HistoryItem(
            id = id,
            title = if (isIncome) "Income: $type" else "Expense: $type",
            description = description,
            date = date,
            amount = amount,
            type = if (isIncome) "Income" else "Expense",
            itemType = HistoryItem.ItemType.TRANSACTION
        )
    }
    
    private fun Investment.toHistoryItem(): HistoryItem {
        return HistoryItem(
            id = id,
            title = name,
            description = description ?: "Investment",
            date = date,
            amount = amount,
            type = type,
            imageUri = imageUri,
            itemType = HistoryItem.ItemType.INVESTMENT
        )
    }
    
    private fun Note.toHistoryItem(): HistoryItem {
        return HistoryItem(
            id = id,
            title = title,
            description = content,
            date = date,
            type = "Note",
            imageUri = imageUri,
            itemType = HistoryItem.ItemType.NOTE
        )
    }
    
    private fun WTStudent.toHistoryItem(): HistoryItem {
        return HistoryItem(
            id = id,
            title = name,
            description = if (isPaid) "Paid" else "Not Paid",
            date = startDate,
            amount = amount,
            type = "Student",
            imageUri = attachmentUri,
            itemType = HistoryItem.ItemType.STUDENT
        )
    }
} 