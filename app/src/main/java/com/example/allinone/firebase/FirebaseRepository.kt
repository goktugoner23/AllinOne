package com.example.allinone.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.allinone.AllinOneApplication
import com.example.allinone.data.Investment
import com.example.allinone.data.Note
import com.example.allinone.data.Transaction
import com.example.allinone.data.WTStudent
import com.example.allinone.data.WTEvent
import com.example.allinone.data.WTLesson
import com.example.allinone.utils.NetworkUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
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
    
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val appContext = context.applicationContext
    
    // Network status
    val isNetworkAvailable: LiveData<Boolean> = networkUtils.isNetworkAvailable
    
    // Cache for data
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    private val _investments = MutableStateFlow<List<Investment>>(emptyList())
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    private val _students = MutableStateFlow<List<WTStudent>>(emptyList())
    private val _wtEvents = MutableStateFlow<List<WTEvent>>(emptyList())
    private val _wtLessons = MutableStateFlow<List<WTLesson>>(emptyList())
    
    // Public flows
    val transactions: StateFlow<List<Transaction>> = _transactions
    val investments: StateFlow<List<Investment>> = _investments
    val notes: StateFlow<List<Note>> = _notes
    val students: StateFlow<List<WTStudent>> = _students
    val wtEvents: StateFlow<List<WTEvent>> = _wtEvents
    val wtLessons: StateFlow<List<WTLesson>> = _wtLessons
    
    // Error handling
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    // Add this field to track if Google Play Services is available
    private val _isGooglePlayServicesAvailable = MutableLiveData<Boolean>(true)
    val isGooglePlayServicesAvailable: LiveData<Boolean> = _isGooglePlayServicesAvailable
    
    // Queue status
    private val _pendingOperations = MutableLiveData<Int>(0)
    val pendingOperations: LiveData<Int> = _pendingOperations
    
    // Add this field to track if Firebase project is properly configured
    private val _isFirebaseProjectValid = MutableLiveData<Boolean>(true)
    val isFirebaseProjectValid: LiveData<Boolean> = _isFirebaseProjectValid
    
    // Add this field to track if Firestore security rules are properly configured
    private val _areFirestoreRulesValid = MutableLiveData<Boolean>(true)
    val areFirestoreRulesValid: LiveData<Boolean> = _areFirestoreRulesValid
    
    // Network connectivity status
    private val _isOnline = MutableLiveData<Boolean>()
    val isOnline: LiveData<Boolean> = _isOnline
    
    // Error handling
    private val _lastError = MutableLiveData<String>()
    val lastError: LiveData<String> = _lastError
    
    init {
        // Initialize by loading data from Firebase or local cache
        CoroutineScope(Dispatchers.IO).launch {
            try {
                checkGooglePlayServicesAvailability()
                refreshAllData()
            } catch (e: Exception) {
                _errorMessage.postValue("Error loading data: ${e.message}")
            }
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
        
        // Initialize network monitor
        monitorNetworkConnectivity()
        
        // Safely initialize Firestore settings for offline support
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            db.firestoreSettings = settings
            Log.d(TAG, "Firestore settings applied successfully")
        } catch (e: IllegalStateException) {
            // Firestore already initialized elsewhere, log this but don't crash
            Log.w(TAG, "Firestore already initialized, skipping settings: ${e.message}")
        }
    }
    
    /**
     * Monitors network connectivity and updates the isOnline LiveData
     */
    private fun monitorNetworkConnectivity() {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.postValue(true)
                Log.d(TAG, "Network connection available")
            }
            
            override fun onLost(network: Network) {
                _isOnline.postValue(false)
                Log.d(TAG, "Network connection lost")
            }
            
            override fun onUnavailable() {
                _isOnline.postValue(false)
                Log.d(TAG, "Network unavailable")
            }
        })
        
        // Initialize with current status using networkUtils
        _isOnline.postValue(networkUtils.isNetworkConnected())
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
                    OfflineQueue.DataType.WT_EVENT -> processWTEventQueueItem(queueItem)
                    OfflineQueue.DataType.WT_LESSON -> processWTLessonQueueItem(queueItem)
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
                    date = Date(),
                    description = "",
                    category = "",
                    type = "",
                    isIncome = false
                )
                firebaseManager.deleteTransaction(transaction)
                true
            }
            else -> false
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
                    description = "",
                    type = "",
                    imageUri = null,
                    profitLoss = 0.0
                )
                firebaseManager.deleteInvestment(investment)
                true
            }
            else -> false
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
            else -> false
        }
    }
    
    private suspend fun processStudentQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operationType) {
            OfflineQueue.OperationType.INSERT, OfflineQueue.OperationType.UPDATE -> {
                val student = gson.fromJson(queueItem.jsonData, WTStudent::class.java)
                firebaseManager.saveStudent(student)
                true
            }
            OfflineQueue.OperationType.UPDATE_WT_STUDENT -> {
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
            else -> false
        }
    }
    
    private suspend fun processWTEventQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operationType) {
            OfflineQueue.OperationType.INSERT_WT_EVENT -> {
                val event = gson.fromJson(queueItem.jsonData, WTEvent::class.java)
                insertWTEvent(event)
                true
            }
            OfflineQueue.OperationType.DELETE_WT_EVENT -> {
                val event = gson.fromJson(queueItem.jsonData, WTEvent::class.java)
                deleteWTEvent(event)
                true
            }
            else -> false
        }
    }
    
    private suspend fun processWTLessonQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operationType) {
            OfflineQueue.OperationType.INSERT_WT_LESSON -> {
                val lesson = gson.fromJson(queueItem.jsonData, WTLesson::class.java)
                insertWTLesson(lesson)
                true
            }
            OfflineQueue.OperationType.DELETE_WT_LESSON -> {
                val lesson = gson.fromJson(queueItem.jsonData, WTLesson::class.java)
                deleteWTLesson(lesson)
                true
            }
            else -> false
        }
    }
    
    /**
     * Update the count of pending operations
     */
    private fun updatePendingOperationsCount() {
        _pendingOperations.postValue(offlineQueue.getQueue().size)
    }
    
    /**
     * Refreshes all data from Firebase with robust error handling
     */
    fun refreshAllData() {
        Log.d(TAG, "Starting data refresh")
        
        // Use modern connectivity check instead of deprecated methods
        val isNetworkAvailable = networkUtils.isNetworkConnected()
        
        if (!isNetworkAvailable) {
            Log.w(TAG, "No network connection available, using cached data")
            _lastError.postValue("No network connection. Using cached data.")
            _isOnline.postValue(false)
            return
        }
        
        _isOnline.postValue(true)
        
        // Now refresh each data type with error handling
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refreshWTEvents()
                refreshWTLessons()
                refreshStudents()
                refreshTransactions()
                refreshInvestments()
                refreshNotes()
            } catch (e: Exception) {
                Log.e(TAG, "Error during data refresh: ${e.message}", e)
                _lastError.postValue("Error refreshing data: ${e.message}")
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
    
    /**
     * Update a transaction
     * @param transaction The transaction to update
     */
    fun updateTransaction(transaction: Transaction) {
        // Update local cache
        val currentList = _transactions.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == transaction.id }
        
        if (index != -1) {
            currentList[index] = transaction
        } else {
            currentList.add(transaction)
        }
        
        _transactions.value = currentList
        
        // Save to Firebase if network is available
        if (networkUtils.isNetworkConnected()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firebaseManager.saveTransaction(transaction)
                } catch (e: Exception) {
                    // Add to offline queue
                    offlineQueue.addOperation(
                        OfflineQueue.OperationType.UPDATE,
                        OfflineQueue.DataType.TRANSACTION,
                        transaction.id,
                        gson.toJson(transaction)
                    )
                    
                    // Update pending operations count
                    updatePendingOperationsCount()
                    
                    // Show error message
                    _errorMessage.postValue("Failed to save transaction: ${e.message}")
                }
            }
        } else {
            // Add to offline queue
            CoroutineScope(Dispatchers.IO).launch {
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.UPDATE,
                    OfflineQueue.DataType.TRANSACTION,
                    transaction.id,
                    gson.toJson(transaction)
                )
                
                // Update pending operations count
                updatePendingOperationsCount()
            }
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
    
    /**
     * Update an investment
     * @param investment The investment to update
     */
    fun updateInvestment(investment: Investment) {
        // Update local cache
        val currentList = _investments.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == investment.id }
        
        if (index != -1) {
            currentList[index] = investment
        } else {
            currentList.add(investment)
        }
        
        _investments.value = currentList
        
        // Save to Firebase if network is available
        if (networkUtils.isNetworkConnected()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firebaseManager.saveInvestment(investment)
                } catch (e: Exception) {
                    // Add to offline queue
                    offlineQueue.addOperation(
                        OfflineQueue.OperationType.UPDATE,
                        OfflineQueue.DataType.INVESTMENT,
                        investment.id,
                        gson.toJson(investment)
                    )
                    
                    // Update pending operations count
                    updatePendingOperationsCount()
                    
                    // Show error message
                    _errorMessage.postValue("Failed to save investment: ${e.message}")
                }
            }
        } else {
            // Add to offline queue
            CoroutineScope(Dispatchers.IO).launch {
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.UPDATE,
                    OfflineQueue.DataType.INVESTMENT,
                    investment.id,
                    gson.toJson(investment)
                )
                
                // Update pending operations count
                updatePendingOperationsCount()
            }
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
    
    /**
     * Update a note
     * @param note The note to update
     */
    fun updateNote(note: Note) {
        // Update local cache
        val currentList = _notes.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == note.id }
        
        if (index != -1) {
            currentList[index] = note
        } else {
            currentList.add(note)
        }
        
        _notes.value = currentList
        
        // Save to Firebase if network is available
        if (networkUtils.isNetworkConnected()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firebaseManager.saveNote(note)
                } catch (e: Exception) {
                    // Add to offline queue
                    offlineQueue.addOperation(
                        OfflineQueue.OperationType.UPDATE,
                        OfflineQueue.DataType.NOTE,
                        note.id,
                        gson.toJson(note)
                    )
                    
                    // Update pending operations count
                    updatePendingOperationsCount()
                    
                    // Show error message
                    _errorMessage.postValue("Failed to save note: ${e.message}")
                }
            }
        } else {
            // Add to offline queue
            CoroutineScope(Dispatchers.IO).launch {
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.UPDATE,
                    OfflineQueue.DataType.NOTE,
                    note.id,
                    gson.toJson(note)
                )
                
                // Update pending operations count
                updatePendingOperationsCount()
            }
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
    
    /**
     * Update a student
     * @param student The student to update
     */
    fun updateStudent(student: WTStudent) {
        // Update local cache
        val currentList = _students.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == student.id }
        
        if (index != -1) {
            currentList[index] = student
        } else {
            currentList.add(student)
        }
        
        _students.value = currentList
        
        // Save to Firebase if network is available
        if (networkUtils.isNetworkConnected()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firebaseManager.saveStudent(student)
                } catch (e: Exception) {
                    // Add to offline queue
                    offlineQueue.addOperation(
                        OfflineQueue.OperationType.UPDATE,
                        OfflineQueue.DataType.STUDENT,
                        student.id,
                        gson.toJson(student)
                    )
                    
                    // Update pending operations count
                    updatePendingOperationsCount()
                    
                    // Show error message
                    _errorMessage.postValue("Failed to save student: ${e.message}")
                }
            }
        } else {
            // Add to offline queue
            CoroutineScope(Dispatchers.IO).launch {
                offlineQueue.addOperation(
                    OfflineQueue.OperationType.UPDATE,
                    OfflineQueue.DataType.STUDENT,
                    student.id,
                    gson.toJson(student)
                )
                
                // Update pending operations count
                updatePendingOperationsCount()
            }
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
    
    /**
     * Clear the error message
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    // WT Events
    suspend fun insertWTEvent(title: String, description: String?, date: Date) {
        val event = WTEvent(
            id = 0, // Will be replaced by Firebase
            title = title,
            description = description,
            date = date,
            type = "Event"
        )
        insertWTEvent(event)
    }
    
    suspend fun insertWTEvent(event: WTEvent) {
        withContext(Dispatchers.IO) {
            try {
                if (networkUtils.isNetworkAvailable.value == true) {
                    // Generate ID if not present
                    val eventWithId = if (event.id == 0L) {
                        event.copy(id = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)
                    } else {
                        event
                    }
                    
                    // Save to Firebase
                    firebaseManager.saveWTEvent(eventWithId).await()
                    
                    // Update local cache - switch to Main thread for LiveData updates
                    withContext(Dispatchers.Main) {
                        val currentEvents = _wtEvents.value.toMutableList()
                        val index = currentEvents.indexOfFirst { it.id == eventWithId.id }
                        if (index >= 0) {
                            currentEvents[index] = eventWithId
                        } else {
                            currentEvents.add(eventWithId)
                        }
                        _wtEvents.value = currentEvents
                    }
                } else {
                    // Queue for later
                    offlineQueue.addOperation(
                        OfflineQueue.OperationType.INSERT_WT_EVENT,
                        OfflineQueue.DataType.WT_EVENT,
                        event.id,
                        gson.toJson(event)
                    )
                }
            } catch (e: Exception) {
                // Handle error
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error inserting WT event: ${e.message}"
                }
            }
        }
    }
    
    suspend fun deleteWTEvent(event: WTEvent) {
        withContext(Dispatchers.IO) {
            try {
                if (networkUtils.isNetworkAvailable.value == true) {
                    // Delete from Firebase
                    firebaseManager.deleteWTEvent(event.id).await()
                    
                    // Update local cache - switch to Main thread for LiveData updates
                    withContext(Dispatchers.Main) {
                        val currentEvents = _wtEvents.value.toMutableList()
                        currentEvents.removeAll { it.id == event.id }
                        _wtEvents.value = currentEvents
                    }
                } else {
                    // Queue for later
                    offlineQueue.addOperation(
                        OfflineQueue.OperationType.DELETE_WT_EVENT,
                        OfflineQueue.DataType.WT_EVENT,
                        event.id,
                        gson.toJson(event)
                    )
                }
            } catch (e: Exception) {
                // Handle error
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error deleting WT event: ${e.message}"
                }
            }
        }
    }
    
    // WT Lessons
    suspend fun insertWTLesson(lesson: WTLesson) {
        withContext(Dispatchers.IO) {
            try {
                if (networkUtils.isNetworkAvailable.value == true) {
                    // Generate ID if not present
                    val lessonWithId = if (lesson.id == 0L) {
                        lesson.copy(id = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)
                    } else {
                        lesson
                    }
                    
                    // Save to Firebase
                    firebaseManager.saveWTLesson(lessonWithId).await()
                    
                    // Update local cache - switch to Main thread for LiveData updates
                    withContext(Dispatchers.Main) {
                        val currentLessons = _wtLessons.value.toMutableList()
                        val index = currentLessons.indexOfFirst { it.id == lessonWithId.id }
                        if (index >= 0) {
                            currentLessons[index] = lessonWithId
                        } else {
                            currentLessons.add(lessonWithId)
                        }
                        _wtLessons.value = currentLessons
                    }
                } else {
                    // Queue for later
                    offlineQueue.addOperation(
                        OfflineQueue.OperationType.INSERT_WT_LESSON,
                        OfflineQueue.DataType.WT_LESSON,
                        lesson.id,
                        gson.toJson(lesson)
                    )
                }
            } catch (e: Exception) {
                // Handle error
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error inserting WT lesson: ${e.message}"
                }
            }
        }
    }
    
    suspend fun deleteWTLesson(lesson: WTLesson) {
        withContext(Dispatchers.IO) {
            try {
                if (networkUtils.isNetworkAvailable.value == true) {
                    // Delete from Firebase
                    firebaseManager.deleteWTLesson(lesson.id).await()
                    
                    // Update local cache - switch to Main thread for LiveData updates
                    withContext(Dispatchers.Main) {
                        val currentLessons = _wtLessons.value.toMutableList()
                        currentLessons.removeAll { it.id == lesson.id }
                        _wtLessons.value = currentLessons
                    }
                } else {
                    // Queue for later
                    offlineQueue.addOperation(
                        OfflineQueue.OperationType.DELETE_WT_LESSON,
                        OfflineQueue.DataType.WT_LESSON,
                        lesson.id,
                        gson.toJson(lesson)
                    )
                }
            } catch (e: Exception) {
                // Handle error
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error deleting WT lesson: ${e.message}"
                }
            }
        }
    }
    
    // WT Students
    suspend fun updateWTStudent(student: WTStudent) {
        withContext(Dispatchers.IO) {
            try {
                if (networkUtils.isNetworkAvailable.value == true) {
                    // Save to Firebase
                    firebaseManager.saveStudent(student)
                    
                    // Update local cache - switch to Main thread for LiveData updates
                    withContext(Dispatchers.Main) {
                        val currentStudents = _students.value.toMutableList()
                        val index = currentStudents.indexOfFirst { it.id == student.id }
                        if (index >= 0) {
                            currentStudents[index] = student
                        } else {
                            currentStudents.add(student)
                        }
                        _students.value = currentStudents
                    }
                } else {
                    // Queue for later
                    offlineQueue.addOperation(
                        OfflineQueue.OperationType.UPDATE_WT_STUDENT,
                        OfflineQueue.DataType.STUDENT,
                        student.id,
                        gson.toJson(student)
                    )
                }
            } catch (e: Exception) {
                // Handle error
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error updating WT student: ${e.message}"
                }
            }
        }
    }
    
    /**
     * Refreshes WT events from Firestore with robust error handling
     */
    fun refreshWTEvents() {
        Log.d(TAG, "Refreshing WT events")
        
        // Check if user is logged in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "Cannot refresh WT events: User not logged in")
            _lastError.postValue("User not logged in")
            return
        }
        
        val userId = currentUser.uid
        
        try {
            db.collection("users").document(userId).collection("wt_events")
                .get()
                .addOnSuccessListener { documents ->
                    Log.d(TAG, "WT events query successful, processing ${documents.size()} documents")
                    val events = mutableListOf<WTEvent>()
                    
                    for (document in documents) {
                        try {
                            val event = document.toObject(WTEvent::class.java)
                            events.add(event)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document to WTEvent: ${e.message}")
                        }
                    }
                    
                    Log.d(TAG, "Loaded ${events.size} WT events")
                    _wtEvents.value = events
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching WT events: ${e.message}")
                    _lastError.postValue("Error loading events: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during WT events refresh: ${e.message}")
            _lastError.postValue("Error refreshing events: ${e.message}")
        }
    }
    
    /**
     * Refreshes WT lessons from Firestore with robust error handling
     */
    fun refreshWTLessons() {
        Log.d(TAG, "Refreshing WT lessons")
        
        // Check if user is logged in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "Cannot refresh WT lessons: User not logged in")
            _lastError.postValue("User not logged in")
            return
        }
        
        val userId = currentUser.uid
        
        try {
            db.collection("users").document(userId).collection("wt_lessons")
                .get()
                .addOnSuccessListener { documents ->
                    Log.d(TAG, "WT lessons query successful, processing ${documents.size()} documents")
                    val lessons = mutableListOf<WTLesson>()
                    
                    for (document in documents) {
                        try {
                            val lesson = document.toObject(WTLesson::class.java)
                            lessons.add(lesson)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document to WTLesson: ${e.message}")
                        }
                    }
                    
                    Log.d(TAG, "Loaded ${lessons.size} WT lessons")
                    _wtLessons.value = lessons
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching WT lessons: ${e.message}")
                    _lastError.postValue("Error loading lessons: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during WT lessons refresh: ${e.message}")
            _lastError.postValue("Error refreshing lessons: ${e.message}")
        }
    }

    // Add this method to check Google Play Services availability
    fun checkGooglePlayServicesAvailability() {
        // Do a full Firebase configuration check
        checkFirebaseConfiguration()
    }

    // Add this method to check Firebase project configuration
    fun checkFirebaseConfiguration() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check Google Play Services availability first
                val isGpsAvailable = try {
                    firebaseManager.testConnection()
                    true
                } catch (e: Exception) {
                    _errorMessage.postValue("Google Play Services error: ${e.message}")
                    false
                }
                
                withContext(Dispatchers.Main) {
                    _isGooglePlayServicesAvailable.value = isGpsAvailable
                }
                
                // If GPS is not available, don't bother checking the rest
                if (!isGpsAvailable) return@launch
                
                // Check Firebase project validity
                val isProjectValid = try {
                    firebaseManager.validateFirebaseProject()
                } catch (e: Exception) {
                    _errorMessage.postValue("Firebase project validation error: ${e.message}")
                    false
                }
                
                withContext(Dispatchers.Main) {
                    _isFirebaseProjectValid.value = isProjectValid
                    
                    if (!isProjectValid) {
                        _errorMessage.value = "Firebase project configuration error. Please check google-services.json."
                    }
                }
                
                // If project is not valid, don't bother checking rules
                if (!isProjectValid) return@launch
                
                // Check security rules
                val areRulesValid = try {
                    firebaseManager.checkSecurityRules()
                } catch (e: Exception) {
                    _errorMessage.postValue("Firestore rules validation error: ${e.message}")
                    false
                }
                
                withContext(Dispatchers.Main) {
                    _areFirestoreRulesValid.value = areRulesValid
                    
                    if (!areRulesValid) {
                        _errorMessage.value = "Firestore security rules are not properly configured."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error checking Firebase configuration: ${e.message}"
                }
            }
        }
    }

    // Constants
    companion object {
        private const val TAG = "FirebaseRepository"
    }
} 