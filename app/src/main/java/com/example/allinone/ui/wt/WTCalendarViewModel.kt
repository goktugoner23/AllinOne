package com.example.allinone.ui.wt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import com.example.allinone.firebase.FirebaseRepository
import com.example.allinone.data.WTEvent
import com.example.allinone.data.WTStudent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class WTCalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application)
    
    // Convert StateFlow to LiveData
    val allStudents: LiveData<List<WTStudent>> = repository.students.asLiveData()
    
    private val _selectedDate = MutableLiveData<Date>().apply {
        value = Calendar.getInstance().time
    }
    
    val eventsForSelectedDate: LiveData<List<WTEvent>> = allStudents.map { students ->
        val events = mutableListOf<WTEvent>()
        val selectedDate = _selectedDate.value ?: return@map emptyList()
        
        // Create calendar instances for comparison
        val selectedCalendar = Calendar.getInstance().apply {
            time = selectedDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        for (student in students) {
            // Check if student's end date matches selected date
            val endDateCalendar = Calendar.getInstance().apply {
                time = student.endDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            if (selectedCalendar.get(Calendar.YEAR) == endDateCalendar.get(Calendar.YEAR) &&
                selectedCalendar.get(Calendar.MONTH) == endDateCalendar.get(Calendar.MONTH) &&
                selectedCalendar.get(Calendar.DAY_OF_MONTH) == endDateCalendar.get(Calendar.DAY_OF_MONTH)) {
                events.add(WTEvent(
                    id = student.id,
                    title = "Membership Expiration: ${student.name}",
                    description = "Wing Tzun membership expires today",
                    date = student.endDate,
                    type = "expiration"
                ))
            }
            
            // Check if student's start date matches selected date
            val startDateCalendar = Calendar.getInstance().apply {
                time = student.startDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            if (selectedCalendar.get(Calendar.YEAR) == startDateCalendar.get(Calendar.YEAR) &&
                selectedCalendar.get(Calendar.MONTH) == startDateCalendar.get(Calendar.MONTH) &&
                selectedCalendar.get(Calendar.DAY_OF_MONTH) == startDateCalendar.get(Calendar.DAY_OF_MONTH)) {
                events.add(WTEvent(
                    id = student.id,
                    title = "Membership Start: ${student.name}",
                    description = "Wing Tzun membership started today",
                    date = student.startDate,
                    type = "start"
                ))
            }
        }
        
        events.sortedBy { it.date }
    }
    
    fun setSelectedDate(date: Date) {
        _selectedDate.value = date
    }
} 