package com.example.allinone.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Schedule
import com.example.allinone.data.Event
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = FirebaseRepository(application)
    private val TAG = "ScheduleViewModel"
    
    // LiveData for schedules
    private val _schedules = MutableLiveData<List<Schedule>>(emptyList())
    val schedules: LiveData<List<Schedule>> = _schedules
    
    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error handling
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage
    
    // Filter states
    private val _selectedCategory = MutableLiveData<String>("All")
    val selectedCategory: LiveData<String> = _selectedCategory
    
    private val _showEnabledOnly = MutableLiveData<Boolean>(false)
    val showEnabledOnly: LiveData<Boolean> = _showEnabledOnly
    
    // In-memory storage for schedules
    private val schedulesList = mutableListOf<Schedule>()
    
    // Available categories
    val categories = listOf("All", "Personal", "Work", "Study", "Exercise", "Other")
    
    // Available colors for schedules
    val availableColors = listOf(
        "#4CAF50", // Green
        "#2196F3", // Blue
        "#FF9800", // Orange
        "#9C27B0", // Purple
        "#F44336", // Red
        "#607D8B", // Blue Grey
        "#795548", // Brown
        "#009688", // Teal
        "#3F51B5", // Indigo
        "#FFEB3B"  // Yellow
    )
    
    init {
        // Load schedules on initialization
        loadSchedules()
    }
    
    /**
     * Load all schedules from Firebase
     */
    fun loadSchedules() {
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading schedules from Firebase")
                
                // For now, we'll use a simple local storage since the app uses Firebase
                // In a real implementation, you'd add schedule methods to FirebaseRepository
                
                // Simulate loading from Firebase (replace with actual Firebase calls)
                loadSchedulesFromRepository()
                
                _isLoading.value = false
                Log.d(TAG, "Schedules loaded successfully, total: ${schedulesList.size}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load schedules: ${e.message}", e)
                _errorMessage.value = "Failed to load schedules: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Add a new schedule
     */
    fun addSchedule(
        title: String,
        description: String?,
        hour: Int,
        minute: Int,
        daysOfWeek: String,
        category: String,
        color: String
    ) {
        viewModelScope.launch {
            try {
                val newSchedule = Schedule(
                    id = System.currentTimeMillis(),
                    title = title,
                    description = description,
                    hour = hour,
                    minute = minute,
                    isEnabled = true,
                    daysOfWeek = daysOfWeek,
                    category = category,
                    color = color,
                    createdAt = Date()
                )
                
                // Add to local list
                schedulesList.add(newSchedule)
                
                // Update filtered list
                updateFilteredSchedules()
                
                // TODO: Save to Firebase repository
                // repository.insertSchedule(newSchedule)
                
                Log.d(TAG, "Schedule added: $title at ${newSchedule.getFormattedTime()}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add schedule: ${e.message}", e)
                _errorMessage.value = "Failed to add schedule: ${e.message}"
            }
        }
    }
    
    /**
     * Update an existing schedule
     */
    fun updateSchedule(schedule: Schedule) {
        viewModelScope.launch {
            try {
                // Find and update in local list
                val index = schedulesList.indexOfFirst { it.id == schedule.id }
                if (index != -1) {
                    schedulesList[index] = schedule
                    
                    // Update filtered list
                    updateFilteredSchedules()
                    
                    // TODO: Update in Firebase repository
                    // repository.updateSchedule(schedule)
                    
                    Log.d(TAG, "Schedule updated: ${schedule.title}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update schedule: ${e.message}", e)
                _errorMessage.value = "Failed to update schedule: ${e.message}"
            }
        }
    }
    
    /**
     * Delete a schedule
     */
    fun deleteSchedule(schedule: Schedule) {
        viewModelScope.launch {
            try {
                // Remove from local list
                schedulesList.removeIf { it.id == schedule.id }
                
                // Update filtered list
                updateFilteredSchedules()
                
                // TODO: Delete from Firebase repository
                // repository.deleteSchedule(schedule)
                
                Log.d(TAG, "Schedule deleted: ${schedule.title}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete schedule: ${e.message}", e)
                _errorMessage.value = "Failed to delete schedule: ${e.message}"
            }
        }
    }
    
    /**
     * Toggle schedule enabled/disabled status
     */
    fun toggleScheduleEnabled(schedule: Schedule) {
        val updatedSchedule = schedule.copy(isEnabled = !schedule.isEnabled)
        updateSchedule(updatedSchedule)
    }
    
    /**
     * Set category filter
     */
    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
        updateFilteredSchedules()
    }
    
    /**
     * Toggle show enabled only filter
     */
    fun toggleShowEnabledOnly() {
        _showEnabledOnly.value = !(_showEnabledOnly.value ?: false)
        updateFilteredSchedules()
    }
    
    /**
     * Update the filtered schedules list based on current filters
     */
    private fun updateFilteredSchedules() {
        val category = _selectedCategory.value ?: "All"
        val showEnabledOnly = _showEnabledOnly.value ?: false
        
        val filteredList = schedulesList.filter { schedule ->
            val categoryMatch = category == "All" || schedule.category == category
            val enabledMatch = !showEnabledOnly || schedule.isEnabled
            
            categoryMatch && enabledMatch
        }.sortedWith(compareBy({ it.hour }, { it.minute }))
        
        _schedules.value = filteredList
    }
    
    /**
     * Generate calendar events from schedules for a specific date range
     */
    fun generateEventsFromSchedules(startDate: Date, endDate: Date): List<Event> {
        val events = mutableListOf<Event>()
        val enabledSchedules = schedulesList.filter { it.isEnabled }
        
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        
        while (calendar.time.before(endDate) || calendar.time == endDate) {
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            // Convert to our system (1=Monday, 7=Sunday)
            val ourDayOfWeek = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1
            
            enabledSchedules.forEach { schedule ->
                if (schedule.getEnabledDays().contains(ourDayOfWeek)) {
                    val eventCalendar = Calendar.getInstance()
                    eventCalendar.time = calendar.time
                    eventCalendar.set(Calendar.HOUR_OF_DAY, schedule.hour)
                    eventCalendar.set(Calendar.MINUTE, schedule.minute)
                    eventCalendar.set(Calendar.SECOND, 0)
                    eventCalendar.set(Calendar.MILLISECOND, 0)
                    
                    val event = Event(
                        id = schedule.id * 1000 + calendar.timeInMillis / 1000, // Unique ID
                        title = schedule.title,
                        description = schedule.description,
                        date = eventCalendar.time,
                        type = "Schedule"
                    )
                    
                    events.add(event)
                }
            }
            
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        return events.sortedBy { it.date }
    }
    
    /**
     * Get schedules for today
     */
    fun getTodaySchedules(): List<Schedule> {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        // Convert to our system (1=Monday, 7=Sunday)
        val ourDayOfWeek = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1
        
        return schedulesList.filter { schedule ->
            schedule.isEnabled && schedule.getEnabledDays().contains(ourDayOfWeek)
        }.sortedWith(compareBy({ it.hour }, { it.minute }))
    }
    
    /**
     * Get next upcoming schedule
     */
    fun getNextSchedule(): Schedule? {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        
        val todaySchedules = getTodaySchedules()
        
        // Find next schedule today
        val nextToday = todaySchedules.find { schedule ->
            schedule.hour > currentHour || (schedule.hour == currentHour && schedule.minute > currentMinute)
        }
        
        return nextToday ?: todaySchedules.firstOrNull() // Return first schedule of next day
    }
    
    /**
     * Load schedules from repository (placeholder for Firebase integration)
     */
    private fun loadSchedulesFromRepository() {
        // TODO: Implement actual Firebase loading
        // For now, we'll create some sample data if the list is empty
        
        if (schedulesList.isEmpty()) {
            // Add some sample schedules
            schedulesList.addAll(listOf(
                Schedule(
                    id = 1,
                    title = "Wake up",
                    description = "Start the day",
                    hour = 7,
                    minute = 0,
                    category = "Personal",
                    color = "#4CAF50",
                    daysOfWeek = "1,2,3,4,5" // Weekdays
                ),
                Schedule(
                    id = 2,
                    title = "Work",
                    description = "Start work",
                    hour = 9,
                    minute = 0,
                    category = "Work",
                    color = "#2196F3",
                    daysOfWeek = "1,2,3,4,5" // Weekdays
                ),
                Schedule(
                    id = 3,
                    title = "Lunch",
                    description = "Lunch break",
                    hour = 12,
                    minute = 0,
                    category = "Personal",
                    color = "#FF9800",
                    daysOfWeek = "1,2,3,4,5,6,7" // Every day
                )
            ))
        }
        
        updateFilteredSchedules()
    }
    
    /**
     * Clear error message
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    /**
     * Force refresh schedules
     */
    fun forceRefresh() {
        loadSchedules()
    }
} 