package com.example.allinone.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.*
import com.example.allinone.data.*
import com.example.allinone.firebase.FirebaseRepository
import com.example.allinone.firebase.FirebaseManager
import com.example.allinone.firebase.FirebaseIdManager
import com.example.allinone.firebase.DataChangeNotifier
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

class InvestmentsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application)
    private val firebaseManager = FirebaseManager()
    private val idManager = FirebaseIdManager()
    
    private val _allInvestments = MutableLiveData<List<Investment>>(emptyList())
    val allInvestments: LiveData<List<Investment>> = _allInvestments
    
    private val _totalInvestment = MutableLiveData<Double>(0.0)
    val totalInvestment: LiveData<Double> = _totalInvestment
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    private val _addStatus = MutableLiveData<AddStatus>()
    val addStatus: LiveData<AddStatus> = _addStatus
    
    private val _updateStatus = MutableLiveData<UpdateStatus>()
    val updateStatus: LiveData<UpdateStatus> = _updateStatus
    
    private val _deleteStatus = MutableLiveData<DeleteStatus>()
    val deleteStatus: LiveData<DeleteStatus> = _deleteStatus
    
    // Add enum classes for status
    sealed class AddStatus {
        object SUCCESS : AddStatus()
        object ERROR : AddStatus()
        object NONE : AddStatus()
    }
    
    sealed class UpdateStatus {
        object SUCCESS : UpdateStatus()
        object ERROR : UpdateStatus()
        object NONE : UpdateStatus()
    }
    
    sealed class DeleteStatus {
        object SUCCESS : DeleteStatus()
        object ERROR : DeleteStatus()
        object NONE : DeleteStatus()
    }
    
    init {
        // Set initial status values
        _addStatus.value = AddStatus.NONE
        _updateStatus.value = UpdateStatus.NONE
        _deleteStatus.value = DeleteStatus.NONE
        
        // Collect investments from the repository flow
        viewModelScope.launch {
            repository.investments.collect { investments ->
                _allInvestments.value = investments
                _totalInvestment.value = investments.sumOf { it.amount }
                Log.d("InvestmentsViewModel", "Collected ${investments.size} investments from repository")
            }
        }
    }

    fun addInvestment(name: String, amount: Double, type: String, 
                      description: String? = null, imageUri: String? = null, isPast: Boolean = false) {
        viewModelScope.launch {
            try {
                // Get next sequential ID
                val investmentId = idManager.getNextId("investments")
                
                val investment = Investment(
                    id = investmentId,
                    name = name,
                    amount = amount,
                    type = type,
                    description = description,
                    imageUri = imageUri,
                    date = Date(),
                    isPast = isPast
                )
                
                // Save to Firebase
                repository.insertInvestment(investment)
                
                // Also create a transaction for the investment (unless it's marked as past)
                if (!isPast) {
                    // Create a corresponding expense transaction
                    val transaction = Transaction(
                        id = 0, // Will be auto-generated
                        amount = amount,
                        type = "Investment",
                        description = "Investment in $name",
                        category = type,
                        date = Date(),
                        isIncome = false // Investments are expenses
                    )
                    
                    repository.insertTransaction(transaction)
                    Log.d("InvestmentsViewModel", "Created expense transaction for investment: $amount")
                } else {
                    Log.d("InvestmentsViewModel", "Past investment - no transaction created")
                }
                
                // Notify about data change
                DataChangeNotifier.notifyInvestmentsChanged()
                DataChangeNotifier.notifyTransactionsChanged()
                
                // Update the local cache
                _addStatus.value = AddStatus.SUCCESS
                refreshData()
            } catch (e: Exception) {
                Log.e("InvestmentsViewModel", "Error adding investment: ${e.message}", e)
                _addStatus.value = AddStatus.ERROR
                _errorMessage.value = "Error adding investment: ${e.message}"
            }
        }
    }
    
    fun updateInvestment(investment: Investment) {
        viewModelScope.launch {
            try {
                // First find the old investment to check if isPast status changed
                val oldInvestment = repository.getInvestmentById(investment.id)
                
                repository.updateInvestment(investment)
                
                // Handle transactions based on isPast status changes
                if (oldInvestment != null) {
                    updateInvestmentAndTransaction(oldInvestment, investment)
                }
                
                // Notify about data change
                DataChangeNotifier.notifyInvestmentsChanged()
                DataChangeNotifier.notifyTransactionsChanged()
                
                refreshData()
                _updateStatus.value = UpdateStatus.SUCCESS
            } catch (e: Exception) {
                Log.e("InvestmentsViewModel", "Error updating investment: ${e.message}", e)
                _updateStatus.value = UpdateStatus.ERROR
                _errorMessage.value = "Error updating investment: ${e.message}"
            }
        }
    }
    
    fun deleteInvestment(investment: Investment) {
        viewModelScope.launch {
            try {
                Log.d("InvestmentsViewModel", "Deleting investment: ${investment.name}, ID: ${investment.id}, Amount: ${investment.amount}")
                
                // First, find and delete related transactions
                val transactions = repository.transactions.value
                
                // Look for any transaction that might be related to this investment, regardless of isPast flag
                val relatedTransactions = transactions.filter { transaction ->
                    (transaction.type == "Investment" || transaction.type.contains("Investment")) &&
                    (
                        transaction.description.contains(investment.name) || 
                        transaction.description.contains("Investment in ${investment.name}") ||
                        transaction.description.contains("Investment: ${investment.name}") ||
                        transaction.description.contains("Additional investment in: ${investment.name}")
                    )
                }
                
                if (relatedTransactions.isNotEmpty()) {
                    Log.d("InvestmentsViewModel", "Found ${relatedTransactions.size} related transactions to delete")
                    
                    // Delete each related transaction
                    relatedTransactions.forEach { transaction ->
                        Log.d("InvestmentsViewModel", "Deleting related transaction: ${transaction.description}, Amount: ${transaction.amount}")
                        repository.deleteTransaction(transaction)
                    }
                } else {
                    Log.d("InvestmentsViewModel", "No related transactions found for investment: ${investment.name}")
                }
                
                // Then delete the investment
                repository.deleteInvestment(investment)
                
                // Notify about data change
                DataChangeNotifier.notifyInvestmentsChanged()
                DataChangeNotifier.notifyTransactionsChanged()
                
                refreshData()
                _deleteStatus.value = DeleteStatus.SUCCESS
            } catch (e: Exception) {
                Log.e("InvestmentsViewModel", "Error deleting investment: ${e.message}", e)
                _deleteStatus.value = DeleteStatus.ERROR
                _errorMessage.value = "Error deleting investment: ${e.message}"
            }
        }
    }

    fun updateInvestmentAndTransaction(oldInvestment: Investment, newInvestment: Investment) {
        viewModelScope.launch {
            try {
                // Handle transactions differently based on past investment status
                if (!oldInvestment.isPast && !newInvestment.isPast) {
                    // Case 1: Current investment remains current - update transaction
                    val transactions = repository.transactions.value
                    val matchingTransaction = transactions.find { 
                        it.description.contains(oldInvestment.name) && 
                        it.type == "Investment" &&
                        !it.isIncome // Only look for expense transactions (the original investment)
                    }
                    
                    if (matchingTransaction != null) {
                        // Update the existing transaction with the new amount
                        // This prevents double-counting
                        val updatedTransaction = matchingTransaction.copy(
                            amount = newInvestment.amount,
                            description = "Investment in ${newInvestment.name}",
                            category = newInvestment.type
                        )
                        repository.updateTransaction(updatedTransaction)
                        Log.d("InvestmentsViewModel", "Updated transaction: ${updatedTransaction.description} with ID: ${updatedTransaction.id}")
                    } else {
                        // Transaction not found - create a new one
                        val transaction = Transaction(
                            id = 0, // Will be auto-generated
                            amount = newInvestment.amount,
                            type = "Investment",
                            description = "Investment in ${newInvestment.name}",
                            category = newInvestment.type,
                            date = Date(),
                            isIncome = false // Investments are expenses
                        )
                        
                        repository.insertTransaction(transaction)
                        Log.d("InvestmentsViewModel", "Created new transaction for investment: ${newInvestment.name}")
                    }
                } else if (oldInvestment.isPast && !newInvestment.isPast) {
                    // Case 2: Past investment changed to current - we need to create a transaction
                    // Insert transaction using repository method
                    val transaction = Transaction(
                        id = 0, // Will be auto-generated
                        amount = newInvestment.amount,
                        type = "Investment",
                        description = "Investment in ${newInvestment.name}",
                        category = newInvestment.type,
                        date = Date(),
                        isIncome = false // Investments are expenses
                    )
                    
                    repository.insertTransaction(transaction)
                    Log.d("InvestmentsViewModel", "Created new transaction for converted past investment: Investment in ${newInvestment.name}")
                } else if (!oldInvestment.isPast && newInvestment.isPast) {
                    // Case 3: Current investment changed to past - find and delete any matching transaction
                    val transactions = repository.transactions.value
                    val matchingTransaction = transactions.find { transaction ->
                        transaction.type == "Investment" && 
                        !transaction.isIncome && // Only look for expense transactions
                        (transaction.description.contains(oldInvestment.name))
                    }
                    
                    if (matchingTransaction != null) {
                        repository.deleteTransaction(matchingTransaction)
                        Log.d("InvestmentsViewModel", "Deleted transaction for investment converted to past: ${matchingTransaction.description}")
                    }
                }
                // Case 4: Past investment remains past - nothing to do with transactions
                
                // Refresh data after all operations
                refreshData()
            } catch (e: Exception) {
                Log.e("InvestmentsViewModel", "Error updating investment transaction: ${e.message}", e)
                _errorMessage.value = "Error updating investment transaction: ${e.message}"
            }
        }
    }
    
    fun deleteInvestmentAndTransaction(investment: Investment) {
        viewModelScope.launch {
            try {
                // Find and delete the corresponding transaction with exact matching
                val transactions = repository.transactions.value
                
                // Find a transaction that matches this investment
                val matchingTransaction = transactions.find { transaction ->
                    transaction.type == "Investment" &&
                    (transaction.description.contains(investment.name) || 
                     transaction.description == "Investment in ${investment.name}" || 
                     transaction.description == "Investment: ${investment.name}")
                }
                
                // Only delete the transaction if we found a match
                if (matchingTransaction != null) {
                    repository.deleteTransaction(matchingTransaction)
                    
                    // Log the deletion to make troubleshooting easier
                    Log.d("InvestmentsViewModel", "Deleted matching transaction: ${matchingTransaction.description} with ID: ${matchingTransaction.id}")
                } else {
                    Log.d("InvestmentsViewModel", "No matching transaction found for investment: ${investment.name}")
                }
                
                // Explicitly trigger data refresh
                refreshData()
            } catch (e: Exception) {
                Log.e("InvestmentsViewModel", "Error deleting investment transaction: ${e.message}", e)
                _errorMessage.value = "Error deleting investment transaction: ${e.message}"
            }
        }
    }
    
    fun refreshData() {
        viewModelScope.launch {
            try {
                Log.d("InvestmentsViewModel", "Refreshing all data...")
                repository.refreshAllData()
                // Reset status values after refresh
                _addStatus.value = AddStatus.NONE
                _updateStatus.value = UpdateStatus.NONE
                _deleteStatus.value = DeleteStatus.NONE
            } catch (e: Exception) {
                Log.e("InvestmentsViewModel", "Error refreshing data: ${e.message}", e)
                _errorMessage.value = "Error refreshing data: ${e.message}"
            }
        }
    }

    /**
     * Add a new investment and return its ID
     * Used for two-step upload process with images
     */
    suspend fun addInvestmentAndGetId(investment: Investment): Long? {
        return try {
            val id = repository.insertInvestmentAndGetId(investment)
            
            // Also create a transaction for the investment (unless it's marked as past)
            if (!investment.isPast) {
                // Create a corresponding expense transaction
                val transaction = Transaction(
                    id = 0, // Will be auto-generated
                    amount = investment.amount,
                    type = "Investment",
                    description = "Investment in ${investment.name}",
                    category = investment.type,
                    date = Date(),
                    isIncome = false // Investments are expenses
                )
                
                repository.insertTransaction(transaction)
                Log.d("InvestmentsViewModel", "Created expense transaction for investment: ${investment.amount}")
                
                // Notify about transaction data change
                DataChangeNotifier.notifyTransactionsChanged()
            }
            
            id
        } catch (e: Exception) {
            Log.e("InvestmentsViewModel", "Error adding investment: ${e.message}", e)
            _errorMessage.value = "Error adding investment: ${e.message}"
            null
        }
    }
    
    /**
     * Upload an image for an investment directly (simplified version)
     */
    suspend fun uploadInvestmentImage(uri: Uri, investmentId: Long): String? {
        Log.d("InvestmentsViewModel", "Uploading image for investment $investmentId: $uri")
        
        try {
            // Direct upload to Firebase Storage with investment ID subfolder
            // Use exactly the same pattern as student profile picture upload
            Log.d("InvestmentsViewModel", "Sending image to FirebaseStorageUtil uploadFile: $investmentId: $uri")
            val result = repository.uploadFile(
                fileUri = uri,
                folderName = "investments",
                id = investmentId.toString()
            )
            
            if (result == null) {
                Log.e("InvestmentsViewModel", "Upload failed - no URL returned from repository")
            } else {
                Log.d("InvestmentsViewModel", "Upload successful: $result")
            }
            
            return result
        } catch (e: Exception) {
            Log.e("InvestmentsViewModel", "Error uploading investment image: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Update the image URIs for an investment
     * 
     * @deprecated Use updateInvestment instead
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun updateInvestmentImages(investmentId: Long, imageUrisString: String) {
        // This method is deprecated - use updateInvestment instead
        Log.d("InvestmentsViewModel", "This method is deprecated, use updateInvestment instead")
    }

    fun calculateTotalInvestments(): Double {
        return allInvestments.value?.sumOf { it.amount } ?: 0.0
    }
} 