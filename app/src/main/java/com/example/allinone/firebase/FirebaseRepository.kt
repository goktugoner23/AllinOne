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
import com.example.allinone.data.Task
import com.example.allinone.data.TaskGroup
import com.example.allinone.data.Transaction
import com.example.allinone.data.WTStudent
import com.example.allinone.data.Event
import com.example.allinone.data.WTLesson
import com.example.allinone.data.WTRegistration
import com.example.allinone.data.Program
import com.example.allinone.data.Workout
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
import com.example.allinone.firebase.FirebaseStorageUtil
import com.example.allinone.firebase.DataChangeNotifier

/**
 * A repository that uses Firebase for all data operations.
 * This replaces all Room-based repositories.
 */
class FirebaseRepository(private val context: Context) {
    private val firebaseManager = FirebaseManager(context)
    private val networkUtils = (context.applicationContext as AllinOneApplication).networkUtils
    private val offlineQueue = OfflineQueue(context)
    private val cacheManager = (context.applicationContext as AllinOneApplication).cacheManager
    private val storageUtil = FirebaseStorageUtil(context)
    private val gson = Gson()
    private val idManager = FirebaseIdManager()

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val appContext = context.applicationContext

    // Network status
    val isNetworkAvailable: LiveData<Boolean> = networkUtils.isNetworkAvailable

    // Cache for data
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    private val _investments = MutableStateFlow<List<Investment>>(emptyList())
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    private val _taskGroups = MutableStateFlow<List<TaskGroup>>(emptyList())
    private val _students = MutableStateFlow<List<WTStudent>>(emptyList())
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    private val _wtLessons = MutableStateFlow<List<WTLesson>>(emptyList())
    private val _registrations = MutableStateFlow<List<WTRegistration>>(emptyList())
    private val _programs = MutableStateFlow<List<Program>>(emptyList())
    private val _workouts = MutableStateFlow<List<Workout>>(emptyList())

    // Public flows
    val transactions: StateFlow<List<Transaction>> = _transactions
    val investments: StateFlow<List<Investment>> = _investments
    val notes: StateFlow<List<Note>> = _notes
    val tasks: StateFlow<List<Task>> = _tasks
    val taskGroups: StateFlow<List<TaskGroup>> = _taskGroups
    val students: StateFlow<List<WTStudent>> = _students
    val events: StateFlow<List<Event>> = _events
    val wtLessons: StateFlow<List<WTLesson>> = _wtLessons
    val registrations: StateFlow<List<WTRegistration>> = _registrations
    val programs: StateFlow<List<Program>> = _programs
    val workouts: StateFlow<List<Workout>> = _workouts

    // Error handling
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

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

            // Load tasks
            val cachedTasks = cacheManager.getCachedTasks()
            if (cachedTasks.isNotEmpty()) {
                _tasks.value = cachedTasks
                Log.d(TAG, "Loaded ${cachedTasks.size} tasks from cache")
            }

