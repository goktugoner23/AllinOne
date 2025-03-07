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
        _isLoading.value = true
        
        // Set current date as selected date
        _selectedDate.value = Calendar.getInstance().time
        
        // Observe repository data
        observeRepositoryData()
        
        // Load data
        loadAllData()
    }
    
    private fun observeRepositoryData() {
        // Collect WTEvents from repository
        viewModelScope.launch {
            firebaseRepository.wtEvents.collect { newEvents ->
                Log.d(TAG, "Received ${newEvents.size} events from repository")
                _events.value = newEvents
                _isLoading.value = false
            }
        }
        
        // Collect WTLessons from repository
        viewModelScope.launch {
            firebaseRepository.wtLessons.collect { newLessons ->
                Log.d(TAG, "Received ${newLessons.size} lessons from repository")
                _lessonSchedule.value = newLessons
            }
        }
        
        // Collect students from repository
        viewModelScope.launch {
            firebaseRepository.students.collect { newStudents ->
                Log.d(TAG, "Received ${newStudents.size} students from repository")
                _students.value = newStudents
            }
        }
        
        // Observe loading state from repository
        firebaseRepository.isLoading.observeForever { isLoading ->
            _isLoading.value = isLoading
        }
        
        // Observe error messages from repository
        firebaseRepository.errorMessage.observeForever { errorMsg ->
            if (!errorMsg.isNullOrEmpty()) {
                _errorMessage.value = errorMsg
            }
        }
    }
    
    /**
     * Load all required data for the calendar
     */
    private fun loadAllData() {
        viewModelScope.launch {
            try {
                // First check if we have data from cache
                if (_events.value.isNullOrEmpty()) {
                    // Set loading state
                    _isLoading.value = true
                    
                    // Load data from repository (which will check cache first)
                    firebaseRepository.refreshWTEvents()
                    firebaseRepository.refreshWTLessons()
                    firebaseRepository.refreshStudents()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading data: ${e.message}", e)
                _errorMessage.value = "Error loading calendar data: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Force refresh data from network
     */
    fun forceRefresh() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                firebaseRepository.refreshWTEvents()
                firebaseRepository.refreshWTLessons()
                firebaseRepository.refreshStudents()
            } catch (e: Exception) {
                Log.e(TAG, "Error during force refresh: ${e.message}", e)
                _errorMessage.value = "Error refreshing data: ${e.message}"
            } finally {
                _isLoading.value = false
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
                Log.d(TAG, "Setting lesson schedule with ${lessons.size} lessons: ${
                    lessons.joinToString(", ") { 
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
                    }
                }")
                
                // Create a map with unique IDs for each lesson day
                val lessonsWithIds = lessons.map { lesson ->
                    // Create a stable ID based on day of week to ensure uniqueness
                    // This helps prevent duplication when re-saving the same days
                    val id = when (lesson.id) {
                        0L -> 100000000L + lesson.dayOfWeek // Unique ID pattern based on day
                        else -> lesson.id
                    }
                    
                    lesson.copy(id = id)
                }
                
                // Update memory immediately for UI responsiveness
                _lessonSchedule.value = lessonsWithIds
                
                // Delete any existing lessons first to prevent duplicates
                val existingLessons = firebaseRepository.wtLessons.value
                if (existingLessons.isNotEmpty()) {
                    Log.d(TAG, "Deleting ${existingLessons.size} existing lessons")
                    for (lesson in existingLessons) {
                        Log.d(TAG, "Deleting existing lesson for day ${lesson.dayOfWeek}")
                        firebaseRepository.deleteWTLesson(lesson)
                        // Add a small delay between deletions
                        delay(300)
                    }
                }
                
                // Create a delay to let deletions complete
                delay(1000)
                
                // Save each lesson with its unique ID
                for (lesson in lessonsWithIds) {
                    Log.d(TAG, "Saving lesson for day ${lesson.dayOfWeek} with ID ${lesson.id}")
                    firebaseRepository.insertWTLesson(lesson)
                    // Add a small delay between insertions
                    delay(300)
                }
                
                // Ensure changes are reflected in repository
                delay(1000)
                firebaseRepository.refreshWTLessons()
                
                // Wait for lessons to be saved before generating events
                delay(1500)
                
                // Generate events based on the lesson schedule
                generateCalendarEvents(true)
                
                // Update student subscription end dates
                updateStudentSubscriptionEndDates()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error setting lesson schedule: ${e.message}", e)
                _errorMessage.postValue("Error saving lesson schedule: ${e.message}")
            }
        }
    }
    
    fun removeLesson(lesson: WTLesson) {
        viewModelScope.launch {
            try {
                // Delete the lesson
                firebaseRepository.deleteWTLesson(lesson)
                
                // Update lesson schedule in memory
                val currentLessons = _lessonSchedule.value?.toMutableList() ?: mutableListOf()
                currentLessons.removeIf { it.dayOfWeek == lesson.dayOfWeek }
                _lessonSchedule.value = currentLessons
                
                // Refresh lessons from repository
                firebaseRepository.refreshWTLessons()
                
                // Wait for changes to propagate
                delay(500)
                
                // Update events to reflect lesson removal
                generateCalendarEvents(true)
                
                // Update student subscription end dates
                updateStudentSubscriptionEndDates()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error removing lesson: ${e.message}", e)
                _errorMessage.postValue("Error removing lesson: ${e.message}")
            }
        }
    }
    
    fun generateCalendarEvents(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                // Get lesson schedule
                val lessons = _lessonSchedule.value ?: emptyList()
                if (lessons.isEmpty()) {
                    Log.w(TAG, "No lessons found, cannot generate events")
                    return@launch
                }
                
                Log.d(TAG, "Generating calendar events for ${lessons.size} lesson days")
                
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
                
                // Get current events from repository
                val currentEvents = if (forceRefresh) {
                    // Force refresh from network
                    firebaseRepository.refreshWTEvents()
                    delay(500) // Give time for refresh to complete
                    firebaseRepository.wtEvents.value
                } else {
                    // Use current value
                    _events.value ?: emptyList()
                }
                
                // First delete all existing lesson events
                val eventsToDelete = currentEvents.filter { it.type == "Lesson" }
                for (event in eventsToDelete) {
                    Log.d(TAG, "Deleting event: ${event.id}")
                    firebaseRepository.deleteWTEvent(event)
                }
                
                // Wait for deletions to complete
                delay(1000)
                
                // Keep non-lesson events
                val nonLessonEvents = currentEvents.filter { it.type != "Lesson" }
                
                // Create new events list with existing non-lesson events
                val newEvents = mutableListOf<WTEvent>()
                newEvents.addAll(nonLessonEvents)
                
                // Generate new lesson events for each lesson day
                for (lesson in lessons) {
                    // Start from today
                    val currentDate = Calendar.getInstance()
                    currentDate.time = today.time
                    
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
                    
                    Log.d(TAG, "Generating events for $dayName")
                    
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
                        
                        // Generate an ID that's consistent for this day and date
                        // This helps prevent duplicates if we regenerate events
                        val uniqueId = generateStableEventId(lesson.dayOfWeek, eventDate.timeInMillis)
                        
                        val event = WTEvent(
                            id = uniqueId,
                            title = "Wing Tzun Lesson ($dayName)",
                            description = "Regular Wing Tzun lesson at $timeText",
                            date = eventDate.time,
                            type = "Lesson"
                        )
                        
                        // Add to our local list
                        newEvents.add(event)
                        
                        // Save directly to Firebase
                        try {
                            firebaseRepository.insertWTEvent(event)
                            Log.d(TAG, "Created event for $dayName on ${eventDate.time}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving event: ${e.message}")
                        }
                        
                        // Move to next week
                        currentDate.add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }
                
                // Update the local list
                _events.postValue(newEvents)
                
                // Force a refresh from Firebase to ensure everything is in sync
                delay(1000)
                firebaseRepository.refreshWTEvents()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error generating calendar events: ${e.message}", e)
                _errorMessage.postValue("Error generating calendar events: ${e.message}")
            }
        }
    }
    
    /**
     * Generate a stable ID for events based on day of week and date
     * This ensures we get the same ID if we regenerate the same event
     */
    private fun generateStableEventId(dayOfWeek: Int, timestamp: Long): Long {
        // Use day of week and rough date (to day precision) to create a stable ID
        val roughDate = timestamp / (24 * 60 * 60 * 1000) // Convert to days
        return (roughDate * 10 + dayOfWeek) // Unique but stable ID
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
     * Clears the current error message
     */
    fun clearErrorMessage() {
        _errorMessage.value = ""
    }
} 