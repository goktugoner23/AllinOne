package com.example.allinone.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.allinone.R
import com.example.allinone.data.WTEvent
import com.example.allinone.databinding.FragmentCalendarBinding
import com.example.allinone.viewmodels.CalendarViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: CalendarViewModel
    
    // Calendar related variables
    private val calendar = Calendar.getInstance()
    private val currentDate = Calendar.getInstance()
    private val dateFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val eventDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val eventDayFormat = SimpleDateFormat("d MMM", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    
    // Map to store day views for quick lookup
    private val dayViews = mutableMapOf<Int, TextView>()
    private val dayEvents = mutableMapOf<Int, MutableList<WTEvent>>()
    
    // Selected day
    private var selectedDay: Int = currentDate.get(Calendar.DAY_OF_MONTH)
    private var selectedMonth: Int = currentDate.get(Calendar.MONTH)
    private var selectedYear: Int = currentDate.get(Calendar.YEAR)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[CalendarViewModel::class.java]
        
        setupCalendarHeader()
        setupCalendarDays()
        setupObservers()
        setupNavigationButtons()
        
        // Initially render the current month
        updateCalendarForMonth(calendar)
        
        // Load lessons for at least a month
        loadLessonsForCalendar()
        
        // Handle potential Google Play Services errors
        setupGooglePlayServices()
    }
    
    private fun loadLessonsForCalendar() {
        // This will trigger loading of lessons from the repository
        viewModel.forceRefresh()
    }
    
    private fun setupCalendarHeader() {
        binding.monthYearText.text = dateFormatter.format(calendar.time)
    }
    
    private fun setupCalendarDays() {
        // Store references to day views for quick access
        val dayViewIds = listOf(
            R.id.day1, R.id.day2, R.id.day3, R.id.day4, R.id.day5, R.id.day6, R.id.day7,
            R.id.day8, R.id.day9, R.id.day10, R.id.day11, R.id.day12, R.id.day13, R.id.day14,
            R.id.day15, R.id.day16, R.id.day17, R.id.day18, R.id.day19, R.id.day20, R.id.day21,
            R.id.day22, R.id.day23, R.id.day24, R.id.day25, R.id.day26, R.id.day27, R.id.day28,
            R.id.day29, R.id.day30, R.id.day31, R.id.day32, R.id.day33, R.id.day34, R.id.day35,
            R.id.day36, R.id.day37, R.id.day38, R.id.day39, R.id.day40, R.id.day41, R.id.day42
        )
        
        // Map each view to its position
        for (i in dayViewIds.indices) {
            val dayView = binding.root.findViewById<TextView>(dayViewIds[i])
            dayViews[i + 1] = dayView
            
            // Make the view clickable and focusable
            dayView.isClickable = true
            dayView.isFocusable = true
            
            // Set up long click listener for adding events
            dayView.setOnLongClickListener {
                val day = dayView.tag as? Int ?: return@setOnLongClickListener false
                if (day > 0) {
                    // Update selected day (same as in click listener)
                    selectedDay = day
                    selectedMonth = calendar.get(Calendar.MONTH)
                    selectedYear = calendar.get(Calendar.YEAR)
                    
                    // Update calendar to show selection
                    updateCalendarForMonth(calendar)
                    
                    showAddEventDialog(day)
                }
                true
            }
            
            // Set up click listener to view events for that day
            dayView.setOnClickListener {
                val day = dayView.tag as? Int ?: return@setOnClickListener
                if (day > 0) {
                    // Update selected day
                    selectedDay = day
                    selectedMonth = calendar.get(Calendar.MONTH)
                    selectedYear = calendar.get(Calendar.YEAR)
                    
                    // Update calendar to show selection
                    updateCalendarForMonth(calendar)
                    
                    // Show events if any exist
                    if (dayEvents.containsKey(day) && dayEvents[day]?.isNotEmpty() == true) {
                        showEventsForDay(day)
                    }
                }
            }
        }
    }
    
    private fun setupObservers() {
        // Observe events from the ViewModel
        viewModel.events.observe(viewLifecycleOwner) { events ->
            // Clear previous events
            dayEvents.clear()
            
            // Group events by day
            events.forEach { event ->
                val eventCal = Calendar.getInstance().apply { time = event.date }
                // Only process events for the current month and year
                if (eventCal.get(Calendar.MONTH) == calendar.get(Calendar.MONTH) && 
                    eventCal.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)) {
                    val day = eventCal.get(Calendar.DAY_OF_MONTH)
                    if (!dayEvents.containsKey(day)) {
                        dayEvents[day] = mutableListOf()
                    }
                    dayEvents[day]?.add(event)
                }
            }
            
            // Update the calendar UI to show events
            updateCalendarForMonth(calendar)
        }
        
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { _ ->
            // Could show a loading indicator here
        }
        
        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
            }
        }
    }
    
    private fun setupNavigationButtons() {
        // Previous month button
        binding.prevMonthButton.setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            
            // Reset selection when changing months
            if (calendar.get(Calendar.MONTH) != selectedMonth || 
                calendar.get(Calendar.YEAR) != selectedYear) {
                selectedDay = -1 // No selection in the new month initially
            }
            
            updateCalendarForMonth(calendar)
        }
        
        // Next month button
        binding.nextMonthButton.setOnClickListener {
            calendar.add(Calendar.MONTH, 1)
            
            // Reset selection when changing months
            if (calendar.get(Calendar.MONTH) != selectedMonth || 
                calendar.get(Calendar.YEAR) != selectedYear) {
                selectedDay = -1 // No selection in the new month initially
            }
            
            updateCalendarForMonth(calendar)
        }
        
        // Today button
        binding.todayButton.setOnClickListener {
            calendar.time = Date() // Reset to today
            
            // Set selection to today
            selectedDay = calendar.get(Calendar.DAY_OF_MONTH)
            selectedMonth = calendar.get(Calendar.MONTH)
            selectedYear = calendar.get(Calendar.YEAR)
            
            updateCalendarForMonth(calendar)
        }
    }
    
    private fun updateCalendarForMonth(cal: Calendar) {
        // Update header
        binding.monthYearText.text = dateFormatter.format(cal.time)
        
        // Make a copy of the calendar to avoid modifying the original
        val tempCal = Calendar.getInstance()
        tempCal.time = cal.time
        
        // Set to the first day of the month
        tempCal.set(Calendar.DAY_OF_MONTH, 1)
        
        // Determine the day of week for the first day of month (0 = Sunday, 1 = Monday, etc.)
        val firstDayOfMonth = tempCal.get(Calendar.DAY_OF_WEEK)
        
        // Adjust to our calendar grid (assuming Sunday is the first day)
        val firstDayPosition = if (firstDayOfMonth == Calendar.SUNDAY) 7 else firstDayOfMonth - 1
        
        // Get the number of days in the month
        val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        // Reset all day cells
        for (i in 1..42) {
            val dayView = dayViews[i] ?: continue
            dayView.text = ""
            dayView.background = null
            dayView.foreground = null  // Clear any foreground
            dayView.setTextColor(ContextCompat.getColor(requireContext(), R.color.textPrimary))
            dayView.tag = 0 // No valid day
            dayView.isClickable = false  // Disable click for empty cells
        }
        
        // Fill in the days of the month
        for (i in 1..daysInMonth) {
            val position = i + firstDayPosition - 1
            val dayView = dayViews[position] ?: continue
            
            dayView.text = i.toString()
            dayView.tag = i // Store the day value
            dayView.isClickable = true  // Enable click for valid days
            
            // Check if this is the selected day
            val isSelectedDay = (i == selectedDay && 
                                cal.get(Calendar.MONTH) == selectedMonth && 
                                cal.get(Calendar.YEAR) == selectedYear)
            
            // Check if this is today
            val isToday = (cal.get(Calendar.YEAR) == currentDate.get(Calendar.YEAR) &&
                          cal.get(Calendar.MONTH) == currentDate.get(Calendar.MONTH) &&
                          i == currentDate.get(Calendar.DAY_OF_MONTH))
            
            // Check if this day has events
            val hasEvents = dayEvents.containsKey(i) && dayEvents[i]?.isNotEmpty() == true
            
            // Apply styling:
            // 1. If a day is selected, it gets the black circle 
            // 2. If no day is selected (selectedDay < 0), then today gets the black circle
            // 3. Days with events get a outline indicator (but no black circle)
            when {
                isSelectedDay -> {
                    // Selected day always gets black circle
                    dayView.setBackgroundResource(R.drawable.bg_selected_day) 
                    dayView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                }
                isToday && selectedDay < 0 -> {
                    // Today gets black circle only if no date is selected
                    dayView.setBackgroundResource(R.drawable.bg_selected_day)
                    dayView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                }
                hasEvents -> {
                    dayView.setBackgroundResource(R.drawable.bg_day_with_events)
                }
            }
        }
    }
    
    private fun showAddEventDialog(day: Int) {
        // Create a temporary calendar for the selected date
        val eventCal = Calendar.getInstance()
        eventCal.time = calendar.time
        eventCal.set(Calendar.DAY_OF_MONTH, day)
        
        // Create and show the dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_event, null)
        val titleInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.eventTitleInput)
        val timeInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.eventTimeInput)
        val descInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.eventDescriptionInput)
        
        // Set up time input formatter
        timeInput.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                
                isFormatting = true
                
                val text = s.toString().replace(":", "")
                if (text.length >= 3) {
                    val hour = text.substring(0, 2)
                    val minute = text.substring(2, minOf(text.length, 4))
                    s.replace(0, s.length, "$hour:$minute")
                } else if (text.length == 2) {
                    s.replace(0, s.length, "$text:")
                }
                
                isFormatting = false
            }
        })
        
        // Allow clicking on the time input to show a time picker
        timeInput.setOnClickListener {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            
            TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
                timeInput.setText(String.format("%02d:%02d", selectedHour, selectedMinute))
            }, hour, minute, true).show()
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Event for ${eventDayFormat.format(eventCal.time)}")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val title = titleInput.text.toString()
                val description = descInput.text.toString().takeIf { it.isNotEmpty() }
                val timeText = timeInput.text.toString()
                
                if (title.isNotEmpty() && timeText.matches(Regex("\\d{2}:\\d{2}"))) {
                    // Parse time
                    val parts = timeText.split(":")
                    val hour = parts[0].toInt()
                    val minute = parts[1].toInt()
                    
                    // Set time on the event calendar
                    eventCal.set(Calendar.HOUR_OF_DAY, hour)
                    eventCal.set(Calendar.MINUTE, minute)
                    
                    viewModel.addEvent(title, description, eventCal.time)
                    Toast.makeText(context, "Event added", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Please enter a title and valid time (HH:MM)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showEventsForDay(day: Int) {
        val events = dayEvents[day] ?: return
        
        if (events.isEmpty()) {
            Toast.makeText(context, "No events for this day", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Create a calendar for the selected date
        val dayCal = Calendar.getInstance()
        dayCal.time = calendar.time
        dayCal.set(Calendar.DAY_OF_MONTH, day)
        
        // Create list items
        val items = events.map { event ->
            val eventTime = eventDateFormat.format(event.date)
            "${eventTime} - ${event.title}${if (event.type == "Lesson") " (Lesson)" else ""}"
        }.toTypedArray()
        
        // Show dialog with events
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Events for ${fullDateFormat.format(dayCal.time)}")
            .setItems(items) { _, which ->
                showEventOptionsDialog(events[which])
            }
            .setPositiveButton("Add New Event") { _, _ ->
                showAddEventDialog(day)
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    private fun showEventOptionsDialog(event: WTEvent) {
        val options = if (event.type == "Lesson") {
            arrayOf("View Details", "Cancel Lesson", "Postpone Lesson")
        } else {
            arrayOf("View Details", "Edit Event", "Delete Event")
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(event.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEventDetailsDialog(event)
                    1 -> {
                        if (event.type == "Lesson") {
                            showCancelLessonConfirmation(event)
                        } else {
                            // Edit event - for simplicity, just delete and let them recreate
                            showDeleteEventConfirmation(event)
                        }
                    }
                    2 -> {
                        if (event.type == "Lesson") {
                            showPostponeLessonDialog(event)
                        } else {
                            showDeleteEventConfirmation(event)
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showEventDetailsDialog(event: WTEvent) {
        // We're only using the date and time, no need to store the calendar object
        val date = fullDateFormat.format(event.date)
        val time = eventDateFormat.format(event.date)
        
        val details = """
            |Title: ${event.title}
            |Date: $date
            |Time: $time
            |Type: ${event.type}
            |${if (event.description != null) "Description: ${event.description}" else ""}
        """.trimMargin()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Event Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showDeleteEventConfirmation(event: WTEvent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete '${event.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteEvent(event)
                Toast.makeText(context, "Event deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showCancelLessonConfirmation(event: WTEvent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancel Lesson")
            .setMessage("Are you sure you want to cancel this lesson (${event.title})?")
            .setPositiveButton("Cancel Lesson") { _, _ ->
                viewModel.cancelLesson(event.date)
                Toast.makeText(context, "Lesson cancelled", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Keep", null)
            .show()
    }
    
    private fun showPostponeLessonDialog(event: WTEvent) {
        // Create a calendar for the event
        val eventCal = Calendar.getInstance().apply { time = event.date }
        
        // Show date picker dialog
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                // Show time picker dialog
                val timePickerDialog = TimePickerDialog(
                    requireContext(),
                    { _, hourOfDay, minute ->
                        // Create a new date with the selected date and time
                        val newEventCal = Calendar.getInstance()
                        newEventCal.set(Calendar.YEAR, year)
                        newEventCal.set(Calendar.MONTH, month)
                        newEventCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        newEventCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        newEventCal.set(Calendar.MINUTE, minute)
                        
                        // Postpone the lesson
                        viewModel.postponeLesson(event.date, newEventCal.time)
                        Toast.makeText(context, "Lesson postponed", Toast.LENGTH_SHORT).show()
                    },
                    eventCal.get(Calendar.HOUR_OF_DAY),
                    eventCal.get(Calendar.MINUTE),
                    true
                )
                timePickerDialog.show()
            },
            eventCal.get(Calendar.YEAR),
            eventCal.get(Calendar.MONTH),
            eventCal.get(Calendar.DAY_OF_MONTH)
        )
        
        // Set minimum date to tomorrow
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)
        datePickerDialog.datePicker.minDate = tomorrow.timeInMillis
        
        datePickerDialog.show()
    }
    
    private fun setupGooglePlayServices() {
        try {
            // Check Google Play Services availability
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val result = availability.isGooglePlayServicesAvailable(requireContext())
            
            if (result != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                if (availability.isUserResolvableError(result)) {
                    // Show dialog to fix the issue
                    availability.getErrorDialog(requireActivity(), result, 9000)?.show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "This device doesn't support Google Play Services",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            // Log the error but continue with limited functionality
            Toast.makeText(
                requireContext(),
                "Calendar sync features may be limited: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 