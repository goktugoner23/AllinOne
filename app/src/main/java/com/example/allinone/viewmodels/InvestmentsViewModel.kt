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
                    isPast = isPast,
                    profitLoss = 0.0,
                    currentValue = amount // Initially, current value equals invested amount
                )

                // Save to Firebase
                repository.insertInvestment(investment)

                // Also create a transaction for the investment (unless it's marked as past)
                if (!isPast) {
                    // Create a corresponding expense transaction - this DEDUCTS from balance
                    val transaction = Transaction(
                        id = 0, // Will be auto-generated
                        amount = amount,
                        type = "Investment",
                        description = "Investment in $name",
                        category = type,
                        date = Date(),
                        isIncome = false // This is EXPENSE - deducts from balance
                    )

                    repository.insertTransaction(transaction)
                    Log.d("InvestmentsViewModel", "Created EXPENSE transaction for investment: $amount (this DEDUCTS from balance)")
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

                if (oldInvestment != null) {
                    // Preserve profit/loss and current value if not explicitly updated
                    val updatedInvestment = investment.copy(
                        profitLoss = if (investment.profitLoss == 0.0) oldInvestment.profitLoss else investment.profitLoss,
                        currentValue = if (investment.currentValue == 0.0) oldInvestment.currentValue else investment.currentValue
                    )
                    
                    repository.updateInvestment(updatedInvestment)

                    // Handle transactions based on isPast status changes and amount differences
                    updateInvestmentAndTransaction(oldInvestment, updatedInvestment)
                } else {
                    // New investment - set default values
                    val updatedInvestment = investment.copy(
                        profitLoss = 0.0,
                        currentValue = investment.amount
                    )
                    repository.updateInvestment(updatedInvestment)
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
                // Log the investment update details for debugging
                Log.d("InvestmentsViewModel", "Updating investment: ${oldInvestment.name} -> ${newInvestment.name}")
                Log.d("InvestmentsViewModel", "Old amount: ${oldInvestment.amount}, New amount: ${newInvestment.amount}")
                Log.d("InvestmentsViewModel", "Old isPast: ${oldInvestment.isPast}, New isPast: ${newInvestment.isPast}")

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
                        Log.d("InvestmentsViewModel", "Found matching transaction: ${matchingTransaction.description} with amount: ${matchingTransaction.amount}")

                        // Calculate the difference between old and new investment amounts
                        val amountDifference = newInvestment.amount - oldInvestment.amount

                        // Always update the description and category to reflect any name/type changes
                        val updatedTransaction = if (amountDifference != 0.0) {
                            Log.d("InvestmentsViewModel", "Amount changed by $amountDifference")

                            // For amount increases, create an expense transaction (deducts from balance)
                            if (amountDifference > 0) {
                                val adjustmentTransaction = Transaction(
                                    id = 0, // Will be auto-generated
                                    amount = Math.abs(amountDifference),
                                    type = "Investment",
                                    description = "Additional investment in ${newInvestment.name}",
                                    category = newInvestment.type,
                                    date = Date(),
                                    isIncome = false // Additional investments are expenses - DEDUCTS from balance
                                )

                                repository.insertTransaction(adjustmentTransaction)
                                Log.d("InvestmentsViewModel", "Created expense transaction for additional investment: ${adjustmentTransaction.description} with amount: ${adjustmentTransaction.amount} (DEDUCTS from balance)")
                            } else {
                                // For amount decreases, create an income transaction (adds to balance)
                                val returnTransaction = Transaction(
                                    id = 0, // Will be auto-generated
                                    amount = Math.abs(amountDifference),
                                    type = "Investment",
                                    description = "Return from investment: ${newInvestment.name}",
                                    category = newInvestment.type,
                                    date = Date(),
                                    isIncome = true // Returns are income - ADDS to balance
                                )

                                repository.insertTransaction(returnTransaction)
                                Log.d("InvestmentsViewModel", "Created income transaction for investment reduction: ${returnTransaction.description} with amount: ${returnTransaction.amount} (ADDS to balance)")
                            }

                            // Keep the original transaction unchanged, just update description and category
                            matchingTransaction.copy(
                                description = "Investment in ${newInvestment.name}",
                                category = newInvestment.type
                            )
                        } else {
                            // Amount didn't change, only update description and category
                            Log.d("InvestmentsViewModel", "Amount unchanged, keeping transaction amount at ${matchingTransaction.amount}")
                            matchingTransaction.copy(
                                description = "Investment in ${newInvestment.name}",
                                category = newInvestment.type
                            )
                        }

                        repository.updateTransaction(updatedTransaction)
                        Log.d("InvestmentsViewModel", "Updated transaction: ${updatedTransaction.description} with ID: ${updatedTransaction.id}, amount: ${updatedTransaction.amount}")
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
                        Log.d("InvestmentsViewModel", "Created new transaction for investment: ${newInvestment.name} with amount: ${newInvestment.amount}")
                    }
                } else if (oldInvestment.isPast && !newInvestment.isPast) {
                    // Case 2: Past investment changed to current - we need to create a transaction
                    Log.d("InvestmentsViewModel", "Converting past investment to current, creating new transaction")

                    // Insert transaction using repository method
                    // When converting from past to current, we create a new transaction for the full amount
                    // since this investment wasn't previously counted in transactions
                    val transaction = Transaction(
                        id = 0, // Will be auto-generated
                        amount = newInvestment.amount,
                        type = "Investment",
                        description = "Investment in ${newInvestment.name} (converted from past)",
                        category = newInvestment.type,
                        date = Date(),
                        isIncome = false // Investments are expenses
                    )

                    repository.insertTransaction(transaction)
                    Log.d("InvestmentsViewModel", "Created new transaction for converted past investment: Investment in ${newInvestment.name} with amount: ${newInvestment.amount}")
                } else if (!oldInvestment.isPast && newInvestment.isPast) {
                    // Case 3: Current investment changed to past - find and delete any matching transaction
                    Log.d("InvestmentsViewModel", "Converting current investment to past, removing associated transaction")

                    val transactions = repository.transactions.value
                    val matchingTransaction = transactions.find { transaction ->
                        transaction.type == "Investment" &&
                        !transaction.isIncome && // Only look for expense transactions
                        (transaction.description.contains(oldInvestment.name))
                    }

                    if (matchingTransaction != null) {
                        Log.d("InvestmentsViewModel", "Found matching transaction to delete: ${matchingTransaction.description} with amount: ${matchingTransaction.amount}")
                        repository.deleteTransaction(matchingTransaction)
                        Log.d("InvestmentsViewModel", "Deleted transaction for investment converted to past: ${matchingTransaction.description}")
                    } else {
                        Log.d("InvestmentsViewModel", "No matching transaction found to delete for investment: ${oldInvestment.name}")
                    }
                } else {
                    // Case 4: Past investment remains past - nothing to do with transactions
                    Log.d("InvestmentsViewModel", "Past investment remains past, no transaction changes needed")
                }

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
            // Ensure proper default values
            val investmentWithDefaults = investment.copy(
                profitLoss = 0.0,
                currentValue = investment.amount
            )
            
            val id = repository.insertInvestmentAndGetId(investmentWithDefaults)

            // Also create a transaction for the investment (unless it's marked as past)
            if (!investment.isPast) {
                // Create a corresponding expense transaction - this DEDUCTS from balance
                val transaction = Transaction(
                    id = 0, // Will be auto-generated
                    amount = investment.amount,
                    type = "Investment",
                    description = "Investment in ${investment.name}",
                    category = investment.type,
                    date = Date(),
                    isIncome = false // Investments are expenses - DEDUCTS from balance
                )

                repository.insertTransaction(transaction)
                Log.d("InvestmentsViewModel", "Created expense transaction for investment: ${investment.amount} (DEDUCTS from balance)")

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

    /**
     * Liquidates an investment by deleting it without affecting related transactions
     * @param investment The investment to liquidate
     */
    fun liquidateInvestment(investment: Investment) {
        viewModelScope.launch {
            try {
                Log.d("InvestmentsViewModel", "Liquidating investment: ${investment.name}, ID: ${investment.id}, Amount: ${investment.amount}")
                
                // Just delete the investment without looking for or modifying transactions
                repository.deleteInvestment(investment)

                // Notify about data change (only for investments)
                DataChangeNotifier.notifyInvestmentsChanged()

                refreshData()
                _deleteStatus.value = DeleteStatus.SUCCESS
            } catch (e: Exception) {
                Log.e("InvestmentsViewModel", "Error liquidating investment: ${e.message}", e)
                _deleteStatus.value = DeleteStatus.ERROR
                _errorMessage.value = "Error liquidating investment: ${e.message}"
            }
        }
    }

    /**
     * Update an investment with a reduced amount, with option to add the difference as income
     * @param newInvestment The updated investment with reduced amount
     * @param addDifferenceAsIncome Whether to add the difference as income
     */
    fun updateInvestmentWithAmountReduction(newInvestment: Investment, addDifferenceAsIncome: Boolean) {
        viewModelScope.launch {
            try {
                // First find the old investment to get the original amount
                val oldInvestment = repository.getInvestmentById(newInvestment.id)

                if (oldInvestment == null) {
                    Log.e("InvestmentsViewModel", "Error: Could not find original investment with ID: ${newInvestment.id}")
                    _errorMessage.value = "Error: Could not find original investment"
                    return@launch
                }

                // Calculate the difference (should be negative if amount was reduced)
                val amountDifference = newInvestment.amount - oldInvestment.amount

                // Verify that this is indeed a reduction
                if (amountDifference >= 0) {
                    Log.e("InvestmentsViewModel", "Error: Expected amount reduction but got difference: $amountDifference")
                    _errorMessage.value = "Error: Expected amount reduction"
                    return@launch
                }

                // Update the investment in the repository
                repository.updateInvestment(newInvestment)

                // Find the original transaction to update its description and category
                val transactions = repository.transactions.value
                val matchingTransaction = transactions.find {
                    it.description.contains(oldInvestment.name) &&
                    it.type == "Investment" &&
                    !it.isIncome // Only look for expense transactions (the original investment)
                }

                if (matchingTransaction != null) {
                    // Update the original transaction's description and category, but not the amount
                    val updatedTransaction = matchingTransaction.copy(
                        description = "Investment in ${newInvestment.name}",
                        category = newInvestment.type
                    )
                    repository.updateTransaction(updatedTransaction)
                    Log.d("InvestmentsViewModel", "Updated original transaction description/category: ${updatedTransaction.description}")
                }

                // If user chose to add the difference as income, create an income transaction
                if (addDifferenceAsIncome) {
                    val incomeTransaction = Transaction(
                        id = 0, // Will be auto-generated
                        amount = Math.abs(amountDifference), // Make it positive
                        type = "Investment",
                        description = "Return from investment: ${newInvestment.name}",
                        category = newInvestment.type,
                        date = Date(),
                        isIncome = true // This is income
                    )

                    repository.insertTransaction(incomeTransaction)
                    Log.d("InvestmentsViewModel", "Created income transaction for investment reduction: ${incomeTransaction.description} with amount: ${incomeTransaction.amount}")
                } else {
                    Log.d("InvestmentsViewModel", "Investment amount reduced without creating income transaction")
                }

                // Notify about data change
                DataChangeNotifier.notifyInvestmentsChanged()
                DataChangeNotifier.notifyTransactionsChanged()

                refreshData()
                _updateStatus.value = UpdateStatus.SUCCESS
            } catch (e: Exception) {
                Log.e("InvestmentsViewModel", "Error updating investment with amount reduction: ${e.message}", e)
                _updateStatus.value = UpdateStatus.ERROR
                _errorMessage.value = "Error updating investment: ${e.message}"
            }
        }
    }

    /**
     * Add profit/loss to an investment without affecting the transaction balance
     * This is purely for tracking investment performance
     */
    fun addProfitLossToInvestment(investment: Investment, profitLossAmount: Double, isProfit: Boolean) {
        viewModelScope.launch {
            try {
                val updatedProfitLoss = if (isProfit) {
                    investment.profitLoss + profitLossAmount
                } else {
                    investment.profitLoss - profitLossAmount
                }

                val updatedInvestment = investment.copy(
                    profitLoss = updatedProfitLoss,
                    currentValue = investment.amount + updatedProfitLoss // Update current value
                )

                repository.updateInvestment(updatedInvestment)

                // Notify about data change
                DataChangeNotifier.notifyInvestmentsChanged()

                refreshData()
                _updateStatus.value = UpdateStatus.SUCCESS
                
                Log.d("InvestmentsViewModel", "Added ${if (isProfit) "profit" else "loss"} of $profitLossAmount to ${investment.name}")
            } catch (e: Exception) {
                Log.e("InvestmentsViewModel", "Error adding profit/loss to investment: ${e.message}", e)
                _updateStatus.value = UpdateStatus.ERROR
                _errorMessage.value = "Error updating investment: ${e.message}"
            }
        }
    }
}