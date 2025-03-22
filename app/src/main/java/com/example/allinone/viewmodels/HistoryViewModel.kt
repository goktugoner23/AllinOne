package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.HistoryItem
import com.example.allinone.data.Investment
import com.example.allinone.data.Note
import com.example.allinone.data.Transaction
import com.example.allinone.data.WTRegistration
import com.example.allinone.data.WTStudent
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.*

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
                repository.students,
                repository.registrations
            ) { transactions, investments, notes, students, registrations ->
                val historyItems = mutableListOf<HistoryItem>()
                
                // Add transactions
                historyItems.addAll(transactions.map { it.toHistoryItem() })
                
                // Add investments
                historyItems.addAll(investments.map { it.toHistoryItem() })
                
                // Add notes
                historyItems.addAll(notes.map { it.toHistoryItem() })
                
                // Add registrations
                // Create a map of student IDs to names for lookup
                val studentNameMap = students.associateBy({ it.id }, { it.name })
                
                // Add registrations with student names
                historyItems.addAll(registrations.mapNotNull { registration ->
                    // Get student name from the map
                    val studentName = studentNameMap[registration.studentId] ?: return@mapNotNull null
                    registration.toHistoryItem(studentName)
                })
                
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
                    
                    // If this is a registration transaction, check if we should also delete the registration
                    if (transaction.description.contains("Registration") && transaction.relatedRegistrationId != null) {
                        // Find the related registration
                        repository.registrations.value.find { it.id == transaction.relatedRegistrationId }?.let { registration ->
                            // Delete the registration too
                            repository.deleteRegistration(registration)
                        }
                    }
                    
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
                    
                    // Delete both investment and related transaction
                    repository.deleteInvestment(investment)
                    
                    // Find and delete related transaction
                    repository.transactions.value.find { 
                        it.description.contains(investment.name) && 
                        it.amount == investment.amount && 
                        it.type.contains("Investment")
                    }?.let { relatedTransaction ->
                        repository.deleteTransaction(relatedTransaction)
                    }
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
                HistoryItem.ItemType.REGISTRATION -> {
                    val registration = WTRegistration(
                        id = item.id,
                        studentId = 0, // We don't have this info in HistoryItem
                        amount = item.amount ?: 0.0,
                        startDate = item.date,
                        endDate = null,
                        attachmentUri = item.imageUri,
                        isPaid = true  // Assume paid since it's in history
                    )
                    
                    // Delete the registration (this will also delete related transactions because of our updated code)
                    repository.deleteRegistration(registration)
                }
            }
            
            // Explicitly refresh data to update UI
            refreshData()
        }
    }
    
    fun refreshData() {
        viewModelScope.launch {
            repository.refreshAllData()
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
    
    private fun WTRegistration.toHistoryItem(studentName: String): HistoryItem {
        return HistoryItem(
            id = id,
            title = "Registration: $studentName",
            description = "Course Registration",
            date = startDate ?: Date(),
            amount = amount,
            type = "Registration",
            imageUri = attachmentUri,
            itemType = HistoryItem.ItemType.REGISTRATION
        )
    }
} 