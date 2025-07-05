package com.example.allinone.firebase

import android.util.Log
import com.example.allinone.data.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Generic offline queue processor that replaces the repetitive queue processing methods.
 * Uses a type-safe approach with handlers for different data types.
 */
class GenericOfflineQueueProcessor(
    private val firebaseManager: FirebaseManager,
    private val gson: Gson = Gson()
) {
    
    companion object {
        private const val TAG = "OfflineQueueProcessor"
    }
    
    // Type-safe handlers for different operations
    private val operationHandlers = mutableMapOf<OfflineQueue.DataType, OperationHandler<*>>()
    
    init {
        setupHandlers()
    }
    
    /**
     * Interface for handling operations on different data types
     */
    interface OperationHandler<T> {
        suspend fun handleInsertOrUpdate(item: T): Boolean
        suspend fun handleDelete(item: T): Boolean
        fun parseFromJson(json: String): T
    }
    
    /**
     * Setup handlers for each data type
     */
    private fun setupHandlers() {
        operationHandlers[OfflineQueue.DataType.TRANSACTION] = TransactionHandler()
        operationHandlers[OfflineQueue.DataType.INVESTMENT] = InvestmentHandler()
        operationHandlers[OfflineQueue.DataType.NOTE] = NoteHandler()
        operationHandlers[OfflineQueue.DataType.TASK] = TaskHandler()
        operationHandlers[OfflineQueue.DataType.TASK_GROUP] = TaskGroupHandler()
        operationHandlers[OfflineQueue.DataType.STUDENT] = StudentHandler()
        operationHandlers[OfflineQueue.DataType.EVENT] = EventHandler()
        operationHandlers[OfflineQueue.DataType.WT_LESSON] = WTLessonHandler()
        operationHandlers[OfflineQueue.DataType.REGISTRATION] = RegistrationHandler()
        operationHandlers[OfflineQueue.DataType.PROGRAM] = ProgramHandler()
        operationHandlers[OfflineQueue.DataType.WORKOUT] = WorkoutHandler()
    }
    
    /**
     * Process a queue item generically
     */
    suspend fun processQueueItem(queueItem: OfflineQueue.QueueItem): Boolean {
        return try {
            val handler = operationHandlers[queueItem.dataType]
            if (handler == null) {
                Log.e(TAG, "No handler found for data type: ${queueItem.dataType}")
                return false
            }
            
            processWithHandler(handler, queueItem)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing queue item: ${e.message}", e)
            false
        }
    }
    
    /**
     * Process queue item with the appropriate handler
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun processWithHandler(
        handler: OperationHandler<*>,
        queueItem: OfflineQueue.QueueItem
    ): Boolean {
        val typedHandler = handler as OperationHandler<Any>
        val item = typedHandler.parseFromJson(queueItem.jsonData ?: "")
        
        return when (queueItem.operation) {
            OfflineQueue.Operation.INSERT, OfflineQueue.Operation.UPDATE -> {
                typedHandler.handleInsertOrUpdate(item)
            }
            OfflineQueue.Operation.DELETE -> {
                typedHandler.handleDelete(item)
            }
        }
    }
    
    // Handler implementations for each data type
    
    inner class TransactionHandler : OperationHandler<Transaction> {
        override suspend fun handleInsertOrUpdate(item: Transaction): Boolean {
            return firebaseManager.saveTransaction(item)
        }
        
        override suspend fun handleDelete(item: Transaction): Boolean {
            return try {
                firebaseManager.deleteTransaction(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting transaction: ${e.message}", e)
                false
            }
        }
        
        override fun parseFromJson(json: String): Transaction {
            return gson.fromJson(json, Transaction::class.java)
        }
    }
    
    inner class InvestmentHandler : OperationHandler<Investment> {
        override suspend fun handleInsertOrUpdate(item: Investment): Boolean {
            return try {
                firebaseManager.saveInvestment(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error saving investment: ${e.message}", e)
                false
            }
        }
        
        override suspend fun handleDelete(item: Investment): Boolean {
            return try {
                firebaseManager.deleteInvestment(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting investment: ${e.message}", e)
                false
            }
        }
        
        override fun parseFromJson(json: String): Investment {
            return gson.fromJson(json, Investment::class.java)
        }
    }
    
    inner class NoteHandler : OperationHandler<Note> {
                override suspend fun handleInsertOrUpdate(item: Note): Boolean {
            return try {
                firebaseManager.saveNote(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error saving note: ${e.message}", e)
                false
            }
        }

        override suspend fun handleDelete(item: Note): Boolean {
            return try {
                firebaseManager.deleteNote(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting note: ${e.message}", e)
                false
            }
        }
        
        override fun parseFromJson(json: String): Note {
            return gson.fromJson(json, Note::class.java)
        }
    }
    
    inner class TaskHandler : OperationHandler<Task> {
                override suspend fun handleInsertOrUpdate(item: Task): Boolean {
            return try {
                firebaseManager.saveTask(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error saving task: ${e.message}", e)
                false
            }
        }

        override suspend fun handleDelete(item: Task): Boolean {
            return try {
                firebaseManager.deleteTask(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting task: ${e.message}", e)
                false
            }
        }
        
        override fun parseFromJson(json: String): Task {
            return gson.fromJson(json, Task::class.java)
        }
    }
    
    inner class TaskGroupHandler : OperationHandler<TaskGroup> {
                override suspend fun handleInsertOrUpdate(item: TaskGroup): Boolean {
            return try {
                firebaseManager.saveTaskGroup(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error saving task group: ${e.message}", e)
                false
            }
        }

        override suspend fun handleDelete(item: TaskGroup): Boolean {
            return try {
                firebaseManager.deleteTaskGroup(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting task group: ${e.message}", e)
                false
            }
        }
        
        override fun parseFromJson(json: String): TaskGroup {
            return gson.fromJson(json, TaskGroup::class.java)
        }
    }
    
    inner class StudentHandler : OperationHandler<WTStudent> {
        override suspend fun handleInsertOrUpdate(item: WTStudent): Boolean {
            return firebaseManager.saveStudent(item)
        }
        
        override suspend fun handleDelete(item: WTStudent): Boolean {
            return try {
                firebaseManager.deleteStudent(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting student: ${e.message}", e)
                false
            }
        }
        
        override fun parseFromJson(json: String): WTStudent {
            return gson.fromJson(json, WTStudent::class.java)
        }
    }
    
    inner class EventHandler : OperationHandler<Event> {
        override suspend fun handleInsertOrUpdate(item: Event): Boolean {
            return try {
                firebaseManager.saveEvent(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error saving event: ${e.message}", e)
                false
            }
        }
        
        override suspend fun handleDelete(item: Event): Boolean {
            return try {
                firebaseManager.deleteEvent(item.id)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting event: ${e.message}", e)
                false
            }
        }
        
        override fun parseFromJson(json: String): Event {
            return gson.fromJson(json, Event::class.java)
        }
    }
    
    inner class WTLessonHandler : OperationHandler<WTLesson> {
        override suspend fun handleInsertOrUpdate(item: WTLesson): Boolean {
            return try {
                firebaseManager.saveWTLesson(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error saving WTLesson: ${e.message}", e)
                false
            }
        }
        
        override suspend fun handleDelete(item: WTLesson): Boolean {
            return try {
                firebaseManager.deleteWTLesson(item.id)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting WTLesson: ${e.message}", e)
                false
            }
        }
        
        override fun parseFromJson(json: String): WTLesson {
            return gson.fromJson(json, WTLesson::class.java)
        }
    }
    
    inner class RegistrationHandler : OperationHandler<WTRegistration> {
        override suspend fun handleInsertOrUpdate(item: WTRegistration): Boolean {
            return try {
                firebaseManager.saveRegistration(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error saving registration: ${e.message}", e)
                false
            }
        }
        
        override suspend fun handleDelete(item: WTRegistration): Boolean {
            return try {
                firebaseManager.deleteRegistration(item.id)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting registration: ${e.message}", e)
                false
            }
        }
        
        override fun parseFromJson(json: String): WTRegistration {
            return gson.fromJson(json, WTRegistration::class.java)
        }
    }
    
    inner class ProgramHandler : OperationHandler<Program> {
                override suspend fun handleInsertOrUpdate(item: Program): Boolean {
            return try {
                firebaseManager.saveProgram(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error saving program: ${e.message}", e)
                false
            }
        }

        override suspend fun handleDelete(item: Program): Boolean {
            return try {
                firebaseManager.deleteProgram(item.id)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting program: ${e.message}", e)
                false
            }
        }
        
        override fun parseFromJson(json: String): Program {
            return gson.fromJson(json, Program::class.java)
        }
    }
    
    inner class WorkoutHandler : OperationHandler<Workout> {
                override suspend fun handleInsertOrUpdate(item: Workout): Boolean {
            return try {
                firebaseManager.saveWorkout(item)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error saving workout: ${e.message}", e)
                false
            }
        }

        override suspend fun handleDelete(item: Workout): Boolean {
            return try {
                firebaseManager.deleteWorkout(item.id)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting workout: ${e.message}", e)
                false
            }
        }
        
        override fun parseFromJson(json: String): Workout {
            return gson.fromJson(json, Workout::class.java)
        }
    }
} 