package com.example.allinone.firebase

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.allinone.AllinOneApplication
import com.example.allinone.data.Investment
import com.example.allinone.data.Note
import com.example.allinone.data.Transaction
import com.example.allinone.data.WTStudent
import com.example.allinone.utils.NetworkUtils
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

/**
 * A repository that uses Firebase for all data operations.
 * This replaces all Room-based repositories.
 */
class FirebaseRepository(private val context: Context) {
    private val firebaseManager = FirebaseManager(context)
    private val networkUtils = (context.applicationContext as AllinOneApplication).networkUtils
    private val offlineQueue = OfflineQueue(context)
    private val gson = Gson()
    
    // Network status
    val isNetworkAvailable: LiveData<Boolean> = networkUtils.isNetworkAvailable
    
    // Cache for data
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    private val _investments = MutableStateFlow<List<Investment>>(emptyList())
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    private val _students = MutableStateFlow<List<WTStudent>>(emptyList())
    
    // Public flows
    val transactions: StateFlow<List<Transaction>> = _transactions
    val investments: StateFlow<List<Investment>> = _investments
    val notes: StateFlow<List<Note>> = _notes
    val students: StateFlow<List<WTStudent>> = _students
    
    // Error handling
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage
    
    // Queue status
    private val _pendingOperations = MutableLiveData<Int>(0)
    val pendingOperations: LiveData<Int> = _pendingOperations
    
    init {
        // Initialize by loading data from Firebase
        CoroutineScope(Dispatchers.IO).launch {
            refreshAllData()
        }
        
        // Listen for network changes
        networkUtils.isNetworkAvailable.observeForever { isAvailable ->
            if (isAvailable) {
                // Network is back, sync data
                CoroutineScope(Dispatchers.IO).launch {
                    processOfflineQueue()
                    refreshAllData()
                }
            }
            
            // Update pending operations count
            updatePendingOperationsCount()
        }
    }
    
    /**
     * Process the offline queue when network is available
     */
    private suspend fun processOfflineQueue() {
        if (!networkUtils.isNetworkConnected()) {
            return
        }
        
        offlineQueue.processQueue { queueItem ->
            try {
                when (queueItem.dataType) {
                    OfflineQueue.DataType.TRANSACTION -> processTransactionQueueItem(queueItem)
                    OfflineQueue.DataType.INVESTMENT -> processInvestmentQueueItem(queueItem)
                    OfflineQueue.DataType.NOTE -> processNoteQueueItem(queueItem)
                    OfflineQueue.DataType.STUDENT -> processStudentQueueItem(queueItem)
                }
                true // Operation succeeded
            } catch (e: Exception) {
                _errorMessage.postValue("Error processing offline operation: ${e.message}")
                false // Operation failed, keep in queue
            }
        }
        
        // Update pending operations count
        updatePendingOperationsCount()
    }
    
