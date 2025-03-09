package com.example.allinone.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Event
import com.example.allinone.data.WTLesson
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import kotlin.math.ceil
import kotlin.math.floor

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    
    // LiveData for events
    private val _events = MutableLiveData<List<Event>>(emptyList())
    val events: LiveData<List<Event>> = _events
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error handling
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage
    
    // Lesson schedule from WT Registry
    private val _lessonSchedule = MutableLiveData<List<WTLesson>>(emptyList())
    
    // In-memory storage for events since we don't have a proper database implementation yet
    private val eventsList = mutableListOf<Event>()
    
    init {
        // Load events when the ViewModel is created
        loadEvents()
    }
    
    /**
     * Load events from local storage or database
     * This is a simple implementation that would be replaced with actual database calls
     */
    private fun loadEvents() {
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                // Simulate loading delay
                kotlinx.coroutines.delay(500)
                
                // In a real app, this would load from a database
                _events.value = eventsList.toList()
                
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load events: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Add a new event to the calendar
     */
    fun addEvent(title: String, description: String?, date: Date) {
        viewModelScope.launch {
            try {
                val newEvent = Event(
                    id = System.currentTimeMillis(), // Simple ID generation
                    title = title,
                    description = description,
                    date = date,
                    type = "Event"
                )
                
                // Add to our in-memory list
                eventsList.add(newEvent)
                
                // Update the LiveData
                _events.value = eventsList.toList()
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add event: ${e.message}"
            }
        }
    }
    
    /**
     * Delete an event from the calendar
     */
    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            try {
                // Remove from our in-memory list
                eventsList.removeIf { it.id == event.id }
                
                // Update the LiveData
                _events.value = eventsList.toList()
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete event: ${e.message}"
            }
        }
    }
    
    /**
     * Cancel a lesson scheduled on a specific date
     */
    fun cancelLesson(date: Date) {
        viewModelScope.launch {
            try {
                // Find and remove the lesson event at this date
                val calendar = Calendar.getInstance().apply { time = date }
                
                // Find event with matching date and "Lesson" type
                eventsList.removeIf { event ->
                    val eventCal = Calendar.getInstance().apply { time = event.date }
                    event.type == "Lesson" &&
                    eventCal.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                    eventCal.get(Calendar.MONTH) == calendar.get(Calendar.MONTH) &&
                    eventCal.get(Calendar.DAY_OF_MONTH) == calendar.get(Calendar.DAY_OF_MONTH) &&
                    eventCal.get(Calendar.HOUR_OF_DAY) == calendar.get(Calendar.HOUR_OF_DAY) &&
                    eventCal.get(Calendar.MINUTE) == calendar.get(Calendar.MINUTE)
                }
                
                // Update the LiveData
                _events.value = eventsList.toList()
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to cancel lesson: ${e.message}"
            }
        }
    }
    
    /**
     * Postpone a lesson from one date to another
     */
    fun postponeLesson(originalDate: Date, newDate: Date) {
        viewModelScope.launch {
            try {
                // Find the lesson event to postpone
                val origCal = Calendar.getInstance().apply { time = originalDate }
                
                // Find the lesson event to postpone
                val lessonToPostpone = eventsList.find { event ->
                    val eventCal = Calendar.getInstance().apply { time = event.date }
                    event.type == "Lesson" &&
                    eventCal.get(Calendar.YEAR) == origCal.get(Calendar.YEAR) &&
                    eventCal.get(Calendar.MONTH) == origCal.get(Calendar.MONTH) &&
                    eventCal.get(Calendar.DAY_OF_MONTH) == origCal.get(Calendar.DAY_OF_MONTH) &&
                    eventCal.get(Calendar.HOUR_OF_DAY) == origCal.get(Calendar.HOUR_OF_DAY) &&
                    eventCal.get(Calendar.MINUTE) == origCal.get(Calendar.MINUTE)
                }
                
                lessonToPostpone?.let { lesson ->
                    // Create a new event with the postponed date
                    val postponedEvent = lesson.copy(
                        id = System.currentTimeMillis(), // New ID for the postponed event
                        date = newDate,
                        title = "${lesson.title} (Postponed)",
                        description = "${lesson.description ?: ""}\nPostponed from ${origCal.time}".trim()
                    )
                    
                    // Remove the original event
                    eventsList.removeIf { it.id == lesson.id }
                    
                    // Add the new postponed event
                    eventsList.add(postponedEvent)
                    
                    // Update the LiveData
                    _events.value = eventsList.toList()
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "Failed to postpone lesson: ${e.message}"
            }
        }
    }
    
    /**
     * Set the lesson schedule and generate corresponding events
     */
    fun setLessonSchedule(lessons: List<WTLesson>) {
        _lessonSchedule.value = lessons
        generateLessonEvents()
    }
    
    /**
     * Generate calendar events from the lesson schedule
     */
    private fun generateLessonEvents() {
        val lessons = _lessonSchedule.value ?: return
        if (lessons.isEmpty()) return
        
        // Clear any existing lesson events
        eventsList.removeIf { it.type == "Lesson" }
        
        // Get the current date
        val currentDate = Calendar.getInstance()
        
        // Get a date far in the future (5 years ahead) to cover all practical calendar navigation
        val farFutureDate = Calendar.getInstance()
        farFutureDate.add(Calendar.YEAR, 5) // Add 5 years to current date
        
        // For each lesson, create events far into the future
        for (lesson in lessons) {
            // Create a calendar for this specific lesson starting from current date
            val lessonCalendar = Calendar.getInstance()
            lessonCalendar.time = currentDate.time
            
            // First reset to Monday (first day of our week)
            while (lessonCalendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                lessonCalendar.add(Calendar.DAY_OF_MONTH, -1)
            }
            
            // Then add the appropriate number of days to reach the correct day
            // Convert Calendar.DAY_OF_WEEK to our Monday-first system:
            // Monday=1, Tuesday=2, ..., Sunday=7
            val targetDay = when (lesson.dayOfWeek) {
                Calendar.SUNDAY -> 7  // Sunday is day 7 in our system
                else -> lesson.dayOfWeek - Calendar.MONDAY + 1  // Others are 1-based from Monday
            }
            
            // Add days (accounting for being already on Monday/day 1)
            lessonCalendar.add(Calendar.DAY_OF_MONTH, targetDay - 1)
            
            // Keep adding weekly lessons until we reach the far future date
            while (lessonCalendar.before(farFutureDate)) {
                // Set the lesson time
                val eventCalendar = Calendar.getInstance()
                eventCalendar.time = lessonCalendar.time
                eventCalendar.set(Calendar.HOUR_OF_DAY, lesson.startHour)
                eventCalendar.set(Calendar.MINUTE, lesson.startMinute)
                
                // Create a formatted time string
                val startTime = String.format("%02d:%02d", lesson.startHour, lesson.startMinute)
                val endTime = String.format("%02d:%02d", lesson.endHour, lesson.endMinute)
                
                // Create the event
                val event = Event(
                    id = System.currentTimeMillis() + eventsList.size, // Simple unique ID
                    title = "WT Lesson ($startTime-$endTime)",
                    description = "Regular weekly Wing Tzun lesson",
                    date = eventCalendar.time,
                    type = "Lesson"
                )
                
                // Add to our list
                eventsList.add(event)
                
                // Move to next week
                lessonCalendar.add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        
        // Update the LiveData
        _events.value = eventsList.toList()
    }
    
    /**
     * Clear error message
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    /**
     * Force refresh events
     */
    fun forceRefresh() {
        _isLoading.value = true
        viewModelScope.launch {
            // Generate lesson events based on current lesson schedule
            generateLessonEvents()
            
            // Update UI state
            _isLoading.value = false
            
            // Notify observers that data has changed
            _events.value = eventsList.toList()
        }
    }
    
    /**
     * Calculates the end date after a number of lessons
     * Used by WTRegisterViewModel to determine when a student's training period ends
     */
    fun calculateEndDateAfterLessons(
        startDate: Calendar,
        lessonCount: Int,
        lessons: List<WTLesson>
    ): Date {
        // If no lessons defined or lesson count is 0, default to 8 weeks
        if (lessons.isEmpty() || lessonCount <= 0) {
            val calendar = Calendar.getInstance()
            calendar.time = startDate.time
            calendar.add(Calendar.WEEK_OF_YEAR, 8)
            return calendar.time
        }
        
        // Count how many lessons occur each week
        val lessonsPerWeek = lessons.size
        
        // Calculate how many weeks needed
        val weeksNeeded = if (lessonsPerWeek > 0) {
            Math.ceil(lessonCount.toDouble() / lessonsPerWeek).toInt()
        } else {
            8 // Default to 8 weeks if no lessons per week
        }
        
        // Calculate end date
        val calendar = Calendar.getInstance()
        calendar.time = startDate.time
        calendar.add(Calendar.WEEK_OF_YEAR, weeksNeeded)
        
        return calendar.time
    }
} 