            // Load task groups
            val cachedTaskGroups = cacheManager.getCachedTaskGroups()
            if (cachedTaskGroups.isNotEmpty()) {
                _taskGroups.value = cachedTaskGroups
                Log.d(TAG, "Loaded ${cachedTaskGroups.size} task groups from cache")
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

            // Load registrations
            val cachedRegistrations = cacheManager.getCachedRegistrations()
            if (cachedRegistrations.isNotEmpty()) {
                _registrations.value = cachedRegistrations
                Log.d(TAG, "Loaded ${cachedRegistrations.size} registrations from cache")
            }

            // Load programs
            val cachedPrograms = cacheManager.getCachedPrograms()
            if (cachedPrograms.isNotEmpty()) {
                _programs.value = cachedPrograms
                Log.d(TAG, "Loaded ${cachedPrograms.size} programs from cache")
            }

            // Load workouts
            val cachedWorkouts = cacheManager.getCachedWorkouts()
            if (cachedWorkouts.isNotEmpty()) {
                _workouts.value = cachedWorkouts
                Log.d(TAG, "Loaded ${cachedWorkouts.size} workouts from cache")
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
                    OfflineQueue.DataType.TASK -> processTaskQueueItem(queueItem)
                    OfflineQueue.DataType.TASK_GROUP -> processTaskGroupQueueItem(queueItem)
                    OfflineQueue.DataType.STUDENT -> processStudentQueueItem(queueItem)
                    OfflineQueue.DataType.EVENT -> processEventQueueItem(queueItem)
                    OfflineQueue.DataType.WT_LESSON -> processWTLessonQueueItem(queueItem)
                    OfflineQueue.DataType.REGISTRATION -> processRegistrationQueueItem(queueItem)
                    OfflineQueue.DataType.PROGRAM -> processProgramQueueItem(queueItem)
                    OfflineQueue.DataType.WORKOUT -> processWorkoutQueueItem(queueItem)
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

    private suspend fun processTaskQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operation) {
            OfflineQueue.Operation.INSERT, OfflineQueue.Operation.UPDATE -> {
                val task = gson.fromJson(queueItem.jsonData, Task::class.java)
                firebaseManager.saveTask(task)
                true
            }
            OfflineQueue.Operation.DELETE -> {
                val task = gson.fromJson(queueItem.jsonData, Task::class.java)
                firebaseManager.deleteTask(task)
                true
            }
        }
    }

