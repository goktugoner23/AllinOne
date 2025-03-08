package com.example.allinone.ui.wt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.allinone.data.WTEvent
import com.example.allinone.data.WTLesson
import java.util.Calendar
import java.util.Date

class WTCalendarViewModel(application: Application) : AndroidViewModel(application) {
    
    // Empty implementations to prevent crashes
    
    private val _events = MutableLiveData<List<WTEvent>>(emptyList())
    val events: LiveData<List<WTEvent>> = _events
    
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _isNetworkAvailable = MutableLiveData<Boolean>(true)
    val isNetworkAvailable: LiveData<Boolean> = _isNetworkAvailable
    
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _lessonSchedule = MutableLiveData<List<WTLesson>>(emptyList())
    val lessonSchedule: LiveData<List<WTLesson>> = _lessonSchedule
    
    // Stub methods that don't do anything
    fun forceRefresh() {}
    fun clearErrorMessage() {}
    fun setLessonSchedule(lessons: List<WTLesson>) {}
    fun generateCalendarEvents() {}
    fun addEvent(title: String, description: String?, date: Date) {}
    fun deleteEvent(event: WTEvent) {}
    fun cancelLesson(date: Date) {}
    fun postponeLesson(originalDate: Date, newDate: Date) {}
    fun removeLesson(lesson: WTLesson) {}
    
    // Add method back for WTRegisterViewModel to use
    fun calculateEndDateAfterLessons(
        startDate: Calendar,
        lessonCount: Int,
        lessons: List<WTLesson>
    ): Date {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.WEEK_OF_YEAR, 8)  // Default to 8 weeks
        return calendar.time
    }
} 