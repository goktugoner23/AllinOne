package com.example.allinone.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.WTSeminar
import com.example.allinone.data.Event
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WTSeminarsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application)
    private val TAG = "WTSeminarsViewModel"
    
    // LiveData for seminars
    private val _seminars = MutableLiveData<List<WTSeminar>>(emptyList())
    val seminars: LiveData<List<WTSeminar>> = _seminars
    
    // LiveData for loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // LiveData for network availability
    val isNetworkAvailable: LiveData<Boolean> = repository.isNetworkAvailable
    
    // Format for displaying time
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    
    init {
        // Load seminars on initialization
        refreshSeminars()
    }
    
    /**
     * Extract seminars from events list
     */
    private fun extractSeminarsFromEvents(events: List<Event>) {
        viewModelScope.launch {
            try {
                // Filter events of type "Seminar"
                val seminarEvents = events.filter { it.type == "Seminar" }
                
                // Convert events to seminars
                val seminarsList = seminarEvents.map { event ->
                    // Parse time information from description
                    // Format expected: "Time: HH:mm-HH:mm, Description: optional description"
                    val descriptionParts = event.description?.split(", Description: ") ?: listOf("", "")
                    val timeInfo = descriptionParts[0].removePrefix("Time: ")
                    
                    // Extract start and end times
                    val times = timeInfo.split("-")
                    
                    val startTime = if (times.isNotEmpty() && times[0].isNotEmpty()) {
                        val parts = times[0].split(":")
                        Pair(parts[0].toIntOrNull() ?: 9, parts[1].toIntOrNull() ?: 0)
                    } else {
                        Pair(9, 0) // Default to 9:00 AM
                    }
                    
                    val endTime = if (times.size > 1 && times[1].isNotEmpty()) {
                        val parts = times[1].split(":")
                        Pair(parts[0].toIntOrNull() ?: 13, parts[1].toIntOrNull() ?: 0)
                    } else {
                        // Default to 4 hours after start
                        var endHour = startTime.first + 4
                        if (endHour >= 24) endHour -= 24
                        Pair(endHour, startTime.second)
                    }
                    
                    val description = if (descriptionParts.size > 1) descriptionParts[1] else null
                    
                    WTSeminar(
                        id = event.id,
                        name = event.title,
                        date = event.date,
                        startHour = startTime.first,
                        startMinute = startTime.second,
                        endHour = endTime.first,
                        endMinute = endTime.second,
                        description = description
                    )
                }
                
                // Update LiveData
                _seminars.postValue(seminarsList)
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting seminars: ${e.message}", e)
            }
        }
    }
    
    /**
     * Refresh seminars from the repository
     */
    fun refreshSeminars() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Force refresh events from the repository
                repository.refreshEvents()
                
                // Get the latest events from repository
                val events = repository.events.value
                
                // Process the events to extract seminars
                extractSeminarsFromEvents(events)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing seminars: ${e.message}", e)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    
    /**
     * Add a new seminar
     */
    fun addSeminar(seminar: WTSeminar) {
        viewModelScope.launch {
            try {
                // Create a description that includes time information
                val timeInfo = "Time: ${formatTime(seminar.startHour, seminar.startMinute)}-${formatTime(seminar.endHour, seminar.endMinute)}"
                val description = if (seminar.description != null) {
                    "$timeInfo, Description: ${seminar.description}"
                } else {
                    timeInfo
                }
                
                // Create an event for this seminar
                val event = Event(
                    id = seminar.id,
                    title = seminar.name,
                    description = description,
                    date = seminar.date,
                    type = "Seminar" // Add a type to distinguish seminars from other events
                )
                
                // Save the event to the repository
                repository.insertEvent(event)
                
                // Force refresh to ensure calendar updates
                repository.refreshEvents()
                
                // Refresh seminars to include the new one
                refreshSeminars()
            } catch (e: Exception) {
                Log.e(TAG, "Error adding seminar: ${e.message}", e)
            }
        }
    }
    
    /**
     * Delete a seminar
     */
    fun deleteSeminar(seminar: WTSeminar) {
        viewModelScope.launch {
            try {
                // Create a corresponding event to delete
                val event = Event(
                    id = seminar.id,
                    title = seminar.name,
                    description = "",
                    date = seminar.date,
                    type = "Seminar"
                )
                
                // Delete the event from the repository
                repository.deleteEvent(event)
                
                // Force refresh to ensure calendar updates
                repository.refreshEvents()
                
                // Refresh the seminars list
                refreshSeminars()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting seminar: ${e.message}", e)
            }
        }
    }
    
    /**
     * Format time as HH:mm
     */
    fun formatTime(hour: Int, minute: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        return timeFormat.format(calendar.time)
    }
    
    /**
     * Format a date as a user-friendly string
     */
    fun formatDate(date: Date): String {
        return dateFormat.format(date)
    }
    
    /**
     * Get a list of upcoming seminars (not in the past)
     */
    fun getUpcomingSeminars(): List<WTSeminar> {
        val currentDate = Calendar.getInstance().time
        return seminars.value?.filter { seminar ->
            seminar.date.after(currentDate) || isSameDay(seminar.date, currentDate)
        }?.sortedBy { it.date } ?: emptyList()
    }
    
    /**
     * Check if two dates represent the same day
     */
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.time = date1
        cal2.time = date2
        
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
               cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }
} 