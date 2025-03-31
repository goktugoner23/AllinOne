package com.example.allinone.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.WTLesson
import com.example.allinone.firebase.FirebaseRepository
import com.example.allinone.firebase.FirebaseIdManager
import com.example.allinone.firebase.DataChangeNotifier
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import java.util.Date

/**
 * Change event for lessons to notify other components
 */
sealed class LessonChangeEvent {
    object LessonsUpdated : LessonChangeEvent()
    data class LessonDeleted(val lesson: WTLesson) : LessonChangeEvent()
    data class LessonAdded(val lesson: WTLesson) : LessonChangeEvent()
    data class LessonModified(val lesson: WTLesson) : LessonChangeEvent()
}

class WTLessonsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application)
    private val idManager = FirebaseIdManager()
    
    private val _lessons = MutableLiveData<List<WTLesson>>(emptyList())
    val lessons: LiveData<List<WTLesson>> = _lessons
    
    // Network availability
    val isNetworkAvailable = repository.isNetworkAvailable
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error message
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage
    
    // Currently editing lesson
    private val _currentEditingLesson = MutableLiveData<WTLesson?>(null)
    val currentEditingLesson: LiveData<WTLesson?> = _currentEditingLesson
    
    // Lesson change events
    private val _lessonChangeEvent = MutableLiveData<LessonChangeEvent>()
    val lessonChangeEvent: LiveData<LessonChangeEvent> = _lessonChangeEvent
    
    init {
        // Collect lesson data from repository
        viewModelScope.launch {
            repository.wtLessons.collect { lessonsList ->
                _lessons.value = lessonsList
                // Notify observers that lessons were updated
                _lessonChangeEvent.value = LessonChangeEvent.LessonsUpdated
            }
        }
    }
    
    /**
     * Add a new lesson with specified parameters 
     * This method internally generates a sequential ID and notifies observers
     */
    fun addLesson(
        dayOfWeek: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ) {
        viewModelScope.launch {
            try {
                // Get next sequential ID for lessons
                val lessonId = idManager.getNextId("wtLessons")
                
                val lesson = WTLesson(
                    id = lessonId,
                    dayOfWeek = dayOfWeek,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute
                )
                
                repository.insertWTLesson(lesson)
                
                // Notify observers through both mechanisms
                _lessonChangeEvent.value = LessonChangeEvent.LessonAdded(lesson)
                DataChangeNotifier.notifyLessonsChanged()
                
                // Refresh lessons to ensure UI consistency
                repository.refreshWTLessons()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add lesson: ${e.message}"
            }
        }
    }
    
    /**
     * Delete a lesson
     */
    fun deleteLesson(lesson: WTLesson) {
        viewModelScope.launch {
            try {
                repository.deleteWTLesson(lesson)
                
                // Notify observers through both mechanisms
                _lessonChangeEvent.value = LessonChangeEvent.LessonDeleted(lesson)
                DataChangeNotifier.notifyLessonsChanged()
                
                // Refresh lessons to ensure UI consistency
                repository.refreshWTLessons()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete lesson: ${e.message}"
            }
        }
    }
    
    /**
     * Delete a lesson by finding it with matching parameters
     */
    fun deleteLessonByParams(dayOfWeek: Int, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        viewModelScope.launch {
            try {
                // Find lesson with matching details
                val lessonToDelete = _lessons.value?.find {
                    it.dayOfWeek == dayOfWeek &&
                    it.startHour == startHour && 
                    it.startMinute == startMinute &&
                    it.endHour == endHour &&
                    it.endMinute == endMinute
                }
                
                if (lessonToDelete != null) {
                    deleteLesson(lessonToDelete)
                } else {
                    _errorMessage.value = "Failed to find lesson to delete"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete lesson: ${e.message}"
            }
        }
    }
    
    /**
     * Set current editing lesson
     */
    fun setEditingLesson(lesson: WTLesson?) {
        _currentEditingLesson.value = lesson
    }
    
    /**
     * Update the currently editing lesson with new values
     */
    fun updateCurrentLesson(dayOfWeek: Int, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        val currentLesson = _currentEditingLesson.value ?: return
        
        viewModelScope.launch {
            try {
                val updatedLesson = currentLesson.copy(
                    dayOfWeek = dayOfWeek,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute
                )
                updateLesson(updatedLesson)
                setEditingLesson(null) // Clear editing state
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update lesson: ${e.message}"
            }
        }
    }
    
    /**
     * Update a specific lesson
     */
    fun updateLesson(lesson: WTLesson) {
        viewModelScope.launch {
            try {
                // Update lesson in Firebase
                repository.insertWTLesson(lesson)
                
                // Notify observers through both mechanisms
                _lessonChangeEvent.value = LessonChangeEvent.LessonModified(lesson)
                DataChangeNotifier.notifyLessonsChanged()
                
                // Refresh lessons to ensure UI consistency
                repository.refreshWTLessons()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update lesson: ${e.message}"
            }
        }
    }
    
    /**
     * Save a list of lessons
     * This method is used to save lessons when they are added/removed in bulk
     */
    fun saveLessons(lessons: List<WTLesson>) {
        viewModelScope.launch {
            try {
                // Update lessons in repository
                lessons.forEach { lesson ->
                    repository.insertWTLesson(lesson)
                }
                
                // Notify observers that lessons were updated
                _lessonChangeEvent.value = LessonChangeEvent.LessonsUpdated
                DataChangeNotifier.notifyLessonsChanged()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save lessons: ${e.message}"
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    /**
     * Force refresh lessons from Firebase and ensure calendar is updated
     */
    fun refreshData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.refreshWTLessons()
                _isLoading.value = false
                
                // Explicitly notify observers that lessons were updated
                // This will trigger the calendar update in MainActivity
                _lessonChangeEvent.value = LessonChangeEvent.LessonsUpdated
            } catch (e: Exception) {
                _errorMessage.value = "Failed to refresh lessons: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Get day name from day of week
     */
    fun getDayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            Calendar.SUNDAY -> "Sunday"
            else -> "Unknown"
        }
    }
    
    /**
     * Format time as HH:MM
     */
    fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }
} 