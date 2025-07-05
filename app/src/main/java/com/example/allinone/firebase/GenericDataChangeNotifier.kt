package com.example.allinone.firebase

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Generic data change notifier that replaces the repetitive methods in DataChangeNotifier.
 * Uses a map-based approach to reduce code duplication and improve maintainability.
 */
object GenericDataChangeNotifier {
    
    // Map to store change flags for different data types
    private val changeFlags = mutableMapOf<String, MutableLiveData<Boolean>>()
    
    // Supported data types
    object DataTypes {
        const val TRANSACTIONS = "transactions"
        const val INVESTMENTS = "investments"
        const val NOTES = "notes"
        const val TASKS = "tasks"
        const val TASK_GROUPS = "taskGroups"
        const val STUDENTS = "students"
        const val EVENTS = "events"
        const val LESSONS = "lessons"
        const val REGISTRATIONS = "registrations"
        const val PROGRAMS = "programs"
        const val WORKOUTS = "workouts"
    }
    
    /**
     * Initialize change flags for all supported data types
     */
    init {
        val dataTypes = listOf(
            DataTypes.TRANSACTIONS, DataTypes.INVESTMENTS, DataTypes.NOTES,
            DataTypes.TASKS, DataTypes.TASK_GROUPS, DataTypes.STUDENTS,
            DataTypes.EVENTS, DataTypes.LESSONS, DataTypes.REGISTRATIONS,
            DataTypes.PROGRAMS, DataTypes.WORKOUTS
        )
        
        dataTypes.forEach { dataType ->
            changeFlags[dataType] = MutableLiveData<Boolean>()
        }
    }
    
    /**
     * Notify that data has changed for a specific type
     */
    fun notifyDataChanged(dataType: String) {
        changeFlags[dataType]?.postValue(true)
    }
    
    /**
     * Get change observable for a specific data type
     */
    fun getDataChangedObservable(dataType: String): LiveData<Boolean>? {
        return changeFlags[dataType]
    }
    
    /**
     * Reset change flag for a specific data type
     */
    fun resetChangeFlag(dataType: String) {
        changeFlags[dataType]?.postValue(false)
    }
    
    /**
     * Check if data type is supported
     */
    fun isDataTypeSupported(dataType: String): Boolean {
        return changeFlags.containsKey(dataType)
    }
    
    /**
     * Get all supported data types
     */
    fun getSupportedDataTypes(): Set<String> {
        return changeFlags.keys
    }
    
    // Convenience methods for backward compatibility
    fun notifyTransactionsChanged() = notifyDataChanged(DataTypes.TRANSACTIONS)
    fun notifyInvestmentsChanged() = notifyDataChanged(DataTypes.INVESTMENTS)
    fun notifyNotesChanged() = notifyDataChanged(DataTypes.NOTES)
    fun notifyTasksChanged() = notifyDataChanged(DataTypes.TASKS)
    fun notifyTaskGroupsChanged() = notifyDataChanged(DataTypes.TASK_GROUPS)
    fun notifyStudentsChanged() = notifyDataChanged(DataTypes.STUDENTS)
    fun notifyEventsChanged() = notifyDataChanged(DataTypes.EVENTS)
    fun notifyLessonsChanged() = notifyDataChanged(DataTypes.LESSONS)
    fun notifyRegistrationsChanged() = notifyDataChanged(DataTypes.REGISTRATIONS)
    fun notifyProgramsChanged() = notifyDataChanged(DataTypes.PROGRAMS)
    fun notifyWorkoutsChanged() = notifyDataChanged(DataTypes.WORKOUTS)
    
    // Observables for backward compatibility
    val transactionsChanged: LiveData<Boolean>? get() = getDataChangedObservable(DataTypes.TRANSACTIONS)
    val investmentsChanged: LiveData<Boolean>? get() = getDataChangedObservable(DataTypes.INVESTMENTS)
    val notesChanged: LiveData<Boolean>? get() = getDataChangedObservable(DataTypes.NOTES)
    val tasksChanged: LiveData<Boolean>? get() = getDataChangedObservable(DataTypes.TASKS)
    val taskGroupsChanged: LiveData<Boolean>? get() = getDataChangedObservable(DataTypes.TASK_GROUPS)
    val studentsChanged: LiveData<Boolean>? get() = getDataChangedObservable(DataTypes.STUDENTS)
    val eventsChanged: LiveData<Boolean>? get() = getDataChangedObservable(DataTypes.EVENTS)
    val lessonsChanged: LiveData<Boolean>? get() = getDataChangedObservable(DataTypes.LESSONS)
    val registrationsChanged: LiveData<Boolean>? get() = getDataChangedObservable(DataTypes.REGISTRATIONS)
    val programsChanged: LiveData<Boolean>? get() = getDataChangedObservable(DataTypes.PROGRAMS)
    val workoutsChanged: LiveData<Boolean>? get() = getDataChangedObservable(DataTypes.WORKOUTS)
} 