    private suspend fun processTransactionQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operationType) {
            OfflineQueue.OperationType.INSERT, OfflineQueue.OperationType.UPDATE -> {
                val transaction = gson.fromJson(queueItem.jsonData, Transaction::class.java)
                firebaseManager.saveTransaction(transaction)
                true
            }
            OfflineQueue.OperationType.DELETE -> {
                val transaction = Transaction(
                    id = queueItem.dataId,
                    amount = 0.0,
                    type = "",
                    description = "",
                    isIncome = false,
                    date = Date(),
                    category = ""
                )
                firebaseManager.deleteTransaction(transaction)
                true
            }
        }
    }
    
    private suspend fun processInvestmentQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operationType) {
            OfflineQueue.OperationType.INSERT, OfflineQueue.OperationType.UPDATE -> {
                val investment = gson.fromJson(queueItem.jsonData, Investment::class.java)
                firebaseManager.saveInvestment(investment)
                true
            }
            OfflineQueue.OperationType.DELETE -> {
                val investment = Investment(
                    id = queueItem.dataId,
                    name = "",
                    amount = 0.0,
                    date = Date(),
                    type = "",
                    description = null,
                    imageUri = null,
                    profitLoss = 0.0
                )
                firebaseManager.deleteInvestment(investment)
                true
            }
        }
    }
    
    private suspend fun processNoteQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operationType) {
            OfflineQueue.OperationType.INSERT, OfflineQueue.OperationType.UPDATE -> {
                val note = gson.fromJson(queueItem.jsonData, Note::class.java)
                firebaseManager.saveNote(note)
                true
            }
            OfflineQueue.OperationType.DELETE -> {
                val note = Note(
                    id = queueItem.dataId,
                    title = "",
                    content = "",
                    date = Date(),
                    imageUri = null
                )
                firebaseManager.deleteNote(note)
                true
            }
        }
    }
    
    private suspend fun processStudentQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operationType) {
            OfflineQueue.OperationType.INSERT, OfflineQueue.OperationType.UPDATE -> {
                val student = gson.fromJson(queueItem.jsonData, WTStudent::class.java)
                firebaseManager.saveStudent(student)
                true
            }
            OfflineQueue.OperationType.DELETE -> {
                val student = WTStudent(
                    id = queueItem.dataId,
                    name = "",
                    startDate = Date(),
                    endDate = Date(),
                    amount = 0.0,
                    isPaid = false,
                    paymentDate = null,
                    attachmentUri = null
                )
                firebaseManager.deleteStudent(student)
                true
            }
        }
    }
    
    /**
     * Update the count of pending operations
     */
    private fun updatePendingOperationsCount() {
        _pendingOperations.postValue(offlineQueue.getQueue().size)
    }
    
    /**
     * Refreshes all data from Firebase
     */
    suspend fun refreshAllData() {
        if (!networkUtils.isNetworkConnected()) {
            _errorMessage.postValue("No network connection. Using cached data.")
            return
        }
        
        withContext(Dispatchers.IO) {
            try {
                launch { refreshTransactions() }
                launch { refreshInvestments() }
                launch { refreshNotes() }
                launch { refreshStudents() }
                _errorMessage.postValue(null) // Clear any previous errors
            } catch (e: Exception) {
                _errorMessage.postValue("Error refreshing data: ${e.message}")
            }
        }
    }
    
    // Transaction methods
    suspend fun refreshTransactions() {
        if (!networkUtils.isNetworkConnected()) {
            return // Use cached data
        }
        
        try {
            val transactionList = firebaseManager.getTransactions()
            _transactions.value = transactionList
        } catch (e: Exception) {
            _errorMessage.postValue("Error loading transactions: ${e.message}")
        }
    }
    
    suspend fun insertTransaction(
        amount: Double,
        type: String,
        description: String?,
        isIncome: Boolean,
        category: String
    ) {
        val transaction = Transaction(
            id = UUID.randomUUID().mostSignificantBits,
            amount = amount,
            type = type,
            description = description ?: "",
            isIncome = isIncome,
            date = Date(),
            category = category
        )
        
        try {
            // Update local cache immediately for responsiveness
            val currentTransactions = _transactions.value.toMutableList()
            currentTransactions.add(transaction)
            _transactions.value = currentTransactions
            
            // Then update Firebase if network is available
            if (networkUtils.isNetworkConnected()) {
                firebaseManager.saveTransaction(transaction)
            } else {
                // Add to offline queue
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.INSERT,
                    OfflineQueue.DataType.TRANSACTION,
                    transaction.id,
                    gson.toJson(transaction)
                )
                _errorMessage.postValue("Transaction saved locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error saving transaction: ${e.message}")
        }
    }
    
    suspend fun updateTransaction(transaction: Transaction) {
        try {
            // Update local cache immediately
            val currentTransactions = _transactions.value.toMutableList()
            val index = currentTransactions.indexOfFirst { it.id == transaction.id }
            if (index != -1) {
                currentTransactions[index] = transaction
                _transactions.value = currentTransactions
            }
            
            // Then update Firebase if network is available
            if (networkUtils.isNetworkConnected()) {
                firebaseManager.saveTransaction(transaction)
            } else {
                // Add to offline queue
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.UPDATE,
                    OfflineQueue.DataType.TRANSACTION,
                    transaction.id,
                    gson.toJson(transaction)
                )
                _errorMessage.postValue("Transaction updated locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error updating transaction: ${e.message}")
        }
    }
    
    suspend fun deleteTransaction(transaction: Transaction) {
        try {
            // Update local cache immediately
            val currentTransactions = _transactions.value.toMutableList()
            currentTransactions.removeIf { it.id == transaction.id }
            _transactions.value = currentTransactions
            
            // Then delete from Firebase if network is available
            if (networkUtils.isNetworkConnected()) {
                firebaseManager.deleteTransaction(transaction)
            } else {
                // Add to offline queue
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.DELETE,
                    OfflineQueue.DataType.TRANSACTION,
                    transaction.id
                )
                _errorMessage.postValue("Transaction deleted locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error deleting transaction: ${e.message}")
        }
    }
    
    // Investment methods
    suspend fun refreshInvestments() {
        if (!networkUtils.isNetworkConnected()) {
            return // Use cached data
        }
        
        try {
            val investmentList = firebaseManager.getInvestments()
            _investments.value = investmentList
        } catch (e: Exception) {
            _errorMessage.postValue("Error loading investments: ${e.message}")
        }
    }
    
    suspend fun insertInvestment(investment: Investment) {
        try {
            // Update local cache immediately
            val currentInvestments = _investments.value.toMutableList()
            currentInvestments.add(investment)
            _investments.value = currentInvestments
            
            // Then update Firebase if network is available
            if (networkUtils.isNetworkConnected()) {
                firebaseManager.saveInvestment(investment)
            } else {
                // Add to offline queue
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.INSERT,
                    OfflineQueue.DataType.INVESTMENT,
                    investment.id,
                    gson.toJson(investment)
                )
                _errorMessage.postValue("Investment saved locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error saving investment: ${e.message}")
        }
    }
    
    suspend fun updateInvestment(investment: Investment) {
        try {
            // Update local cache immediately
            val currentInvestments = _investments.value.toMutableList()
            val index = currentInvestments.indexOfFirst { it.id == investment.id }
            if (index != -1) {
                currentInvestments[index] = investment
                _investments.value = currentInvestments
            }
            
            // Then update Firebase if network is available
            if (networkUtils.isNetworkConnected()) {
                firebaseManager.saveInvestment(investment)
            } else {
                // Add to offline queue
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.UPDATE,
                    OfflineQueue.DataType.INVESTMENT,
                    investment.id,
                    gson.toJson(investment)
                )
                _errorMessage.postValue("Investment updated locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error updating investment: ${e.message}")
        }
    }
    
    suspend fun deleteInvestment(investment: Investment) {
        try {
            // Update local cache immediately
            val currentInvestments = _investments.value.toMutableList()
            currentInvestments.removeIf { it.id == investment.id }
            _investments.value = currentInvestments
            
            // Then delete from Firebase if network is available
            if (networkUtils.isNetworkConnected()) {
                firebaseManager.deleteInvestment(investment)
            } else {
                // Add to offline queue
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.DELETE,
                    OfflineQueue.DataType.INVESTMENT,
                    investment.id
                )
                _errorMessage.postValue("Investment deleted locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error deleting investment: ${e.message}")
        }
    }
    
    // Note methods
    suspend fun refreshNotes() {
        if (!networkUtils.isNetworkConnected()) {
            return // Use cached data
        }
        
        try {
            val noteList = firebaseManager.getNotes()
            _notes.value = noteList
        } catch (e: Exception) {
            _errorMessage.postValue("Error loading notes: ${e.message}")
        }
    }
    
    suspend fun insertNote(note: Note) {
        try {
            // Update local cache immediately
            val currentNotes = _notes.value.toMutableList()
            currentNotes.add(note)
            _notes.value = currentNotes
            
            // Then update Firebase if network is available
            if (networkUtils.isNetworkConnected()) {
                firebaseManager.saveNote(note)
            } else {
                // Add to offline queue
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.INSERT,
                    OfflineQueue.DataType.NOTE,
                    note.id,
                    gson.toJson(note)
                )
                _errorMessage.postValue("Note saved locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error saving note: ${e.message}")
        }
    }
    
    suspend fun updateNote(note: Note) {
        try {
            // Update local cache immediately
            val currentNotes = _notes.value.toMutableList()
            val index = currentNotes.indexOfFirst { it.id == note.id }
            if (index != -1) {
                currentNotes[index] = note
                _notes.value = currentNotes
            }
            
            // Then update Firebase if network is available
            if (networkUtils.isNetworkConnected()) {
                firebaseManager.saveNote(note)
            } else {
                // Add to offline queue
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.UPDATE,
                    OfflineQueue.DataType.NOTE,
                    note.id,
                    gson.toJson(note)
                )
                _errorMessage.postValue("Note updated locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error updating note: ${e.message}")
        }
    }
    
    suspend fun deleteNote(note: Note) {
        try {
            // Update local cache immediately
            val currentNotes = _notes.value.toMutableList()
            currentNotes.removeIf { it.id == note.id }
            _notes.value = currentNotes
            
            // Then delete from Firebase if network is available
            if (networkUtils.isNetworkConnected()) {
                firebaseManager.deleteNote(note)
            } else {
                // Add to offline queue
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.DELETE,
                    OfflineQueue.DataType.NOTE,
                    note.id
                )
                _errorMessage.postValue("Note deleted locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error deleting note: ${e.message}")
        }
    }
    
    // WTStudent methods
    suspend fun refreshStudents() {
        if (!networkUtils.isNetworkConnected()) {
            return // Use cached data
        }
        
        try {
            val studentList = firebaseManager.getStudents()
            _students.value = studentList
        } catch (e: Exception) {
            _errorMessage.postValue("Error loading students: ${e.message}")
        }
    }
    
    suspend fun insertStudent(student: WTStudent) {
        try {
            // Update local cache immediately
            val currentStudents = _students.value.toMutableList()
            currentStudents.add(student)
            _students.value = currentStudents
            
            // Then update Firebase if network is available
            if (networkUtils.isNetworkConnected()) {
                firebaseManager.saveStudent(student)
            } else {
                // Add to offline queue
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.INSERT,
                    OfflineQueue.DataType.STUDENT,
                    student.id,
                    gson.toJson(student)
                )
                _errorMessage.postValue("Student saved locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error saving student: ${e.message}")
        }
    }
    
    suspend fun updateStudent(student: WTStudent) {
        try {
            // Update local cache immediately
            val currentStudents = _students.value.toMutableList()
            val index = currentStudents.indexOfFirst { it.id == student.id }
            if (index != -1) {
                currentStudents[index] = student
                _students.value = currentStudents
            }
            
            // Then update Firebase if network is available
            if (networkUtils.isNetworkConnected()) {
                firebaseManager.saveStudent(student)
            } else {
                // Add to offline queue
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.UPDATE,
                    OfflineQueue.DataType.STUDENT,
                    student.id,
                    gson.toJson(student)
                )
                _errorMessage.postValue("Student updated locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error updating student: ${e.message}")
        }
    }
    
    suspend fun deleteStudent(student: WTStudent) {
        try {
            // Update local cache immediately
            val currentStudents = _students.value.toMutableList()
            currentStudents.removeIf { it.id == student.id }
            _students.value = currentStudents
            
            // Then delete from Firebase if network is available
            if (networkUtils.isNetworkConnected()) {
                firebaseManager.deleteStudent(student)
            } else {
                // Add to offline queue
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.DELETE,
                    OfflineQueue.DataType.STUDENT,
                    student.id
                )
                _errorMessage.postValue("Student deleted locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error deleting student: ${e.message}")
        }
    }
    
    // Helper methods for filtering data
    fun getTransactionsByType(isIncome: Boolean): List<Transaction> {
        return _transactions.value.filter { it.isIncome == isIncome }
    }
    
    fun getTotalByType(isIncome: Boolean): Double {
        return _transactions.value
            .filter { it.isIncome == isIncome }
            .sumOf { it.amount }
    }
    
    // Image and attachment handling
    suspend fun uploadImage(uri: Uri): String? {
        if (!networkUtils.isNetworkConnected()) {
            _errorMessage.postValue("Cannot upload image without network connection.")
            return null
        }
        
        return try {
            firebaseManager.uploadImage(uri)
        } catch (e: Exception) {
            _errorMessage.postValue("Error uploading image: ${e.message}")
            null
        }
    }
    
    suspend fun uploadAttachment(uri: Uri): String? {
        if (!networkUtils.isNetworkConnected()) {
            _errorMessage.postValue("Cannot upload attachment without network connection.")
            return null
        }
        
        return try {
            firebaseManager.uploadAttachment(uri)
        } catch (e: Exception) {
            _errorMessage.postValue("Error uploading attachment: ${e.message}")
            null
        }
    }
    
    // Clear error message
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
} 