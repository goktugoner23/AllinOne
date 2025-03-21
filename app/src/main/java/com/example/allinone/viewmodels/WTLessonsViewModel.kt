package com.example.allinone.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.WTLesson
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

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
    
    // Add a new lesson
    fun addLesson(dayOfWeek: Int, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        viewModelScope.launch {
            try {
                val lesson = WTLesson(
                    id = UUID.randomUUID().mostSignificantBits,
                    dayOfWeek = dayOfWeek,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute
                )
                repository.insertWTLesson(lesson)
                // Notify observers that a lesson was added
                _lessonChangeEvent.value = LessonChangeEvent.LessonAdded(lesson)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add lesson: ${e.message}"
            }
        }
    }
    
    // Delete a lesson
    fun deleteLesson(lesson: WTLesson) {
        viewModelScope.launch {
            try {
                repository.deleteWTLesson(lesson)
                // Notify observers that a lesson was deleted
                _lessonChangeEvent.value = LessonChangeEvent.LessonDeleted(lesson)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete lesson: ${e.message}"
            }
        }
    }
    
    // Set current editing lesson
    fun setEditingLesson(lesson: WTLesson?) {
        _currentEditingLesson.value = lesson
    }
    
    // Update a lesson
    fun updateLesson(dayOfWeek: Int, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
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
                repository.insertWTLesson(updatedLesson)
                setEditingLesson(null) // Clear editing state
                
                // Notify observers that a lesson was modified
                _lessonChangeEvent.value = LessonChangeEvent.LessonModified(updatedLesson)
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
                // For simplicity, we're just ensuring the current lessons are persisted
                // In a real implementation, you might want to compare with existing lessons
                // and only add/update/delete as needed
                lessons.forEach { lesson ->
                    repository.insertWTLesson(lesson)
                }
                
                // Notify observers that lessons were updated
                _lessonChangeEvent.value = LessonChangeEvent.LessonsUpdated
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save lessons: ${e.message}"
            }
        }
    }
    
    // Clear error message
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    /**
     * Force refresh lessons from Firebase and ensure calendar is updated
     */
    fun refreshLessons() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.refreshWTLessons(true) // Force refresh from Firebase
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
    
    // Get day name from day of week
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
    
    // Format time as HH:MM
    fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }
} 