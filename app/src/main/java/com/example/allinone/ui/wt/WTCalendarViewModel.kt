package com.example.allinone.ui.wt

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.WTLesson
import com.example.allinone.data.WTEvent
import com.example.allinone.data.WTStudent
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class WTCalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseRepository = FirebaseRepository(application)

    private val _selectedDate = MutableLiveData<Date>()
    val selectedDate: LiveData<Date> = _selectedDate

    private val _events = MutableLiveData<List<WTEvent>>()
    val events: LiveData<List<WTEvent>> = _events

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    // Network availability status from Firebase Repository
    val isNetworkAvailable: LiveData<Boolean> = firebaseRepository.isOnline

    private val _lessonSchedule = MutableLiveData<List<WTLesson>>(emptyList())
    val lessonSchedule: LiveData<List<WTLesson>> = _lessonSchedule
    
    private val _students = MutableLiveData<List<WTStudent>>(emptyList())
    val students: LiveData<List<WTStudent>> = _students

    companion object {
        private const val TAG = "WTCalendarViewModel"
    }

    init {
        Log.d(TAG, "ViewModel initialized")
        _isLoading.value = false
        
        // Start data collection
        viewModelScope.launch {
            try {
                firebaseRepository.wtEvents
                    .catch { e ->
                        Log.e(TAG, "Error collecting events: ${e.message}")
                        _errorMessage.postValue("Error loading events: ${e.message}")
                    }
                    .collect { eventsList ->
                        Log.d(TAG, "Collected ${eventsList.size} events")
                        _events.postValue(eventsList)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in events flow collection: ${e.message}")
                _errorMessage.postValue("Error in events flow: ${e.message}")
            }

            try {
                firebaseRepository.wtLessons
                    .catch { e -> 
                        Log.e(TAG, "Error collecting lessons: ${e.message}")
                        _errorMessage.postValue("Error loading lessons: ${e.message}")
                    }
                    .collect { lessonsList ->
                        Log.d(TAG, "Collected ${lessonsList.size} lessons")
                        
                        // Only generate events if we have no lesson events yet
                        val existingLessonEvents = _events.value?.filter { it.type == "Lesson" } ?: emptyList()
                        if (existingLessonEvents.isEmpty() && lessonsList.isNotEmpty()) {
                            Log.d(TAG, "No lesson events found, generating from ${lessonsList.size} lessons")
                            generateCalendarEvents()
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in lessons flow collection: ${e.message}")
                _errorMessage.postValue("Error in lessons flow: ${e.message}")
            }

            try {
                firebaseRepository.students
                    .catch { e -> 
                        Log.e(TAG, "Error collecting students: ${e.message}")
                        _errorMessage.postValue("Error loading students: ${e.message}")
                    }
                    .collect { studentsList ->
                        Log.d(TAG, "Collected ${studentsList.size} students")
                        // When students change, we might need to update events
                        // as they may contain student information
                        if (_events.value?.isNotEmpty() == true) {
                            generateCalendarEvents()
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in students flow collection: ${e.message}")
                _errorMessage.postValue("Error in students flow: ${e.message}")
            }
        }
    }
    
    fun addEvent(title: String, description: String?, date: Date) {
        viewModelScope.launch {
            val event = WTEvent(
                title = title,
                description = description,
                date = date,
                type = "Event"
            )
            
            firebaseRepository.insertWTEvent(event)
            
            // Immediately update the events list
            val currentEvents = _events.value?.toMutableList() ?: mutableListOf()
            currentEvents.add(event)
            _events.value = currentEvents
        }
    }
    
    fun deleteEvent(event: WTEvent) {
        viewModelScope.launch {
            firebaseRepository.deleteWTEvent(event)
            
            // Immediately update the events list
            val currentEvents = _events.value?.toMutableList() ?: mutableListOf()
            currentEvents.removeAll { it.id == event.id }
            _events.value = currentEvents
        }
    }
    
    fun setLessonSchedule(lessons: List<WTLesson>) {
        viewModelScope.launch {
            try {
                Log.d("WTCalendarViewModel", "Setting lesson schedule with ${lessons.size} lessons")
                
                // Log the days being set
                val daysText = lessons.map { 
                    when(it.dayOfWeek) {
                        Calendar.SUNDAY -> "Sunday"
                        Calendar.MONDAY -> "Monday" 
                        Calendar.TUESDAY -> "Tuesday"
                        Calendar.WEDNESDAY -> "Wednesday"
                        Calendar.THURSDAY -> "Thursday"
                        Calendar.FRIDAY -> "Friday"
                        Calendar.SATURDAY -> "Saturday"
                        else -> "Unknown"
                    }
                }.joinToString(", ")
                Log.d("WTCalendarViewModel", "Setting lessons for days: $daysText")
                
                // Delete existing lessons - keep a copy for comparison
                val existingLessons = _lessonSchedule.value ?: emptyList()
                
                // First update memory for immediate UI response
                _lessonSchedule.postValue(lessons)
                
                // Then perform the Firebase operations
                for (lesson in existingLessons) {
                    try {
                        firebaseRepository.deleteWTLesson(lesson)
                    } catch (e: Exception) {
                        Log.e("WTCalendarViewModel", "Error deleting lesson: ${e.message}")
                    }
                }
                
                // Add new lessons - with proper IDs
                for (lesson in lessons) {
                    try {
                        // Ensure each lesson has a unique ID
                        val lessonWithId = if (lesson.id == 0L) {
                            lesson.copy(id = UUID.randomUUID().mostSignificantBits)
                        } else {
                            lesson
                        }
                        firebaseRepository.insertWTLesson(lessonWithId)
                    } catch (e: Exception) {
                        Log.e("WTCalendarViewModel", "Error inserting lesson: ${e.message}")
                    }
                }
                
                // Generate calendar events after a short delay to ensure lessons are properly saved
                kotlinx.coroutines.delay(500)
                generateCalendarEvents()
                
                // Update student subscription end dates
                updateStudentSubscriptionEndDates()
                
            } catch (e: Exception) {
                Log.e("WTCalendarViewModel", "Error setting lesson schedule: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    fun removeLesson(lesson: WTLesson) {
        viewModelScope.launch {
            firebaseRepository.deleteWTLesson(lesson)
            
            // Update student subscription end dates
            updateStudentSubscriptionEndDates()
        }
    }
    
    fun generateCalendarEvents() {
        viewModelScope.launch {
            try {
                // Get lesson schedule
                val lessons = _lessonSchedule.value ?: return@launch
                if (lessons.isEmpty()) {
                    Log.w("WTCalendarViewModel", "No lessons found, cannot generate events")
                    return@launch
                }
                
                // Debug: Log lesson days that should be scheduled
                val lessonDaysText = lessons.map { 
                    when(it.dayOfWeek) {
                        Calendar.SUNDAY -> "Sunday"
                        Calendar.MONDAY -> "Monday" 
                        Calendar.TUESDAY -> "Tuesday"
                        Calendar.WEDNESDAY -> "Wednesday"
                        Calendar.THURSDAY -> "Thursday"
                        Calendar.FRIDAY -> "Friday"
                        Calendar.SATURDAY -> "Saturday"
                        else -> "Unknown"
                    }
                }.joinToString(", ")
                Log.d("WTCalendarViewModel", "Generating events for days: $lessonDaysText")
                
                // Generate events for the next 4 months (instead of 4 weeks)
                val today = Calendar.getInstance()
                today.set(Calendar.HOUR_OF_DAY, 0)
                today.set(Calendar.MINUTE, 0)
                today.set(Calendar.SECOND, 0)
                today.set(Calendar.MILLISECOND, 0)
                
                // Calculate end date (4 months from now)
                val endDate = Calendar.getInstance()
                endDate.time = today.time
                endDate.add(Calendar.MONTH, 4)
                
                // Get existing events
                val existingEvents = _events.value ?: emptyList()
                
                // Create a new event list with non-lesson events preserved
                val nonLessonEvents = existingEvents.filter { it.type != "Lesson" }
                val newEvents = mutableListOf<WTEvent>()
                newEvents.addAll(nonLessonEvents)
                
                // Track events to delete for a clean removal
                val eventsToDelete = mutableListOf<WTEvent>()
                eventsToDelete.addAll(existingEvents.filter { it.type == "Lesson" })
                
                // Generate new lesson events
                for (lesson in lessons) {
                    // Start from today
                    val currentDate = Calendar.getInstance()
                    currentDate.time = today.time
                    
                    // Make sure we're only looking at the date part
                    currentDate.set(Calendar.HOUR_OF_DAY, 0)
                    currentDate.set(Calendar.MINUTE, 0)
                    currentDate.set(Calendar.SECOND, 0)
                    currentDate.set(Calendar.MILLISECOND, 0)
                    
                    // Find the next occurrence of this day of week
                    while (currentDate.get(Calendar.DAY_OF_WEEK) != lesson.dayOfWeek) {
                        currentDate.add(Calendar.DAY_OF_MONTH, 1)
                    }
                    
                    val dayName = when(lesson.dayOfWeek) {
                        Calendar.SUNDAY -> "Sunday"
                        Calendar.MONDAY -> "Monday" 
                        Calendar.TUESDAY -> "Tuesday"
                        Calendar.WEDNESDAY -> "Wednesday"
                        Calendar.THURSDAY -> "Thursday"
                        Calendar.FRIDAY -> "Friday"
                        Calendar.SATURDAY -> "Saturday"
                        else -> "Unknown"
                    }
                    
                    Log.d("WTCalendarViewModel", "First occurrence of $dayName is ${currentDate.time}")
                    
                    var eventCount = 0
                    // Generate recurring events until end date
                    while (currentDate.before(endDate)) {
                        // Set the time for this event
                        val eventDate = Calendar.getInstance()
                        eventDate.time = currentDate.time
                        eventDate.set(Calendar.HOUR_OF_DAY, lesson.startHour)
                        eventDate.set(Calendar.MINUTE, lesson.startMinute)
                        
                        // Create the lesson event
                        val timeText = "${lesson.startHour}:${lesson.startMinute.toString().padStart(2, '0')} - " +
                                "${lesson.endHour}:${lesson.endMinute.toString().padStart(2, '0')}"
                        
                        // Generate a truly unique ID that won't collide
                        val uniqueId = UUID.randomUUID().mostSignificantBits
                        
                        val event = WTEvent(
                            id = uniqueId, // Using UUID for truly unique IDs
                            title = "Wing Tzun Lesson ($dayName)",
                            description = "Regular Wing Tzun lesson at $timeText",
                            date = eventDate.time,
                            type = "Lesson"
                        )
                        
                        newEvents.add(event)
                        eventCount++
                        
                        // Log the event being created
                        Log.d("WTCalendarViewModel", "Created lesson event for $dayName on ${eventDate.time}")
                        
                        // Move to next week
                        currentDate.add(Calendar.WEEK_OF_YEAR, 1)
                    }
                    
                    Log.d("WTCalendarViewModel", "Created $eventCount events for $dayName")
                }
                
                Log.d("WTCalendarViewModel", "Total new events: ${newEvents.size}")
                
                // First update the local events (for immediate UI update)
                _events.postValue(newEvents)
                
                // Then delete old lesson events from Firebase
                for (event in eventsToDelete) {
                    try {
                        firebaseRepository.deleteWTEvent(event)
                    } catch (e: Exception) {
                        Log.e("WTCalendarViewModel", "Error deleting event ${event.id}: ${e.message}")
                    }
                }
                
                // Then save all new lesson events to Firebase
                for (event in newEvents) {
                    if (event.type == "Lesson") {
                        try {
                            firebaseRepository.insertWTEvent(event)
                        } catch (e: Exception) {
                            Log.e("WTCalendarViewModel", "Error saving event ${event.id}: ${e.message}")
                        }
                    }
                }
                
                // Final update after Firebase operations
                _events.postValue(newEvents)
                
            } catch (e: Exception) {
                Log.e("WTCalendarViewModel", "Error generating calendar events: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Reload events from repository
     * Call this when returning to the fragment
     */
    fun reloadEvents() {
        viewModelScope.launch {
            firebaseRepository.refreshAllData()
        }
    }
    
    fun updateStudentSubscriptionEndDates() {
        viewModelScope.launch {
            val students = _students.value ?: emptyList()
            val lessons = _lessonSchedule.value ?: emptyList()
            
            // If no lessons are scheduled, we can't calculate end dates
            if (lessons.isEmpty()) return@launch
            
            for (student in students) {
                // Calculate lesson schedule for each student
                val startDate = Calendar.getInstance()
                startDate.time = student.startDate
                
                // Calculate end date after 8 lessons
                val endDate = calculateEndDateAfterLessons(startDate, 8, lessons)
                
                // Update student
                val updatedStudent = student.copy(endDate = endDate)
                firebaseRepository.updateWTStudent(updatedStudent)
            }
        }
    }
    
    fun calculateEndDateAfterLessons(
        startDate: Calendar,
        lessonCount: Int,
        lessons: List<WTLesson>
    ): Date {
        val calendar = Calendar.getInstance()
        calendar.time = startDate.time
        
        // Get lesson days sorted by day of week
        val lessonDays = lessons.map { it.dayOfWeek }.sorted()
        
        // If no lesson days, return start date + 8 weeks as fallback
        if (lessonDays.isEmpty()) {
            calendar.add(Calendar.WEEK_OF_YEAR, 8)
            return calendar.time
        }
        
        // Get future lesson events for better accuracy
        val futureEvents = (_events.value ?: emptyList())
            .filter { 
                it.type == "Lesson" && 
                it.date.time >= startDate.timeInMillis 
            }
            .sortedBy { it.date.time }
        
        // If we have future events, use them for more accurate calculation
        if (futureEvents.isNotEmpty()) {
            return calculateEndDateFromEvents(startDate.time, lessonCount, futureEvents)
        }
        
        // Otherwise fall back to day-of-week based calculation
        var countedLessons = 0
        while (countedLessons < lessonCount) {
            // Move to the next day
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            
            // Check if this day has a lesson
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            if (lessonDays.contains(dayOfWeek)) {
                countedLessons++
            }
        }
        
        // Return the date
        return calendar.time
    }
    
    private fun calculateEndDateFromEvents(
        startDate: Date,
        lessonCount: Int,
        events: List<WTEvent>
    ): Date {
        var count = 0
        var lastEventDate = startDate
        
        for (event in events) {
            if (event.date.time >= startDate.time) {
                count++
                lastEventDate = event.date
                
                if (count >= lessonCount) {
                    return lastEventDate
                }
            }
        }
        
        // If we don't have enough events, estimate the remaining lessons
        // Calculate average days between lessons from available events
        if (events.size >= 2) {
            val avgDaysBetweenLessons = calculateAverageDaysBetweenLessons(events)
            val remainingLessons = lessonCount - count
            
            val calendar = Calendar.getInstance()
            calendar.time = lastEventDate
            calendar.add(Calendar.DAY_OF_YEAR, (remainingLessons * avgDaysBetweenLessons).toInt())
            return calendar.time
        }
        
        // Fallback: add 7 days per remaining lesson
        val calendar = Calendar.getInstance()
        calendar.time = lastEventDate
        calendar.add(Calendar.DAY_OF_YEAR, (lessonCount - count) * 7)
        return calendar.time
    }
    
    private fun calculateAverageDaysBetweenLessons(events: List<WTEvent>): Float {
        if (events.size < 2) return 7f
        
        var totalDays = 0L
        for (i in 1 until events.size) {
            val diffMs = events[i].date.time - events[i-1].date.time
            totalDays += diffMs / (1000 * 60 * 60 * 24)
        }
        
        return totalDays.toFloat() / (events.size - 1)
    }
    
    fun postponeLesson(eventDate: Date, newDate: Date) {
        viewModelScope.launch {
            // Find the event
            val events = _events.value ?: emptyList()
            val event = events.find { it.date.time == eventDate.time && it.type == "Lesson" }
            
            if (event != null) {
                // Delete the old event
                firebaseRepository.deleteWTEvent(event)
                
                // Create a new event with the new date
                val newEvent = event.copy(
                    id = 0, // Generate new ID
                    date = newDate,
                    description = event.description + "\n(Rescheduled from ${formatDate(eventDate)})"
                )
                firebaseRepository.insertWTEvent(newEvent)
                
                // Update event list immediately
                val currentEvents = _events.value?.toMutableList() ?: mutableListOf()
                currentEvents.remove(event)
                currentEvents.add(newEvent)
                _events.value = currentEvents
                
                // Update student subscription end dates
                updateStudentSubscriptionEndDates()
            }
        }
    }
    
    private fun formatDate(date: Date): String {
        val format = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }
    
    fun cancelLesson(eventDate: Date) {
        viewModelScope.launch {
            // Find the event
            val events = _events.value ?: emptyList()
            val event = events.find { it.date.time == eventDate.time && it.type == "Lesson" }
            
            if (event != null) {
                // Delete the event
                firebaseRepository.deleteWTEvent(event)
                
                // Update event list immediately
                val currentEvents = _events.value?.toMutableList() ?: mutableListOf()
                currentEvents.remove(event)
                _events.value = currentEvents
                
                // Update student subscription end dates
                updateStudentSubscriptionEndDates()
            }
        }
    }
    
    /**
     * Force a refresh of data from Firebase
     */
    fun forceRefresh() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "Starting manual refresh of data")
                firebaseRepository.refreshAllData()
                
                // After data is refreshed, regenerate calendar events
                Log.d(TAG, "Manual refresh completed, regenerating events")
                generateCalendarEvents()
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Error during manual refresh: ${e.message}", e)
                _errorMessage.value = "Error refreshing data: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Clears the current error message
     */
    fun clearErrorMessage() {
        _errorMessage.value = ""
    }
} 