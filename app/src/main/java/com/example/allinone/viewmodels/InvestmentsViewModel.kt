package com.example.allinone.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.*
import com.example.allinone.data.*
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

class InvestmentsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application)
    
    private val _allInvestments = MutableLiveData<List<Investment>>(emptyList())
    val allInvestments: LiveData<List<Investment>> = _allInvestments
    
    private val _totalInvestment = MutableLiveData<Double>(0.0)
    val totalInvestment: LiveData<Double> = _totalInvestment
    
    init {
        // Collect investments from the repository flow
        viewModelScope.launch {
            repository.investments.collect { investments ->
                _allInvestments.value = investments
                _totalInvestment.value = investments.sumOf { it.amount }
            }
        }
    }

    fun addInvestment(investment: Investment) {
        viewModelScope.launch {
            // Add the investment to the repository
            repository.insertInvestment(investment)
            
            // Only create a corresponding transaction if it's not a past investment
            if (!investment.isPast) {
                // Create an expense transaction for the investment amount
                val transaction = Transaction(
                    id = UUID.randomUUID().mostSignificantBits,
                    amount = investment.amount,
                    type = "Investment",
                    description = "Investment in ${investment.name}",
                    isIncome = false,
                    date = Date(),
                    category = investment.type
                )
                
                // Insert the transaction
                repository.updateTransaction(transaction)
            }
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
            // First update the investment including any image URIs
            Log.d("InvestmentsViewModel", "Updating investment with images: ${newInvestment.imageUri}")
            repository.updateInvestment(newInvestment)
            
            // Only handle the transaction if it's not a past investment
            if (!newInvestment.isPast) {
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
            } else {
                // For past investments, we need to find and delete any matching transaction
                // to avoid affecting the current balance
                val transactions = repository.transactions.value
                val matchingTransaction = transactions.find { transaction ->
                    transaction.type == "Investment" && 
                    (transaction.description == "Investment in ${oldInvestment.name}" || 
                     transaction.description == "Investment: ${oldInvestment.name}")
                }
                
                if (matchingTransaction != null) {
                    repository.deleteTransaction(matchingTransaction)
                    Log.d("InvestmentsViewModel", "Deleted transaction for past investment: ${matchingTransaction.description} with ID: ${matchingTransaction.id}")
                }
            }
        }
    }
    
    fun deleteInvestmentAndTransaction(investment: Investment) {
        viewModelScope.launch {
            // First step: Delete the investment
            repository.deleteInvestment(investment)
            
            // Only delete the transaction if it's not a past investment
            if (!investment.isPast) {
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

    /**
     * Add a new investment and return its ID
     * Used for two-step upload process with images
     */
    suspend fun addInvestmentAndGetId(investment: Investment): Long? {
        return try {
            repository.insertInvestmentAndGetId(investment)
        } catch (e: Exception) {
            Log.e("InvestmentsViewModel", "Error adding investment: ${e.message}", e)
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
} 