package com.example.allinone.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.allinone.data.Event
import com.example.allinone.data.WTLesson
import com.example.allinone.data.WTRegistration
import com.example.allinone.data.WTStudent
import com.example.allinone.firebase.FirebaseRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FirebaseRepository(application)

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

    // Registrations and students for calendar events
    private val _registrations = MutableLiveData<List<WTRegistration>>(emptyList())
    private val _students = MutableLiveData<List<WTStudent>>(emptyList())

    // In-memory storage to complement Firebase data
    private val eventsList = mutableListOf<Event>()

    init {
        // Initial load
        loadEvents()

        // Also load registrations and students
        loadRegistrationsAndStudents()
    }

    /**
     * Update our events list from Firebase data
     */
    private fun updateEventsFromFirebase(firebaseEvents: List<Event>) {
        viewModelScope.launch {
            try {
                // Log what we're loading from Firebase
                val postponedLessons = firebaseEvents.filter {
                    it.type == "Lesson" && it.title.contains("(Postponed)")
                }
                val cancelledLessons = firebaseEvents.filter {
                    it.type == "Lesson" && it.title.contains("(Cancelled)")
                }

                Log.d("CalendarViewModel", "Updating from Firebase: ${firebaseEvents.size} total events, " +
                      "${postponedLessons.size} postponed lessons, ${cancelledLessons.size} cancelled lessons")

                // First, remove all non-lesson events as they will be loaded from Firebase
                eventsList.removeIf { event -> event.type != "Lesson" }

                // Then, remove all special lesson events (postponed or cancelled) as they will be loaded from Firebase
                eventsList.removeIf { event ->
                    event.type == "Lesson" && (event.title.contains("(Postponed)") || event.title.contains("(Cancelled)"))
                }

                // Log the remaining regular lessons for debugging
                val regularLessons = eventsList.filter { it.type == "Lesson" }
                Log.d("CalendarViewModel", "Regular lessons in memory after cleanup: ${regularLessons.size}")

                // Add all events from Firebase
                for (event in firebaseEvents) {
                    // Skip if this event ID already exists in our list
                    if (eventsList.any { it.id == event.id }) continue

                    // Add the event from Firebase
                    eventsList.add(event)

                    // Log special lesson events for debugging
                    if (event.type == "Lesson") {
                        if (event.title.contains("(Postponed)")) {
                            Log.d("CalendarViewModel", "Loaded postponed lesson from Firebase: ${event.id}, ${event.date}")
                        } else if (event.title.contains("(Cancelled)")) {
                            Log.d("CalendarViewModel", "Loaded cancelled lesson from Firebase: ${event.id}, ${event.date}")
                        }
                    }
                }

                // Update the LiveData
                _events.value = eventsList.toList()
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Error updating events from Firebase: ${e.message}", e)
                _errorMessage.value = "Error updating events: ${e.message}"
            }
        }
    }

    /**
     * Load events from Firebase
     */
    private fun loadEvents() {
        _isLoading.value = true

        viewModelScope.launch {
            try {
                Log.d("CalendarViewModel", "Loading events from Firebase")

                // Request a refresh from the repository
                repository.refreshEvents()

                // Get the events directly from the repository
                val firebaseEvents = repository.events.value

                // Also load lesson schedule
                repository.refreshWTLessons()
                _lessonSchedule.value = repository.wtLessons.value

                // Update our local events list from Firebase first
                updateEventsFromFirebase(firebaseEvents)

                // Generate any additional events (like lessons)
                generateLessonEvents()

                _isLoading.value = false

                Log.d("CalendarViewModel", "Events loaded successfully, total events: ${eventsList.size}")
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Failed to load events: ${e.message}", e)
                _errorMessage.value = "Failed to load events: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    /**
     * Load registrations and students to display in calendar
     */
    private fun loadRegistrationsAndStudents() {
        viewModelScope.launch {
            try {
                // Load registrations
                repository.refreshRegistrations()
                _registrations.value = repository.registrations.value

                // Load students
                repository.refreshStudents()
                _students.value = repository.students.value

                // Generate registration events
                generateRegistrationEvents()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load registrations: ${e.message}"
            }
        }
    }

    /**
     * Add a new event to the calendar
     */
    fun addEvent(title: String, description: String?, date: Date, endDate: Date? = null) {
        viewModelScope.launch {
            try {
                val newEvent = Event(
                    id = System.currentTimeMillis(), // Simple ID generation
                    title = title,
                    description = description,
                    date = date,
                    endDate = endDate,
                    type = "Event"
                )

                // Add to Firebase repository only - our observer will add it to local list
                repository.insertEvent(newEvent)

                // Refresh repository data
                repository.refreshEvents()

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
                // Remove from Firebase repository
                repository.deleteEvent(event)

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
                val lessonToCancel = eventsList.find { event ->
                    val eventCal = Calendar.getInstance().apply { time = event.date }
                    event.type == "Lesson" &&
                    eventCal.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                    eventCal.get(Calendar.MONTH) == calendar.get(Calendar.MONTH) &&
                    eventCal.get(Calendar.DAY_OF_MONTH) == calendar.get(Calendar.DAY_OF_MONTH) &&
                    eventCal.get(Calendar.HOUR_OF_DAY) == calendar.get(Calendar.HOUR_OF_DAY) &&
                    eventCal.get(Calendar.MINUTE) == calendar.get(Calendar.MINUTE)
                }

                if (lessonToCancel != null) {
                    Log.d("CalendarViewModel", "Found lesson to cancel: ${lessonToCancel.id}, ${lessonToCancel.date}")

                    // If this is a regular lesson (not a postponed one), we need to create a special
                    // "cancelled" marker in Firebase to prevent it from being regenerated
                    if (!lessonToCancel.title.contains("(Postponed)")) {
                        // Create a cancelled lesson marker with EXACTLY the same date/time as the original
                        val cancelledMarker = lessonToCancel.copy(
                            id = System.currentTimeMillis(),
                            title = "${lessonToCancel.title} (Cancelled)",
                            description = "${lessonToCancel.description ?: ""}\nCancelled on ${Date()}",
                            // Keep the exact same date and endDate
                            date = lessonToCancel.date,
                            endDate = lessonToCancel.endDate
                        )

                        // Save the cancelled marker to Firebase
                        repository.insertEvent(cancelledMarker)
                        Log.d("CalendarViewModel", "Created cancelled lesson marker: ${cancelledMarker.id} for date ${cancelledMarker.date}")
                    } else {
                        // For postponed lessons, just delete them from Firebase
                        repository.deleteEvent(lessonToCancel)
                        Log.d("CalendarViewModel", "Deleted postponed lesson from Firebase: ${lessonToCancel.id}")
                    }

                    // Remove from our in-memory list
                    eventsList.removeIf { it.id == lessonToCancel.id }

                    // Update the LiveData
                    _events.value = eventsList.toList()

                    Log.d("CalendarViewModel", "Lesson cancelled successfully: ${lessonToCancel.id}")
                } else {
                    Log.e("CalendarViewModel", "Failed to find lesson to cancel at date: $date")
                    _errorMessage.value = "Failed to find lesson to cancel"
                }

            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Failed to cancel lesson: ${e.message}", e)
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

                if (lessonToPostpone != null) {
                    Log.d("CalendarViewModel", "Found lesson to postpone: ${lessonToPostpone.id}, ${lessonToPostpone.date}")

                    // Calculate the new end date if one exists
                    val newEndDate = if (lessonToPostpone.endDate != null) {
                        // Get the duration between original start and end
                        val durationMs = lessonToPostpone.endDate.time - lessonToPostpone.date.time
                        // Apply same duration to new date
                        Date(newDate.time + durationMs)
                    } else {
                        null
                    }

                    // Create a new event with the postponed date
                    val postponedEvent = lessonToPostpone.copy(
                        id = System.currentTimeMillis(), // New ID for the postponed event
                        date = newDate,
                        endDate = newEndDate,
                        title = "${lessonToPostpone.title} (Postponed)",
                        description = "${lessonToPostpone.description ?: ""}\nPostponed from ${origCal.time}".trim()
                    )

                    // If this is a regular lesson (not already postponed), we need to create a special
                    // "cancelled" marker in Firebase to prevent it from being regenerated
                    if (!lessonToPostpone.title.contains("(Postponed)")) {
                        // Create a cancelled lesson marker with EXACTLY the same date/time as the original
                        val cancelledMarker = lessonToPostpone.copy(
                            id = System.currentTimeMillis() + 1, // Different ID from postponed event
                            title = "${lessonToPostpone.title} (Cancelled)",
                            description = "${lessonToPostpone.description ?: ""}\nPostponed to ${newDate} on ${Date()}",
                            // Keep the exact same date and endDate
                            date = lessonToPostpone.date,
                            endDate = lessonToPostpone.endDate
                        )

                        // Save the cancelled marker to Firebase
                        repository.insertEvent(cancelledMarker)
                        Log.d("CalendarViewModel", "Created cancelled lesson marker: ${cancelledMarker.id} for date ${cancelledMarker.date}")
                    } else {
                        // For already postponed lessons, delete the original from Firebase
                        repository.deleteEvent(lessonToPostpone)
                        Log.d("CalendarViewModel", "Deleted original postponed lesson: ${lessonToPostpone.id}")
                    }

                    // Add the new postponed event to Firebase
                    repository.insertEvent(postponedEvent)
                    Log.d("CalendarViewModel", "Added new postponed event to Firebase: ${postponedEvent.id}")

                    // Remove the original event from our in-memory list
                    eventsList.removeIf { it.id == lessonToPostpone.id }

                    // Add the new postponed event to our in-memory list
                    eventsList.add(postponedEvent)

                    // Update the LiveData
                    _events.value = eventsList.toList()

                    // Now handle registration extensions for students if needed
                    extendRegistrationsAfterPostponement(originalDate, newDate)

                    Log.d("CalendarViewModel", "Lesson postponed successfully: ${lessonToPostpone.id} -> ${postponedEvent.id}")
                } else {
                    Log.e("CalendarViewModel", "Failed to find lesson to postpone at date: $originalDate")
                    _errorMessage.value = "Failed to find lesson to postpone"
                }

            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Failed to postpone lesson: ${e.message}", e)
                _errorMessage.value = "Failed to postpone lesson: ${e.message}"
            }
        }
    }

    /**
     * Extend registrations for students if a postponed lesson falls outside their registration period
     */
    private fun extendRegistrationsAfterPostponement(originalDate: Date, newDate: Date) {
        viewModelScope.launch {
            try {
                // Get current registrations and lessons
                val registrations = _registrations.value ?: return@launch
                val lessons = _lessonSchedule.value ?: return@launch

                if (registrations.isEmpty() || lessons.isEmpty()) return@launch

                // Registrations that need to be updated
                val registrationsToUpdate = mutableListOf<WTRegistration>()

                // Check each registration
                for (registration in registrations) {
                    // Skip if missing start or end date
                    if (registration.startDate == null || registration.endDate == null) continue

                    // Check if original lesson was within this registration's period
                    val originalInRange = originalDate.time >= registration.startDate.time &&
                                         originalDate.time <= registration.endDate.time

                    // Check if new lesson date is after the registration's end date
                    val newDateOutOfRange = newDate.time > registration.endDate.time

                    // If both conditions are true, this registration needs extension
                    if (originalInRange && newDateOutOfRange) {
                        // Calculate new end date by adding one lesson from the current end date
                        val endCal = Calendar.getInstance()
                        endCal.time = registration.endDate

                        // Use the end date as the start point to find the next lesson date
                        val newEndDate = calculateDateAfterNLessons(endCal, lessons, 1)

                        // Set time to 22:00 (10pm)
                        val adjustedEndCal = Calendar.getInstance()
                        adjustedEndCal.time = newEndDate
                        adjustedEndCal.set(Calendar.HOUR_OF_DAY, 22)
                        adjustedEndCal.set(Calendar.MINUTE, 0)
                        adjustedEndCal.set(Calendar.SECOND, 0)
                        adjustedEndCal.set(Calendar.MILLISECOND, 0)

                        // Create updated registration with new end date
                        val updatedRegistration = registration.copy(
                            endDate = adjustedEndCal.time
                        )

                        // Add to list of registrations to update
                        registrationsToUpdate.add(updatedRegistration)
                    }
                }

                // Update registrations in Firebase
                if (registrationsToUpdate.isNotEmpty()) {
                    for (registration in registrationsToUpdate) {
                        repository.updateRegistration(registration)
                        Log.d("CalendarViewModel", "Extended registration ${registration.id} end date to accommodate postponed lesson")
                    }

                    // Refresh registrations data
                    repository.refreshRegistrations()

                    // Generate events for the updated registrations
                    generateRegistrationEvents()
                }

            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Failed to extend registrations: ${e.message}")
                _errorMessage.value = "Failed to update registrations: ${e.message}"
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
     *
     * Note: This method only generates in-memory events for display purposes.
     * Individual lesson events are not stored in Firebase until they are modified
     * (cancelled or postponed), at which point they become persistent.
     */
    private fun generateLessonEvents() {
        val lessons = _lessonSchedule.value ?: return
        if (lessons.isEmpty()) return

        // Get all events from Firebase that are related to lessons
        val firebaseEvents = repository.events.value
        val postponedLessons = firebaseEvents.filter {
            it.type == "Lesson" && it.title.contains("(Postponed)")
        }
        val cancelledLessons = firebaseEvents.filter {
            it.type == "Lesson" && it.title.contains("(Cancelled)")
        }

        Log.d("CalendarViewModel", "Found ${postponedLessons.size} postponed lessons and ${cancelledLessons.size} cancelled lessons in Firebase")

        // Clear any existing regular lesson events (keep postponed ones)
        eventsList.removeIf { event ->
            event.type == "Lesson" && !event.title.contains("(Postponed)") && !event.title.contains("(Cancelled)")
        }

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

            // Directly set the day of week from the lesson
            // This ensures we get the correct day regardless of how the calendar is configured
            val daysToAdd = when (lesson.dayOfWeek) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> 0 // Default to Monday if invalid
            }

            // Add the appropriate number of days to reach the target day
            lessonCalendar.add(Calendar.DAY_OF_MONTH, daysToAdd)

            // Keep adding weekly lessons until we reach the far future date
            while (lessonCalendar.before(farFutureDate)) {
                // Set the lesson start time
                val startCalendar = Calendar.getInstance()
                startCalendar.time = lessonCalendar.time
                startCalendar.set(Calendar.HOUR_OF_DAY, lesson.startHour)
                startCalendar.set(Calendar.MINUTE, lesson.startMinute)

                // Set the lesson end time
                val endCalendar = Calendar.getInstance()
                endCalendar.time = lessonCalendar.time
                endCalendar.set(Calendar.HOUR_OF_DAY, lesson.endHour)
                endCalendar.set(Calendar.MINUTE, lesson.endMinute)

                // Check if this lesson has been cancelled
                val isCancelled = cancelledLessons.any { cancelledLesson ->
                    val cancelledCal = Calendar.getInstance().apply { time = cancelledLesson.date }

                    // Compare year, month, day, hour, and minute
                    val sameDateTime =
                        cancelledCal.get(Calendar.YEAR) == startCalendar.get(Calendar.YEAR) &&
                        cancelledCal.get(Calendar.MONTH) == startCalendar.get(Calendar.MONTH) &&
                        cancelledCal.get(Calendar.DAY_OF_MONTH) == startCalendar.get(Calendar.DAY_OF_MONTH) &&
                        cancelledCal.get(Calendar.HOUR_OF_DAY) == startCalendar.get(Calendar.HOUR_OF_DAY) &&
                        cancelledCal.get(Calendar.MINUTE) == startCalendar.get(Calendar.MINUTE)

                    if (sameDateTime) {
                        Log.d("CalendarViewModel", "Found cancelled lesson marker for ${startCalendar.time}")
                    }

                    sameDateTime
                }

                // Skip this lesson if it's been cancelled
                if (isCancelled) {
                    Log.d("CalendarViewModel", "Skipping cancelled lesson on ${startCalendar.time}")
                    lessonCalendar.add(Calendar.WEEK_OF_YEAR, 1)
                    continue
                }

                // Check if this lesson has been postponed
                val isPostponed = postponedLessons.any { postponedLesson ->
                    val originalDateDesc = postponedLesson.description ?: ""
                    val originalDateStr = originalDateDesc.substringAfter("Postponed from ").trim()

                    if (originalDateStr.isNotEmpty()) {
                        try {
                            val originalDate = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US).parse(originalDateStr)
                            val originalCal = Calendar.getInstance().apply { time = originalDate ?: Date() }

                            originalCal.get(Calendar.YEAR) == startCalendar.get(Calendar.YEAR) &&
                            originalCal.get(Calendar.MONTH) == startCalendar.get(Calendar.MONTH) &&
                            originalCal.get(Calendar.DAY_OF_MONTH) == startCalendar.get(Calendar.DAY_OF_MONTH) &&
                            originalCal.get(Calendar.HOUR_OF_DAY) == startCalendar.get(Calendar.HOUR_OF_DAY) &&
                            originalCal.get(Calendar.MINUTE) == startCalendar.get(Calendar.MINUTE)
                        } catch (e: Exception) {
                            Log.e("CalendarViewModel", "Error parsing original date: $originalDateStr", e)
                            false
                        }
                    } else {
                        false
                    }
                }

                // Skip this lesson if it's been postponed
                if (isPostponed) {
                    Log.d("CalendarViewModel", "Skipping postponed lesson on ${startCalendar.time}")
                    lessonCalendar.add(Calendar.WEEK_OF_YEAR, 1)
                    continue
                }

                // Create the event
                val event = Event(
                    id = System.currentTimeMillis() + eventsList.size, // Simple unique ID
                    title = "WT Lesson", // Simplified title since we now use endDate properly
                    description = "Regular weekly Wing Tzun lesson",
                    date = startCalendar.time,
                    endDate = endCalendar.time,
                    type = "Lesson"
                )

                // Add to our list
                eventsList.add(event)

                // Move to next week
                lessonCalendar.add(Calendar.WEEK_OF_YEAR, 1)
            }
        }

        // Make sure all postponed lessons from Firebase are in our list
        for (postponedLesson in postponedLessons) {
            if (!eventsList.any { it.id == postponedLesson.id }) {
                eventsList.add(postponedLesson)
                Log.d("CalendarViewModel", "Added postponed lesson from Firebase: ${postponedLesson.id}, ${postponedLesson.date}")
            }
        }

        // Update the LiveData - Use postValue for thread safety
        _events.postValue(eventsList.toList())
    }

    /**
     * Generate calendar events for registration start and end dates
     */
    private fun generateRegistrationEvents() {
        val registrations = _registrations.value ?: return
        val students = _students.value ?: return

        if (registrations.isEmpty()) return

        // First remove any existing registration events
        eventsList.removeIf { it.type == "Registration Start" || it.type == "Registration End" }

        // Create a student ID to name map for quick lookup
        val studentNames = students.associateBy({ it.id }, { it.name })

        // For each registration, create start and end date events
        for (registration in registrations) {
            val studentName = studentNames[registration.studentId] ?: "Unknown Student"

            // Only process registrations with valid dates
            if (registration.startDate != null) {
                // Create start date event
                val startEvent = Event(
                    id = registration.id * 10 + 1, // Use a formula to ensure unique IDs
                    title = "$studentName - Registration Start",
                    description = "Training period starts (${formatAmount(registration.amount)})",
                    date = registration.startDate,
                    type = "Registration Start"
                )
                eventsList.add(startEvent)
            }

            if (registration.endDate != null) {
                // Create end date event
                val endEvent = Event(
                    id = registration.id * 10 + 2, // Different ID from start event
                    title = "$studentName - Registration End",
                    description = "Training period ends (${formatAmount(registration.amount)})",
                    date = registration.endDate,
                    type = "Registration End"
                )
                eventsList.add(endEvent)
            }
        }

        // Update the LiveData - Use postValue for thread safety
        _events.postValue(eventsList.toList())
    }

    /**
     * Format amount as currency
     */
    private fun formatAmount(amount: Double): String {
        return String.format("%.2f ₺", amount)
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
            try {
                Log.d("CalendarViewModel", "Starting force refresh of calendar data")

                // Refresh events from Firebase
                repository.refreshEvents()

                // Get the latest events directly
                val firebaseEvents = repository.events.value

                // Log the events from Firebase for debugging
                Log.d("CalendarViewModel", "Loaded ${firebaseEvents.size} events from Firebase")
                firebaseEvents.filter { it.type == "Lesson" }.forEach {
                    Log.d("CalendarViewModel", "Firebase Lesson: ${it.id}, ${it.title}, ${it.date}")
                }

                // Refresh registrations
                repository.refreshRegistrations()
                _registrations.value = repository.registrations.value

                // Refresh students
                repository.refreshStudents()
                _students.value = repository.students.value

                // Refresh lesson schedule
                repository.refreshWTLessons()
                _lessonSchedule.value = repository.wtLessons.value

                // Update our local events from Firebase first
                updateEventsFromFirebase(firebaseEvents)

                // Generate lesson events based on current lesson schedule
                generateLessonEvents()

                // Generate registration events
                generateRegistrationEvents()

                // Update UI state
                _isLoading.value = false

                Log.d("CalendarViewModel", "Calendar data refresh complete, total events: ${eventsList.size}")
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Failed to refresh events: ${e.message}", e)
                _errorMessage.value = "Failed to refresh events: ${e.message}"
                _isLoading.value = false
            }
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

    /**
     * Add an event directly to the internal events list
     * This is used to ensure events added from other view models show up immediately
     * until the next Firebase refresh
     */
    fun addEventDirectly(event: Event) {
        // Check if event with this ID already exists in our list
        val eventExists = eventsList.any { it.id == event.id }

        // Only add if it doesn't already exist
        if (!eventExists) {
            // Add to our in-memory list
            eventsList.add(event)

            // Update the LiveData
            _events.value = eventsList.toList()
        }
    }

    /**
     * Calculate the next lesson date after the given start date
     * @param startDate The starting date to search from
     * @param lessons The list of weekly lessons to check against
     * @return The date of the next lesson, or 7 days later if no lessons found
     */
    fun calculateNextLessonDate(startDate: Calendar, lessons: List<WTLesson>): Date {
        // If there are no lessons, return a date 7 days after the start date
        if (lessons.isEmpty()) {
            val nextWeekCalendar = Calendar.getInstance()
            nextWeekCalendar.time = startDate.time
            nextWeekCalendar.add(Calendar.DAY_OF_MONTH, 7)
            return nextWeekCalendar.time
        }

        // Clone the start date to avoid modifying the original
        val currentDate = Calendar.getInstance()
        currentDate.time = startDate.time

        // Try for the next 14 days (2 weeks) to find a lesson
        for (day in 0 until 14) {
            // Get the day of week (1 = Sunday, 7 = Saturday)
            val dayOfWeek = currentDate.get(Calendar.DAY_OF_WEEK)

            // Check if there's a lesson on this day
            for (lesson in lessons) {
                // Convert the WTLesson day format to Calendar.DAY_OF_WEEK
                // WTLesson uses: 0 = Monday, 6 = Sunday
                // Calendar uses: 1 = Sunday, 2 = Monday, ..., 7 = Saturday
                val lessonDayOfWeek = when (lesson.dayOfWeek) {
                    0 -> Calendar.MONDAY
                    1 -> Calendar.TUESDAY
                    2 -> Calendar.WEDNESDAY
                    3 -> Calendar.THURSDAY
                    4 -> Calendar.FRIDAY
                    5 -> Calendar.SATURDAY
                    6 -> Calendar.SUNDAY
                    else -> -1 // Invalid day
                }

                // If this is the right day and we haven't passed the lesson time
                if (dayOfWeek == lessonDayOfWeek) {
                    // We found a lesson day - return this date
                    return currentDate.time
                }
            }

            // Move to the next day
            currentDate.add(Calendar.DAY_OF_MONTH, 1)
        }

        // If no lessons found within 14 days, return a date 7 days after start
        val nextWeekCalendar = Calendar.getInstance()
        nextWeekCalendar.time = startDate.time
        nextWeekCalendar.add(Calendar.DAY_OF_MONTH, 7)
        return nextWeekCalendar.time
    }

    /**
     * Calculate the date after a specified number of lessons from the start date
     * @param startDate The starting date to search from
     * @param lessons The list of weekly lessons to check against
     * @param lessonCount The number of lessons to count (default is 8)
     * @return The date after the specified number of lessons
     */
    fun calculateDateAfterNLessons(startDate: Calendar, lessons: List<WTLesson>, lessonCount: Int = 8): Date {
        // If there are no lessons, return a date 8 weeks after the start date
        if (lessons.isEmpty()) {
            val endCalendar = Calendar.getInstance()
            endCalendar.time = startDate.time
            endCalendar.add(Calendar.WEEK_OF_YEAR, 8)
            return endCalendar.time
        }

        // Clone the start date to avoid modifying the original
        val currentDate = Calendar.getInstance()
        currentDate.time = startDate.time

        // Keep track of how many lessons we've found
        var lessonsFound = 0

        // Try for up to a year to find enough lessons
        for (day in 0 until 365) {
            // Get the day of week (1 = Sunday, 7 = Saturday)
            val dayOfWeek = currentDate.get(Calendar.DAY_OF_WEEK)

            // Check if there's a lesson on this day
            for (lesson in lessons) {
                // Convert the WTLesson day format to Calendar.DAY_OF_WEEK
                // WTLesson uses: 0 = Monday, 6 = Sunday
                // Calendar uses: 1 = Sunday, 2 = Monday, ..., 7 = Saturday
                val lessonDayOfWeek = when (lesson.dayOfWeek) {
                    0 -> Calendar.MONDAY
                    1 -> Calendar.TUESDAY
                    2 -> Calendar.WEDNESDAY
                    3 -> Calendar.THURSDAY
                    4 -> Calendar.FRIDAY
                    5 -> Calendar.SATURDAY
                    6 -> Calendar.SUNDAY
                    else -> -1 // Invalid day
                }

                // If this is a lesson day
                if (dayOfWeek == lessonDayOfWeek) {
                    // We found a lesson day
                    lessonsFound++

                    // If we've found enough lessons, return this date
                    if (lessonsFound >= lessonCount) {
                        return currentDate.time
                    }

                    // Break the inner loop since we've already counted this day's lesson
                    break
                }
            }

            // Move to the next day
            currentDate.add(Calendar.DAY_OF_MONTH, 1)
        }

        // If not enough lessons found within a year, fallback to 8 weeks after start
        val fallbackCalendar = Calendar.getInstance()
        fallbackCalendar.time = startDate.time
        fallbackCalendar.add(Calendar.WEEK_OF_YEAR, 8)
        return fallbackCalendar.time
    }
}