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
import com.example.allinone.data.Event
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID
import com.google.firebase.firestore.PersistentCacheSettings
import com.example.allinone.cache.CacheManager

/**
 * A repository that uses Firebase for all data operations.
 * This replaces all Room-based repositories.
 */
class FirebaseRepository(private val context: Context) {
    private val firebaseManager = FirebaseManager(context)
    private val networkUtils = (context.applicationContext as AllinOneApplication).networkUtils
    private val offlineQueue = OfflineQueue(context)
    private val cacheManager = (context.applicationContext as AllinOneApplication).cacheManager
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
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    private val _wtLessons = MutableStateFlow<List<WTLesson>>(emptyList())
    
    // Public flows
    val transactions: StateFlow<List<Transaction>> = _transactions
    val investments: StateFlow<List<Investment>> = _investments
    val notes: StateFlow<List<Note>> = _notes
    val students: StateFlow<List<WTStudent>> = _students
    val events: StateFlow<List<Event>> = _events
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
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    init {
        // Initialize by loading data from Firebase or local cache
        CoroutineScope(Dispatchers.IO).launch {
            try {
                checkGooglePlayServicesAvailability()
                
                // Load initial data from local cache first for immediate display
                loadFromLocalCache()
                
                // Then refresh from network if available
                if (networkUtils.isActiveNetworkConnected()) {
                    refreshAllData()
                }
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
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build()
                )
                .build()
            db.firestoreSettings = settings
            Log.d(TAG, "Firestore settings applied successfully")
        } catch (e: IllegalStateException) {
            // Firestore already initialized elsewhere, log this but don't crash
            Log.w(TAG, "Firestore already initialized, skipping settings: ${e.message}")
        }
    }
    
    /**
     * Load all data from local cache to provide immediate response
     */
    private suspend fun loadFromLocalCache() {
        withContext(Dispatchers.IO) {
            // Load transactions
            val cachedTransactions = cacheManager.getCachedTransactions()
            if (cachedTransactions.isNotEmpty()) {
                _transactions.value = cachedTransactions
                Log.d(TAG, "Loaded ${cachedTransactions.size} transactions from cache")
            }
            
            // Load investments
            val cachedInvestments = cacheManager.getCachedInvestments()
            if (cachedInvestments.isNotEmpty()) {
                _investments.value = cachedInvestments
                Log.d(TAG, "Loaded ${cachedInvestments.size} investments from cache")
            }
            
            // Load notes
            val cachedNotes = cacheManager.getCachedNotes()
            if (cachedNotes.isNotEmpty()) {
                _notes.value = cachedNotes
                Log.d(TAG, "Loaded ${cachedNotes.size} notes from cache")
            }
            
            // Load students
            val cachedStudents = cacheManager.getCachedStudents()
            if (cachedStudents.isNotEmpty()) {
                _students.value = cachedStudents
                Log.d(TAG, "Loaded ${cachedStudents.size} students from cache")
            }
            
            // Load events
            val cachedEvents = cacheManager.getCachedEvents()
            if (cachedEvents.isNotEmpty()) {
                _events.value = cachedEvents
                Log.d(TAG, "Loaded ${cachedEvents.size} events from cache")
            }
            
            // Load lessons
            val cachedLessons = cacheManager.getCachedLessons()
            if (cachedLessons.isNotEmpty()) {
                _wtLessons.value = cachedLessons
                Log.d(TAG, "Loaded ${cachedLessons.size} lessons from cache")
            }
        }
    }
    
    /**
     * Monitors network connectivity and updates the isOnline LiveData
     */
    private fun monitorNetworkConnectivity() {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Keep track of last network status to prevent flapping
        var lastNetworkStatus = true // Start assuming we have network
        var networkStatusChangeTime = System.currentTimeMillis()
        val debounceTimeMs = 3000L // 3 seconds debounce (was 2 seconds)
        
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val currentTime = System.currentTimeMillis()
                
                // Add a short delay before confirming network is available
                CoroutineScope(Dispatchers.IO).launch {
                    delay(500) // Wait half second to confirm network
                    
                    // Double-check that network is still connected
                    if (networkUtils.isNetworkConnected()) {
                        // Switch to Main thread only for updating LiveData
                        withContext(Dispatchers.Main) {
                            if (!lastNetworkStatus || (currentTime - networkStatusChangeTime) > debounceTimeMs) {
                                _isOnline.value = true
                                lastNetworkStatus = true
                                networkStatusChangeTime = currentTime
                                Log.d(TAG, "Network connection available (confirmed)")
                            }
                        }
                    }
                }
            }
            