    private suspend fun processTaskGroupQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operation) {
            OfflineQueue.Operation.INSERT, OfflineQueue.Operation.UPDATE -> {
                val taskGroup = gson.fromJson(queueItem.jsonData, TaskGroup::class.java)
                firebaseManager.saveTaskGroup(taskGroup)
                true
            }
            OfflineQueue.Operation.DELETE -> {
                val taskGroup = gson.fromJson(queueItem.jsonData, TaskGroup::class.java)
                firebaseManager.deleteTaskGroup(taskGroup)
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

    private suspend fun processRegistrationQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operation) {
            OfflineQueue.Operation.INSERT, OfflineQueue.Operation.UPDATE -> {
                val registration = gson.fromJson(queueItem.jsonData, WTRegistration::class.java)
                insertRegistration(registration)
                true
            }
            OfflineQueue.Operation.DELETE -> {
                val registration = gson.fromJson(queueItem.jsonData, WTRegistration::class.java)
                deleteRegistration(registration)
                true
            }
        }
    }

    private suspend fun processProgramQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operation) {
            OfflineQueue.Operation.INSERT, OfflineQueue.Operation.UPDATE -> {
                val program = gson.fromJson(queueItem.jsonData, Program::class.java)
                firebaseManager.saveProgram(program)
                refreshPrograms() // Refresh to get the latest data
                true
            }
            OfflineQueue.Operation.DELETE -> {
                val program = gson.fromJson(queueItem.jsonData, Program::class.java)
                firebaseManager.deleteProgram(program.id)
                refreshPrograms() // Refresh to get the latest data
                true
            }
        }
    }

    private suspend fun processWorkoutQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return when (queueItem.operation) {
            OfflineQueue.Operation.INSERT, OfflineQueue.Operation.UPDATE -> {
                val workout = gson.fromJson(queueItem.jsonData, Workout::class.java)
                firebaseManager.saveWorkout(workout)
                refreshWorkouts() // Refresh to get the latest data
                true
            }
            OfflineQueue.Operation.DELETE -> {
                val workout = gson.fromJson(queueItem.jsonData, Workout::class.java)
                firebaseManager.deleteWorkout(workout.id)
                refreshWorkouts() // Refresh to get the latest data
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
        Log.d(TAG, "Starting refreshAllData()")

        try {
            // First check if we should refresh from network
            if (!networkUtils.isActiveNetworkConnected()) {
                Log.d(TAG, "Network not connected, using cached data")
                _isLoading.postValue(false)
                return // Use cached data
            }

            Log.d(TAG, "Network connected, refreshing all data from Firebase")

            refreshTransactions()
            refreshInvestments()
            refreshNotes()
            refreshTasks()
            refreshTaskGroups()
            refreshStudents()
            refreshEvents()
            refreshWTLessons()
            refreshRegistrations()
            refreshPrograms()
            refreshWorkouts()

            // Log state after refresh
            Log.d(TAG, "All data refreshed. Current counts - " +
                "Transactions: ${_transactions.value.size}, " +
                "Investments: ${_investments.value.size}, " +
                "Notes: ${_notes.value.size}, " +
                "Students: ${_students.value.size}, " +
                "Registrations: ${_registrations.value.size}")

        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing data: ${e.message}", e)
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
                _tasks.value = emptyList()
                _taskGroups.value = emptyList()
                _students.value = emptyList()
                _events.value = emptyList()
                _wtLessons.value = emptyList()
                _registrations.value = emptyList()

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
        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                val transactions = firebaseManager.getTransactions()
                _transactions.value = transactions
                cacheManager.cacheTransactions(transactions)
                _isLoading.postValue(false)

                // Notify other components
                DataChangeNotifier.notifyTransactionsChanged()
            } catch (e: Exception) {
                _errorMessage.postValue("Error refreshing transactions: ${e.message}")
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * Insert a new transaction directly with a Transaction object
     */
    suspend fun insertTransaction(transaction: Transaction) {
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
     * Insert a new transaction into Firebase
     */
    suspend fun insertTransaction(
        amount: Double,
        type: String,
        description: String?,
        isIncome: Boolean,
        category: String,
        relatedRegistrationId: Long? = null,
        date: Long? = null
    ) {
        Log.d(TAG, "Inserting transaction: $type, $amount, $isIncome")

        // Get next sequential ID for the transaction
        val transactionId = idManager.getNextId("transactions")

        val transaction = Transaction(
            id = transactionId,
            amount = amount,
            type = type,
            description = description ?: "",
            isIncome = isIncome,
            date = Date(date ?: System.currentTimeMillis()),
            category = category,
            relatedRegistrationId = relatedRegistrationId
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
        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                val investments = firebaseManager.getInvestments()
                _investments.value = investments
                cacheManager.cacheInvestments(investments)
                _isLoading.postValue(false)

                // Notify other components
                DataChangeNotifier.notifyInvestmentsChanged()
            } catch (e: Exception) {
                _errorMessage.postValue("Error refreshing investments: ${e.message}")
                _isLoading.postValue(false)
            }
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

            // Force refresh balance calculations using a proper coroutine scope
            CoroutineScope(Dispatchers.Main).launch {
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

    /**
     * Get an investment by its ID
     * @param id The ID of the investment to retrieve
     * @return The investment if found, null otherwise
     */
    fun getInvestmentById(id: Long): Investment? {
        return _investments.value.find { it.id == id }
    }

    // Note methods
    suspend fun refreshNotes() {
        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                val notes = firebaseManager.getNotes()
                _notes.value = notes
                cacheManager.cacheNotes(notes)
                _isLoading.postValue(false)

                // Notify other components
                DataChangeNotifier.notifyNotesChanged()
            } catch (e: Exception) {
                _errorMessage.postValue("Error refreshing notes: ${e.message}")
                _isLoading.postValue(false)
            }
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

    // Task methods
    suspend fun refreshTasks() {
        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                val tasks = firebaseManager.getTasks()
                _tasks.value = tasks
                cacheManager.cacheTasks(tasks)
                _isLoading.postValue(false)

                // Notify other components
                DataChangeNotifier.notifyTasksChanged()
            } catch (e: Exception) {
                _errorMessage.postValue("Error refreshing tasks: ${e.message}")
                _isLoading.postValue(false)
            }
        }
    }

    suspend fun insertTask(task: Task) {
        try {
            // Update local cache immediately
            val currentTasks = _tasks.value.toMutableList()
            currentTasks.add(task)
            _tasks.value = currentTasks

            // Then update Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.saveTask(task)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.TASK,
                    OfflineQueue.Operation.INSERT,
                    gson.toJson(task)
                )
                _errorMessage.postValue("Task saved locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error saving task: ${e.message}")
        }
    }

    suspend fun updateTask(task: Task) {
        try {
            // Update local cache immediately
            val currentTasks = _tasks.value.toMutableList()
            val index = currentTasks.indexOfFirst { it.id == task.id }

            if (index != -1) {
                currentTasks[index] = task
            } else {
                currentTasks.add(task)
            }

            _tasks.value = currentTasks

            // Save to Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.saveTask(task)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.TASK,
                    OfflineQueue.Operation.UPDATE,
                    gson.toJson(task)
                )
                _errorMessage.postValue("Task updated locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error updating task: ${e.message}")
        }
    }

    suspend fun deleteTask(task: Task) {
        try {
            // Update local cache immediately
            val currentTasks = _tasks.value.toMutableList()
            currentTasks.removeIf { it.id == task.id }
            _tasks.value = currentTasks

            // Then delete from Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.deleteTask(task)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.TASK,
                    OfflineQueue.Operation.DELETE,
                    gson.toJson(task)
                )
                _errorMessage.postValue("Task deleted locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error deleting task: ${e.message}")
        }
    }

    // Task Group methods
    suspend fun refreshTaskGroups() {
        withContext(Dispatchers.IO) {
            try {
                _isLoading.postValue(true)
                val taskGroups = firebaseManager.getTaskGroups()
                _taskGroups.value = taskGroups
                cacheManager.cacheTaskGroups(taskGroups)
                _isLoading.postValue(false)

                // Notify other components
                DataChangeNotifier.notifyTaskGroupsChanged()
            } catch (e: Exception) {
                _errorMessage.postValue("Error refreshing task groups: ${e.message}")
                _isLoading.postValue(false)
            }
        }
    }

    suspend fun insertTaskGroup(taskGroup: TaskGroup) {
        try {
            // Update local cache immediately
            val currentTaskGroups = _taskGroups.value.toMutableList()
            currentTaskGroups.add(taskGroup)
            _taskGroups.value = currentTaskGroups

            // Then update Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.saveTaskGroup(taskGroup)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.TASK_GROUP,
                    OfflineQueue.Operation.INSERT,
                    gson.toJson(taskGroup)
                )
                _errorMessage.postValue("Task group saved locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error saving task group: ${e.message}")
        }
    }

    suspend fun updateTaskGroup(taskGroup: TaskGroup) {
        try {
            // Update local cache immediately
            val currentTaskGroups = _taskGroups.value.toMutableList()
            val index = currentTaskGroups.indexOfFirst { it.id == taskGroup.id }

            if (index != -1) {
                currentTaskGroups[index] = taskGroup
            } else {
                currentTaskGroups.add(taskGroup)
            }

            _taskGroups.value = currentTaskGroups

            // Save to Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.saveTaskGroup(taskGroup)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.TASK_GROUP,
                    OfflineQueue.Operation.UPDATE,
                    gson.toJson(taskGroup)
                )
                _errorMessage.postValue("Task group updated locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error updating task group: ${e.message}")
        }
    }

    suspend fun deleteTaskGroup(taskGroup: TaskGroup) {
        try {
            // Update local cache immediately
            val currentTaskGroups = _taskGroups.value.toMutableList()
            currentTaskGroups.removeIf { it.id == taskGroup.id }
            _taskGroups.value = currentTaskGroups

            // Then delete from Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.deleteTaskGroup(taskGroup)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.TASK_GROUP,
                    OfflineQueue.Operation.DELETE,
                    gson.toJson(taskGroup)
                )
                _errorMessage.postValue("Task group deleted locally. Will sync when network is available.")
                updatePendingOperationsCount()
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error deleting task group: ${e.message}")
        }
    }

    // WTStudent methods
    suspend fun refreshStudents() = withContext(Dispatchers.IO) {
        try {
            _isLoading.postValue(true)
            val students = firebaseManager.getStudents()
            _students.value = students
            cacheManager.cacheStudents(students)
            _isLoading.postValue(false)

            // Notify other components
            DataChangeNotifier.notifyStudentsChanged()
        } catch (e: Exception) {
            _errorMessage.postValue("Error refreshing students: ${e.message}")
            _isLoading.postValue(false)
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
            Log.d(TAG, "Starting deletion of student: ID=${student.id}, Name=${student.name}")

            // Update local cache immediately
            val currentStudents = _students.value.toMutableList()
            currentStudents.removeIf { it.id == student.id }
            _students.value = currentStudents

            // Then delete from Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                firebaseManager.deleteStudent(student)
                Log.d(TAG, "Student successfully deleted from Firebase: ID=${student.id}")
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

            // Cache the updated list
            cacheManager.cacheStudents(_students.value)

            // Notify other components
            DataChangeNotifier.notifyStudentsChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting student: ${e.message}", e)
            _errorMessage.postValue("Error deleting student: ${e.message}")
            throw e
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
            id = idManager.getNextId("events"),
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
                        // Use sequential ID instead of random UUID
                        val nextId = firebaseManager.idManager.getNextId("events")
                        event.copy(id = nextId)
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
                        lesson.copy(id = idManager.getNextId("lessons"))
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

    // WT Registrations
    suspend fun refreshRegistrations() {
        if (!networkUtils.isActiveNetworkConnected()) {
            return // Use cached data
        }

        try {
            // Show loading state
            withContext(Dispatchers.Main) {
                _isLoading.value = true
            }

            val registrationList = firebaseManager.getRegistrations()

            // Update cache first
            cacheManager.cacheRegistrations(registrationList)

            // Then update LiveData on main thread
            withContext(Dispatchers.Main) {
                _registrations.value = registrationList
                _isLoading.value = false
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _errorMessage.value = "Error loading registrations: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    suspend fun insertRegistration(registration: WTRegistration) {
        withContext(Dispatchers.IO) {
            try {
                if (networkUtils.isActiveNetworkConnected()) {
                    // Generate ID if not present
                    val registrationWithId = if (registration.id == 0L) {
                        // Use sequential ID instead of random UUID
                        val nextId = firebaseManager.idManager.getNextId("registrations")
                        registration.copy(id = nextId)
                    } else {
                        registration
                    }

                    // Save to Firebase
                    firebaseManager.saveRegistration(registrationWithId).await()

                    // Update cache
                    val currentRegistrations = _registrations.value.toMutableList()
                    val index = currentRegistrations.indexOfFirst { it.id == registrationWithId.id }
                    if (index >= 0) {
                        currentRegistrations[index] = registrationWithId
                    } else {
                        currentRegistrations.add(registrationWithId)
                    }
                    withContext(Dispatchers.Main) {
                        _registrations.value = currentRegistrations
                    }
                } else {
                    // Queue for later
                    offlineQueue.enqueue(
                        OfflineQueue.DataType.REGISTRATION,
                        OfflineQueue.Operation.INSERT,
                        gson.toJson(registration)
                    )
                }
            } catch (e: Exception) {
                // Handle error
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error saving registration: ${e.message}"
                }
            }
        }
    }

    suspend fun updateRegistration(registration: WTRegistration) {
        withContext(Dispatchers.IO) {
            try {
                if (networkUtils.isActiveNetworkConnected()) {
                    // Save to Firebase
                    firebaseManager.saveRegistration(registration).await()

                    // Update local cache
                    val currentRegistrations = _registrations.value.toMutableList()
                    val index = currentRegistrations.indexOfFirst { it.id == registration.id }
                    if (index >= 0) {
                        currentRegistrations[index] = registration
                    } else {
                        currentRegistrations.add(registration)
                    }
                    withContext(Dispatchers.Main) {
                        _registrations.value = currentRegistrations
                    }

                    // Refresh to ensure data consistency
                    refreshRegistrations()
                } else {
                    // Queue for later
                    offlineQueue.enqueue(
                        OfflineQueue.DataType.REGISTRATION,
                        OfflineQueue.Operation.UPDATE,
                        gson.toJson(registration)
                    )
                }
            } catch (e: Exception) {
                // Handle error
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error updating registration: ${e.message}"
                }
            }
        }
    }

    suspend fun deleteRegistration(registration: WTRegistration) {
        try {
            Log.d(TAG, "Starting to delete registration with ID: ${registration.id}")

            // Delete from Firestore
            firebaseManager.deleteRegistration(registration.id).await()

            // Remove from local cache
            val currentRegistrations = _registrations.value
            val updatedRegistrations = currentRegistrations.filter { it.id != registration.id }
            _registrations.emit(updatedRegistrations)
            cacheManager.cacheRegistrations(updatedRegistrations)

            Log.d(TAG, "Successfully deleted registration with ID: ${registration.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting registration: ${e.message}", e)
            throw e
        }
    }

    // Get registrations for a specific student
    fun getRegistrationsForStudent(studentId: Long): List<WTRegistration> {
        return _registrations.value.filter { it.studentId == studentId }
    }

    // Get current active registration for a student (if any)
    fun getCurrentRegistrationForStudent(studentId: Long): WTRegistration? {
        val now = Date()
        return _registrations.value.find {
            it.studentId == studentId &&
            (it.startDate?.before(now) ?: true) &&
            (it.endDate?.after(now) ?: true)
        }
    }

    // Check if a student has an active registration
    fun isStudentCurrentlyRegistered(studentId: Long): Boolean {
        return getCurrentRegistrationForStudent(studentId) != null
    }

    // Constants
    companion object {
        private const val TAG = "FirebaseRepository"
    }

    /**
     * Delete all transactions associated with a specific registration ID
     */
    suspend fun deleteTransactionsByRegistrationId(registrationId: Long) {
        Log.d(TAG, "Looking for transactions with registrationId: $registrationId")

        // Find transactions related to this registration
        val transactionsToDelete = _transactions.value.filter {
            it.relatedRegistrationId == registrationId
        }

        if (transactionsToDelete.isEmpty()) {
            Log.d(TAG, "No transactions found for registrationId: $registrationId")
            return
        }

        Log.d(TAG, "Found ${transactionsToDelete.size} transactions to delete for registrationId: $registrationId")

        // Delete each transaction
        transactionsToDelete.forEach { transaction ->
            try {
                Log.d(TAG, "Deleting transaction ID: ${transaction.id}")
                deleteTransaction(transaction)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting transaction: ${e.message}", e)
                // Continue with other transactions even if one fails
            }
        }
    }

    /**
     * Upload a file to Firebase Storage
     * @param fileUri The URI of the file to upload
     * @param folderName The folder in Firebase Storage to save the file (e.g., "registrations", "notes")
     * @param id Optional ID to use for subfolder (e.g., studentId or registrationId)
     * @return The download URL of the uploaded file, or null if upload failed
     */
    suspend fun uploadFile(fileUri: Uri, folderName: String, id: String? = null): String? {
        Log.d(TAG, "============ UPLOAD FILE ============")
        Log.d(TAG, "Starting uploadFile: fileUri=$fileUri, folderName=$folderName, id=$id")

        try {
            if (!networkUtils.isActiveNetworkConnected()) {
                Log.e(TAG, "Upload failed: No network connection")
                return null
            }

            // Verify the URI is valid
            if (fileUri.toString().isEmpty()) {
                Log.e(TAG, "Upload failed: Empty URI")
                return null
            }

            if (!fileUri.toString().startsWith("content://")) {
                Log.e(TAG, "Upload failed: URI doesn't start with content:// - ${fileUri.toString().take(20)}")
                return null
            }

            Log.d(TAG, "Calling storageUtil.uploadFile...")
            val result = storageUtil.uploadFile(fileUri, folderName, id)

            if (result != null) {
                Log.d(TAG, "Upload successful! Result URL: ${result.take(50)}...")
            } else {
                Log.e(TAG, "Upload failed! storageUtil.uploadFile returned null")
            }

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Exception in uploadFile: ${e.javaClass.simpleName}: ${e.message}", e)
            return null
        } finally {
            Log.d(TAG, "============ END UPLOAD FILE ============")
        }
    }

    /**
     * Delete a file from Firebase Storage
     * @param fileUrl The download URL of the file to delete
     * @return True if successful, false otherwise
     */
    suspend fun deleteFile(fileUrl: String): Boolean {
        return try {
            Log.d(TAG, "Deleting file from Firebase Storage: $fileUrl")
            storageUtil.deleteFile(fileUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${e.message}", e)
            false
        }
    }

    /**
     * Delete a student's folder from Firebase Storage
     * @param studentId The ID of the student whose folder should be deleted
     * @return True if successful, false otherwise
     */
    suspend fun deleteStudentFolder(studentId: Long): Boolean {
        return try {
            Log.d(TAG, "Deleting student folder from Firebase Storage: $studentId")
            storageUtil.deleteFolder("profile_pictures", studentId.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting student folder: ${e.message}", e)
            false
        }
    }

    /**
     * Insert a student and return its generated ID
     */
    suspend fun insertStudentAndGetId(student: WTStudent): Long {
        return try {
            Log.d(TAG, "Inserting student and returning ID: ${student.name}")
            val studentId = firebaseManager.saveStudentAndGetId(student)
            refreshStudents() // Refresh to get the latest data
            studentId
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting student: ${e.message}", e)
            throw e
        }
    }

    /**
     * Insert an investment and return its generated ID
     */
    suspend fun insertInvestmentAndGetId(investment: Investment): Long {
        return try {
            Log.d(TAG, "Inserting investment and returning ID: ${investment.name}")
            val investmentId = firebaseManager.saveInvestmentAndGetId(investment)
            refreshInvestments() // Refresh to get the latest data
            investmentId
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting investment: ${e.message}", e)
            throw e
        }
    }

    /**
     * Refresh programs from Firebase
     */
    suspend fun refreshPrograms() {
        try {
            if (networkUtils.isActiveNetworkConnected()) {
                val programs = firebaseManager.getPrograms()
                _programs.value = programs
                cacheManager.cachePrograms(programs)
                Log.d(TAG, "Refreshed ${programs.size} programs from Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing programs: ${e.message}", e)
        }
    }

    /**
     * Refresh workouts from Firebase
     */
    suspend fun refreshWorkouts() {
        try {
            if (networkUtils.isActiveNetworkConnected()) {
                val workouts = firebaseManager.getWorkouts()
                _workouts.value = workouts
                cacheManager.cacheWorkouts(workouts)
                Log.d(TAG, "Refreshed ${workouts.size} workouts from Firebase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing workouts: ${e.message}", e)
        }
    }

    /**
     * Save a program to Firebase
     */
    suspend fun saveProgram(program: Program): Long {
        try {
            // Update local cache immediately
            val currentPrograms = _programs.value.toMutableList()
            val index = currentPrograms.indexOfFirst { it.id == program.id }

            if (index >= 0) {
                currentPrograms[index] = program
            } else {
                currentPrograms.add(program)
            }

            _programs.value = currentPrograms

            // Then update Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                return firebaseManager.saveProgram(program)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.PROGRAM,
                    OfflineQueue.Operation.INSERT,
                    gson.toJson(program)
                )
                _errorMessage.postValue("Program saved locally. Will sync when network is available.")
                updatePendingOperationsCount()
                return program.id
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error saving program: ${e.message}")
            throw e
        }
    }

    /**
     * Save a workout to Firebase
     */
    suspend fun saveWorkout(workout: Workout): Long {
        try {
            // Update local cache immediately
            val currentWorkouts = _workouts.value.toMutableList()
            val index = currentWorkouts.indexOfFirst { it.id == workout.id }

            if (index >= 0) {
                currentWorkouts[index] = workout
            } else {
                currentWorkouts.add(workout)
            }

            _workouts.value = currentWorkouts

            // Then update Firebase if network is available
            if (networkUtils.isActiveNetworkConnected()) {
                return firebaseManager.saveWorkout(workout)
            } else {
                // Add to offline queue
                offlineQueue.enqueue(
                    OfflineQueue.DataType.WORKOUT,
                    OfflineQueue.Operation.INSERT,
                    gson.toJson(workout)
                )
                _errorMessage.postValue("Workout saved locally. Will sync when network is available.")
                updatePendingOperationsCount()
                return workout.id
            }
        } catch (e: Exception) {
            _errorMessage.postValue("Error saving workout: ${e.message}")
            throw e
        }
    }

    /**
     * Get the next sequential ID for a resource type
     * @param resourceType The type of resource (e.g., "transactions", "investments", "notes")
     * @return The next sequential ID
     */
    suspend fun getNextId(resourceType: String): Long {
        return idManager.getNextId(resourceType)
    }
}
