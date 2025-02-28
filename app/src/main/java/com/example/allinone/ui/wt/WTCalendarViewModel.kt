package com.example.allinone.ui.wt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.WTLesson
import com.example.allinone.data.WTEvent
import com.example.allinone.data.WTStudent
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class WTCalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application)
    
    private val _events = MutableLiveData<List<WTEvent>>(emptyList())
    val events: LiveData<List<WTEvent>> = _events
    
    private val _lessonSchedule = MutableLiveData<List<WTLesson>>(emptyList())
    val lessonSchedule: LiveData<List<WTLesson>> = _lessonSchedule
    
    private val _students = MutableLiveData<List<WTStudent>>(emptyList())
    val students: LiveData<List<WTStudent>> = _students
    
    init {
        viewModelScope.launch {
            repository.wtEvents.collect { newEvents ->
                _events.value = newEvents
            }
        }
        
        viewModelScope.launch {
            repository.wtLessons.collect { newLessons ->
                _lessonSchedule.value = newLessons
            }
        }
        
        viewModelScope.launch {
            repository.students.collect { newStudents ->
                _students.value = newStudents
            }
        }
    }
    
    fun addEvent(title: String, description: String?, date: Date) {
        viewModelScope.launch {
            repository.insertWTEvent(
                title = title,
                description = description,
                date = date
            )
        }
    }
    
    fun deleteEvent(event: WTEvent) {
        viewModelScope.launch {
            repository.deleteWTEvent(event)
        }
    }
    
    fun setLessonSchedule(lessons: List<WTLesson>) {
        viewModelScope.launch {
            // Delete existing lessons
            val existingLessons = _lessonSchedule.value ?: emptyList()
            for (lesson in existingLessons) {
                repository.deleteWTLesson(lesson)
            }
            
            // Add new lessons
            for (lesson in lessons) {
                repository.insertWTLesson(lesson)
            }
            
            // Update student subscription end dates
            updateStudentSubscriptionEndDates()
        }
    }
    
    fun removeLesson(lesson: WTLesson) {
        viewModelScope.launch {
            repository.deleteWTLesson(lesson)
            
            // Update student subscription end dates
            updateStudentSubscriptionEndDates()
        }
    }
    
    fun generateCalendarEvents() {
        viewModelScope.launch {
            // Get lesson schedule
            val lessons = _lessonSchedule.value ?: return@launch
            
            // Generate events for the next 4 weeks
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)
            
            // Delete existing lesson-type events
            val existingEvents = _events.value ?: emptyList()
            for (event in existingEvents) {
                if (event.type == "Lesson") {
                    repository.deleteWTEvent(event)
                }
            }
            
            // Generate new events
            for (i in 0 until 4) {  // For 4 weeks
                for (lesson in lessons) {
                    // Find next occurrence of this weekday
                    val eventDate = Calendar.getInstance()
                    eventDate.time = today.time
                    
                    // Advance to target weekday
                    while (eventDate.get(Calendar.DAY_OF_WEEK) != lesson.dayOfWeek) {
                        eventDate.add(Calendar.DAY_OF_MONTH, 1)
                    }
                    
                    // Add week offset
                    eventDate.add(Calendar.WEEK_OF_YEAR, i)
                    
                    // Set time
                    eventDate.set(Calendar.HOUR_OF_DAY, lesson.startHour)
                    eventDate.set(Calendar.MINUTE, lesson.startMinute)
                    
                    // Create event
                    val timeText = "${lesson.startHour}:${lesson.startMinute.toString().padStart(2, '0')} - " +
                            "${lesson.endHour}:${lesson.endMinute.toString().padStart(2, '0')}"
                    
                    val event = WTEvent(
                        title = "Wing Tzun Lesson",
                        description = "Regular Wing Tzun lesson at $timeText",
                        date = eventDate.time,
                        type = "Lesson"
                    )
                    
                    repository.insertWTEvent(event)
                }
            }
        }
    }
    
    // Called when a student is added or when the lesson schedule changes
    fun updateStudentSubscriptionEndDates() {
        viewModelScope.launch {
            val students = _students.value ?: emptyList()
            val lessons = _lessonSchedule.value ?: emptyList()
            
            // If no lessons are scheduled, we can't calculate end dates
            if (lessons.isEmpty()) return@launch
            
            for (student in students) {
                // Only update students that have a startDate
                if (student.startDate != null) {
                    val startDate = Calendar.getInstance()
                    startDate.time = student.startDate
                    
                    // Calculate end date after 8 lessons
                    val endDate = calculateEndDateAfterLessons(startDate, 8, lessons)
                    
                    // Update student
                    val updatedStudent = student.copy(endDate = endDate)
                    repository.updateWTStudent(updatedStudent)
                }
            }
        }
    }
    
    private fun calculateEndDateAfterLessons(
        startDate: Calendar,
        lessonCount: Int,
        lessons: List<WTLesson>
    ): Date {
        val calendar = Calendar.getInstance()
        calendar.time = startDate.time
        
        // Get lesson days sorted by day of week
        val lessonDays = lessons.map { it.dayOfWeek }.sorted()
        
        // Count up to the required number of lessons
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
    
    fun postponeLesson(eventDate: Date, newDate: Date) {
        viewModelScope.launch {
            // Find the event
            val events = _events.value ?: emptyList()
            val event = events.find { it.date.time == eventDate.time && it.type == "Lesson" }
            
            if (event != null) {
                // Delete the old event
                repository.deleteWTEvent(event)
                
                // Create a new event with the new date
                val newEvent = event.copy(
                    id = 0, // Generate new ID
                    date = newDate
                )
                repository.insertWTEvent(newEvent)
                
                // Update student subscription end dates
                updateStudentSubscriptionEndDates()
            }
        }
    }
    
    fun cancelLesson(eventDate: Date) {
        viewModelScope.launch {
            // Find the event
            val events = _events.value ?: emptyList()
            val event = events.find { it.date.time == eventDate.time && it.type == "Lesson" }
            
            if (event != null) {
                // Delete the event
                repository.deleteWTEvent(event)
                
                // Update student subscription end dates
                updateStudentSubscriptionEndDates()
            }
        }
    }
} 