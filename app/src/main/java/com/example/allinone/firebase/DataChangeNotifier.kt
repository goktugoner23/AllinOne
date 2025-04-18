package com.example.allinone.firebase

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Singleton class that notifies components when data in Firebase changes.
 * Other components that modify data should call the notify methods.
 * Observer components (like fragments) can subscribe to changes.
 */
object DataChangeNotifier {
    
    // LiveData for each collection type
    private val _transactionsChanged = MutableLiveData<Boolean>()
    val transactionsChanged: LiveData<Boolean> = _transactionsChanged
    
    private val _investmentsChanged = MutableLiveData<Boolean>()
    val investmentsChanged: LiveData<Boolean> = _investmentsChanged
    
    private val _notesChanged = MutableLiveData<Boolean>()
    val notesChanged: LiveData<Boolean> = _notesChanged
    
    private val _studentsChanged = MutableLiveData<Boolean>()
    val studentsChanged: LiveData<Boolean> = _studentsChanged
    
    private val _eventsChanged = MutableLiveData<Boolean>()
    val eventsChanged: LiveData<Boolean> = _eventsChanged
    
    private val _lessonsChanged = MutableLiveData<Boolean>()
    val lessonsChanged: LiveData<Boolean> = _lessonsChanged
    
    private val _registrationsChanged = MutableLiveData<Boolean>()
    val registrationsChanged: LiveData<Boolean> = _registrationsChanged
    
    // Add LiveData for workout-related collections
    private val _programsChanged = MutableLiveData<Boolean>()
    val programsChanged: LiveData<Boolean> = _programsChanged
    
    private val _workoutsChanged = MutableLiveData<Boolean>()
    val workoutsChanged: LiveData<Boolean> = _workoutsChanged
    
    // Notification methods to be called when data changes
    fun notifyTransactionsChanged() {
        _transactionsChanged.postValue(true)
    }
    
    fun notifyInvestmentsChanged() {
        _investmentsChanged.postValue(true)
    }
    
    fun notifyNotesChanged() {
        _notesChanged.postValue(true)
    }
    
    fun notifyStudentsChanged() {
        _studentsChanged.postValue(true)
    }
    
    fun notifyEventsChanged() {
        _eventsChanged.postValue(true)
    }
    
    fun notifyLessonsChanged() {
        _lessonsChanged.postValue(true)
    }
    
    fun notifyRegistrationsChanged() {
        _registrationsChanged.postValue(true)
    }
    
    // Add notification methods for workout-related collections
    fun notifyProgramsChanged() {
        _programsChanged.postValue(true)
    }
    
    fun notifyWorkoutsChanged() {
        _workoutsChanged.postValue(true)
    }
    
    /**
     * Notify that data in a specific collection has changed
     * @param collectionName The name of the collection that changed
     */
    fun notifyCollectionChanged(collectionName: String) {
        when (collectionName) {
            "transactions" -> notifyTransactionsChanged()
            "investments" -> notifyInvestmentsChanged()
            "notes" -> notifyNotesChanged()
            "students" -> notifyStudentsChanged()
            "events" -> notifyEventsChanged()
            "wtLessons" -> notifyLessonsChanged()
            "registrations" -> notifyRegistrationsChanged()
            "programs" -> notifyProgramsChanged()
            "workouts" -> notifyWorkoutsChanged()
        }
    }
} 