            override fun onLost(network: Network) {
                // Add a larger delay before reporting network as lost
                // to prevent brief network transitions from affecting the UI
                CoroutineScope(Dispatchers.IO).launch {
                    delay(2000) // Wait 2 seconds (was 1 second)
                    
                    // Check if network is still unavailable
                    if (!networkUtils.isNetworkConnected()) {
                        val currentTime = System.currentTimeMillis()
                        
                        // Switch to Main thread only for updating LiveData
                        withContext(Dispatchers.Main) {
                            if (lastNetworkStatus || (currentTime - networkStatusChangeTime) > debounceTimeMs) {
                                _isOnline.value = false
                                lastNetworkStatus = false
                                networkStatusChangeTime = currentTime
                                Log.d(TAG, "Network connection lost (confirmed)")
                            }
                        }
                    }
                }
            }
            
            override fun onUnavailable() {
                // Also add a delay for onUnavailable
                CoroutineScope(Dispatchers.IO).launch {
                    delay(1000) // Wait 1 second
                    
                    // Double check network status
                    if (!networkUtils.isNetworkConnected()) {
                        val currentTime = System.currentTimeMillis()
                        
                        // Switch to Main thread only for updating LiveData
                        withContext(Dispatchers.Main) {
                            if (lastNetworkStatus || (currentTime - networkStatusChangeTime) > debounceTimeMs) {
                                _isOnline.value = false
                                lastNetworkStatus = false
                                networkStatusChangeTime = currentTime
                                Log.d(TAG, "Network unavailable (confirmed)")
                            }
                        }
                    }
                }
            }
        })
        
        // Initialize with current status using networkUtils but with a small delay
        CoroutineScope(Dispatchers.IO).launch {
            delay(1000) // Wait 1 second (was 500ms)
            val isConnected = networkUtils.isNetworkConnected()
            
            // Switch to Main thread only for updating LiveData
            withContext(Dispatchers.Main) {
                _isOnline.value = isConnected
                lastNetworkStatus = isConnected
                Log.d(TAG, "Initial network state: ${if (isConnected) "Connected" else "Disconnected"}")
            }
        }
    }
    
    /**
     * Process the offline queue when network is available
     */
    private suspend fun processOfflineQueue() {
        if (!networkUtils.isActiveNetworkConnected()) {
            return
        }
        
        offlineQueue.processQueue { queueItem ->
            try {
                when (queueItem.dataType) {
                    OfflineQueue.DataType.TRANSACTION -> processTransactionQueueItem(queueItem)
                    OfflineQueue.DataType.INVESTMENT -> processInvestmentQueueItem(queueItem)
                    OfflineQueue.DataType.NOTE -> processNoteQueueItem(queueItem)
                    OfflineQueue.DataType.STUDENT -> processStudentQueueItem(queueItem)
                    OfflineQueue.DataType.EVENT -> processEventQueueItem(queueItem)
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
        return when (queueItem.operation) {
            OfflineQueue.Operation.INSERT, OfflineQueue.Operation.UPDATE -> {
                val transaction = gson.fromJson(queueItem.jsonData, Transaction::class.java)
                firebaseManager.saveTransaction(transaction)
                true
            }
            OfflineQueue.Operation.DELETE -> {
                val transaction = gson.fromJson(queueItem.jsonData, Transaction::class.java)
                firebaseManager.deleteTransaction(transaction)
                true
            }
        }
    }
    
    private suspend fun processInvestmentQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operation) {
            OfflineQueue.Operation.INSERT, OfflineQueue.Operation.UPDATE -> {
                val investment = gson.fromJson(queueItem.jsonData, Investment::class.java)
                firebaseManager.saveInvestment(investment)
                true
            }
            OfflineQueue.Operation.DELETE -> {
                val investment = gson.fromJson(queueItem.jsonData, Investment::class.java)
                firebaseManager.deleteInvestment(investment)
                true
            }
        }
    }
    
    private suspend fun processNoteQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operation) {
            OfflineQueue.Operation.INSERT, OfflineQueue.Operation.UPDATE -> {
                val note = gson.fromJson(queueItem.jsonData, Note::class.java)
                firebaseManager.saveNote(note)
                true
            }
            OfflineQueue.Operation.DELETE -> {
                val note = gson.fromJson(queueItem.jsonData, Note::class.java)
                firebaseManager.deleteNote(note)
                true
            }
        }
    }
    
    private suspend fun processStudentQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operation) {
            OfflineQueue.Operation.INSERT, OfflineQueue.Operation.UPDATE -> {
                val student = gson.fromJson(queueItem.jsonData, WTStudent::class.java)
                firebaseManager.saveStudent(student)
                true
            }
            OfflineQueue.Operation.DELETE -> {
                val student = gson.fromJson(queueItem.jsonData, WTStudent::class.java)
                firebaseManager.deleteStudent(student)
                true
            }
        }
    }
    
    private suspend fun processEventQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operation) {
            OfflineQueue.Operation.INSERT, OfflineQueue.Operation.UPDATE -> {
                val event = gson.fromJson(queueItem.jsonData, Event::class.java)
                insertEvent(event)
                true
            }
            OfflineQueue.Operation.DELETE -> {
                val event = gson.fromJson(queueItem.jsonData, Event::class.java)
                deleteEvent(event)
                true
            }
        }
    }
    
    private suspend fun processWTLessonQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operation) {
            OfflineQueue.Operation.INSERT, OfflineQueue.Operation.UPDATE -> {
                val lesson = gson.fromJson(queueItem.jsonData, WTLesson::class.java)
                insertWTLesson(lesson)
                true
            }
            OfflineQueue.Operation.DELETE -> {
                val lesson = gson.fromJson(queueItem.jsonData, WTLesson::class.java)
                deleteWTLesson(lesson)
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
     * Refreshes all data from Firebase with robust error handling
     */
    suspend fun refreshAllData() {
        _isLoading.postValue(true)
        
        try {
            // First check if we should refresh from network
            if (!networkUtils.isActiveNetworkConnected()) {
                _isLoading.postValue(false)
                return // Use cached data
            }
            
            refreshTransactions()
            refreshInvestments()
            refreshNotes()
            refreshStudents()
            refreshEvents()
            refreshWTLessons()
            
        } catch (e: Exception) {
            _errorMessage.postValue("Error refreshing data: ${e.message}")
        } finally {
            _isLoading.postValue(false)
        }
    }
    
    /**
     * Force refresh all data regardless of cache state
     */
    suspend fun forceRefreshAllData() {
        if (networkUtils.isActiveNetworkConnected()) {
            // Clear all caches first
            cacheManager.clearAllCache()
            // Then refresh from network
            refreshAllData()
        } else {
            _errorMessage.postValue("Network unavailable. Cannot force refresh data.")
        }
    }
    
    /**
     * Delete all data from Firestore database
     */
    suspend fun clearAllFirestoreData(): Boolean {
        return try {
            if (networkUtils.isActiveNetworkConnected()) {
                // Clear all data in Firestore
                firebaseManager.clearAllFirestoreData()
                
                // Clear local cache
                cacheManager.clearAllCache()
                
                // Clear offline queue
                offlineQueue.clearQueue()
                
                // Reset local data collections
                _transactions.value = emptyList()
                _investments.value = emptyList()
                _notes.value = emptyList()
                _students.value = emptyList()
                _events.value = emptyList()
                _wtLessons.value = emptyList()
                
                true
            } else {
                _errorMessage.postValue("Network unavailable. Cannot clear Firestore data.")
                false
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error clearing Firestore data: ${e.message}")
            false
        }
    }
    
    // Transaction methods
    suspend fun refreshTransactions() {
        if (!networkUtils.isActiveNetworkConnected()) {
            return // Use cached data
        }
        
        try {
            val transactionList = firebaseManager.getTransactions()
            _transactions.value = transactionList
            
            // Update cache
            cacheManager.cacheTransactions(transactionList)
            
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
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.saveTransaction(transaction)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.TRANSACTION,
                    OfflineQueue.Operation.INSERT,
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
        if (networkUtils.isActiveNetworkConnected()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firebaseManager.saveTransaction(transaction)
                } catch (e: Exception) {
                    // Add to offline queue
                    offlineQueue.enqueue(
                        OfflineQueue.DataType.TRANSACTION,
                        OfflineQueue.Operation.UPDATE,
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
                offlineQueue.enqueue(
                    OfflineQueue.DataType.TRANSACTION,
                    OfflineQueue.Operation.UPDATE,
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
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.deleteTransaction(transaction)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.TRANSACTION,
                    OfflineQueue.Operation.DELETE,
                    gson.toJson(transaction)
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
        if (!networkUtils.isActiveNetworkConnected()) {
            return // Use cached data
        }
        
        try {
            val investmentList = firebaseManager.getInvestments()
            _investments.value = investmentList
            
            // Update cache
            cacheManager.cacheInvestments(investmentList)
            
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
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.saveInvestment(investment)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.INVESTMENT,
                    OfflineQueue.Operation.INSERT,
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
        if (networkUtils.isActiveNetworkConnected()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firebaseManager.saveInvestment(investment)
                } catch (e: Exception) {
                    // Add to offline queue
                    offlineQueue.enqueue(
                        OfflineQueue.DataType.INVESTMENT,
                        OfflineQueue.Operation.UPDATE,
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
                offlineQueue.enqueue(
                    OfflineQueue.DataType.INVESTMENT,
                    OfflineQueue.Operation.UPDATE,
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
            
            // Log the deletion for debugging
            Log.d(TAG, "Deleting investment with ID: ${investment.id}, Name: ${investment.name}, Amount: ${investment.amount}")
            
            // Then delete from Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.deleteInvestment(investment)
                Log.d(TAG, "Successfully deleted investment from Firebase")
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.INVESTMENT,
                    OfflineQueue.Operation.DELETE,
                    gson.toJson(investment)
                )
                _errorMessage.postValue("Investment deleted locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
            
            // Force refresh balance calculations
            // This ensures HomeViewModel is notified of the change
            viewModelScope.launch {
                delay(100) // Brief delay to ensure operations complete
                notifyInvestmentChange()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting investment: ${e.message}", e)
            _errorMessage.postValue("Error deleting investment: ${e.message}")
        }
    }
    
    // Helper method to notify listeners of investment changes
    private fun notifyInvestmentChange() {
        val currentInvestments = _investments.value
        _investments.value = currentInvestments // Trigger updates by resetting the same value
    }
    
    // Note methods
    suspend fun refreshNotes() {
        if (!networkUtils.isActiveNetworkConnected()) {
            return // Use cached data
        }
        
        try {
            val noteList = firebaseManager.getNotes()
            _notes.value = noteList
            
            // Update cache
            cacheManager.cacheNotes(noteList)
            
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
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.saveNote(note)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.NOTE,
                    OfflineQueue.Operation.INSERT,
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
        if (networkUtils.isActiveNetworkConnected()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firebaseManager.saveNote(note)
                } catch (e: Exception) {
                    // Add to offline queue
                    offlineQueue.enqueue(
                        OfflineQueue.DataType.NOTE,
                        OfflineQueue.Operation.UPDATE,
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
                offlineQueue.enqueue(
                    OfflineQueue.DataType.NOTE,
                    OfflineQueue.Operation.UPDATE,
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
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.deleteNote(note)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.NOTE,
                    OfflineQueue.Operation.DELETE,
                    gson.toJson(note)
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
        if (!networkUtils.isActiveNetworkConnected()) {
            return // Use cached data
        }
        
        try {
            val studentList = firebaseManager.getStudents()
            _students.value = studentList
            
            // Update cache
            cacheManager.cacheStudents(studentList)
            
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
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.saveStudent(student)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.STUDENT,
                    OfflineQueue.Operation.INSERT,
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
     * @return A boolean indicating whether the update was successful
     */
    suspend fun updateStudent(student: WTStudent): Boolean {
        try {
            // Check for existing students first by ID or name
            val existingStudents = _students.value.filter { 
                it.id == student.id || it.name.equals(student.name, ignoreCase = true)
            }
            
            // Get latest local cache copy
            val currentList = _students.value.toMutableList()
            
            // Flag to track if we found and updated an existing student
            var studentUpdated = false
            var studentToSave = student
            
            if (existingStudents.isNotEmpty()) {
                // Update all matching students to prevent duplicates
                existingStudents.forEach { existingStudent ->
                    val index = currentList.indexOfFirst { it.id == existingStudent.id }
                    if (index != -1) {
                        // Keep the same ID but update all other fields
                        val updatedStudent = student.copy(id = existingStudent.id)
                        currentList[index] = updatedStudent
                        
                        // Use the updated student for saving to Firebase
                        studentToSave = updatedStudent
                        studentUpdated = true
                    }
                }
            }
            
            // If no students were updated, add as new
            if (!studentUpdated) {
                currentList.add(studentToSave)
            }
            
            // Update local cache, ensuring no duplicates by ID
            _students.value = currentList.distinctBy { it.id }
            
            // Then update Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                try {
                    val success = firebaseManager.saveStudent(studentToSave)
                    if (!success) {
                        throw Exception("Firebase save operation failed")
                    }
                    return true
                } catch (e: Exception) {
                    // Add to offline queue
                    offlineQueue.enqueue(
                        OfflineQueue.DataType.STUDENT,
                        OfflineQueue.Operation.UPDATE,
                        gson.toJson(studentToSave)
                    )
                    
                    // Update pending operations count
                    updatePendingOperationsCount()
                    
                    // Show error message
                    _errorMessage.postValue("Failed to save student: ${e.message}")
                    return false
                }
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.STUDENT,
                    OfflineQueue.Operation.UPDATE,
                    gson.toJson(studentToSave)
                )
                
                // Update pending operations count
                updatePendingOperationsCount()
                return true // Consider it a success since it's queued for later
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error updating student: ${e.message}")
            return false
        }
    }
    
    suspend fun deleteStudent(student: WTStudent) {
        try {
            // Update local cache immediately
            val currentStudents = _students.value.toMutableList()
            currentStudents.removeIf { it.id == student.id }
            _students.value = currentStudents
            
            // Then delete from Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.deleteStudent(student)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.STUDENT,
                    OfflineQueue.Operation.DELETE,
                    gson.toJson(student)
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
        if (!networkUtils.isActiveNetworkConnected()) {
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
        if (!networkUtils.isActiveNetworkConnected()) {
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
    suspend fun insertEvent(title: String, description: String?, date: Date) {
        val event = Event(
            id = UUID.randomUUID().mostSignificantBits,
            title = title,
            description = description,
            date = date
        )
        insertEvent(event)
    }
    
    suspend fun insertEvent(event: Event) {
        withContext(Dispatchers.IO) {
            try {
                if (networkUtils.isActiveNetworkConnected()) {
                    // Generate ID if not present
                    val eventWithId = if (event.id == 0L) {
                        event.copy(id = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)
                    } else {
                        event
                    }
                    
                    // Save to Firebase
                    firebaseManager.saveEvent(eventWithId).await()
                    
                    // Update local cache - switch to Main thread for LiveData updates
                    withContext(Dispatchers.Main) {
                        val currentEvents = _events.value.toMutableList()
                        val index = currentEvents.indexOfFirst { it.id == eventWithId.id }
                        if (index >= 0) {
                            currentEvents[index] = eventWithId
                        } else {
                            currentEvents.add(eventWithId)
                        }
                        _events.value = currentEvents
                    }
                } else {
                    // Queue for later
                    offlineQueue.enqueue(
                        OfflineQueue.DataType.EVENT,
                        OfflineQueue.Operation.INSERT,
                        gson.toJson(event)
                    )
                }
            } catch (e: Exception) {
                // Handle error
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error inserting event: ${e.message}"
                }
            }
        }
    }
    
    suspend fun deleteEvent(event: Event) {
        withContext(Dispatchers.IO) {
            try {
                if (networkUtils.isActiveNetworkConnected()) {
                    // Delete from Firebase
                    firebaseManager.deleteEvent(event.id).await()
                    
                    // Update local cache - switch to Main thread for LiveData updates
                    withContext(Dispatchers.Main) {
                        val currentEvents = _events.value.toMutableList()
                        currentEvents.removeAll { it.id == event.id }
                        _events.value = currentEvents
                    }
                } else {
                    // Queue for later
                    offlineQueue.enqueue(
                        OfflineQueue.DataType.EVENT,
                        OfflineQueue.Operation.DELETE,
                        gson.toJson(event)
                    )
                }
            } catch (e: Exception) {
                // Handle error
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error deleting event: ${e.message}"
                }
            }
        }
    }
    
    // WT Lessons
    suspend fun insertWTLesson(lesson: WTLesson) {
        withContext(Dispatchers.IO) {
            try {
                if (networkUtils.isActiveNetworkConnected()) {
                    // Generate ID if not present
                    val lessonWithId = if (lesson.id == 0L) {
                        lesson.copy(id = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)
                    } else {
                        lesson
                    }
                    
                    // Save to Firebase
                    firebaseManager.saveWTLesson(lessonWithId).await()
                    
                    // Update cache
                    val currentLessons = _wtLessons.value.toMutableList()
                    val index = currentLessons.indexOfFirst { it.id == lessonWithId.id }
                    if (index >= 0) {
                        currentLessons[index] = lessonWithId
                    } else {
                        currentLessons.add(lessonWithId)
                    }
                    withContext(Dispatchers.Main) {
                        _wtLessons.value = currentLessons
                    }
                } else {
                    // Queue for later
                    offlineQueue.enqueue(
                        OfflineQueue.DataType.WT_LESSON,
                        OfflineQueue.Operation.INSERT,
                        gson.toJson(lesson)
                    )
                }
            } catch (e: Exception) {
                // Handle error
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error saving Wing Tzun lesson: ${e.message}"
                }
            }
        }
    }
    
    suspend fun deleteWTLesson(lesson: WTLesson) {
        withContext(Dispatchers.IO) {
            try {
                if (networkUtils.isActiveNetworkConnected()) {
                    // Delete from Firestore
                    firebaseManager.deleteWTLesson(lesson.id).await()
                    
                    // Update cache
                    val currentLessons = _wtLessons.value.toMutableList()
                    currentLessons.removeIf { it.id == lesson.id }
                    withContext(Dispatchers.Main) {
                        _wtLessons.value = currentLessons
                    }
                } else {
                    // Queue for later
                    offlineQueue.enqueue(
                        OfflineQueue.DataType.WT_LESSON,
                        OfflineQueue.Operation.DELETE,
                        gson.toJson(lesson)
                    )
                }
            } catch (e: Exception) {
                // Handle error
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error deleting Wing Tzun lesson: ${e.message}"
                }
            }
        }
    }
    
    // WT Students
    suspend fun updateWTStudent(student: WTStudent) {
        withContext(Dispatchers.IO) {
            try {
                if (networkUtils.isActiveNetworkConnected()) {
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
                    offlineQueue.enqueue(
                        OfflineQueue.DataType.STUDENT,
                        OfflineQueue.Operation.UPDATE,
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
    suspend fun refreshEvents() {
        if (!networkUtils.isActiveNetworkConnected()) {
            return // Use cached data
        }
        
        try {
            // Show loading state
            withContext(Dispatchers.Main) {
                _isLoading.value = true
            }
            
            val eventList = firebaseManager.getEvents()
            
            // Update cache first
            cacheManager.cacheEvents(eventList)
            
            // Then update LiveData on main thread
            withContext(Dispatchers.Main) {
                _events.value = eventList
                _isLoading.value = false
            }
            
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _errorMessage.value = "Error loading events: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Refreshes WT lessons from Firestore with robust error handling
     * @param forceRefresh If true, will attempt to refresh from Firebase even if network is unavailable
     */
    suspend fun refreshWTLessons(forceRefresh: Boolean = false) {
        if (!networkUtils.isActiveNetworkConnected() && !forceRefresh) {
            Log.d(TAG, "Network unavailable, using cached lessons data")
            return // Use cached data
        }
        
        try {
            // Show loading state
            withContext(Dispatchers.Main) {
                _isLoading.value = true
            }
            
            // Log the refresh attempt
            Log.d(TAG, "Refreshing WT lessons from Firebase")
            
            val lessonList = firebaseManager.getAllWTLessons()
            
            // Log the fetch result
            Log.d(TAG, "Retrieved ${lessonList.size} lessons from Firebase")
            
            // Update cache first
            cacheManager.cacheLessons(lessonList)
            
            // Then update LiveData on main thread
            withContext(Dispatchers.Main) {
                _wtLessons.value = lessonList
                _isLoading.value = false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading lessons: ${e.message}", e)
            withContext(Dispatchers.Main) {
                _errorMessage.value = "Error loading lessons: ${e.message}"
                _isLoading.value = false
            }
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