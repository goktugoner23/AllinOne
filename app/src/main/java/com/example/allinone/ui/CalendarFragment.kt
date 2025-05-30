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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.EventAdapter
import com.example.allinone.data.Event
import com.example.allinone.databinding.FragmentCalendarBinding
import com.example.allinone.viewmodels.CalendarViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.allinone.utils.TextStyleUtils

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
    private val dayEvents = mutableMapOf<Int, MutableList<Event>>()
    
    // Selected day
    private var selectedDay: Int = currentDate.get(Calendar.DAY_OF_MONTH)
    private var selectedMonth: Int = currentDate.get(Calendar.MONTH)
    private var selectedYear: Int = currentDate.get(Calendar.YEAR)

    // Adapter for events
    private lateinit var eventAdapter: EventAdapter
    
    // All events for the current month
    private val monthEvents = mutableListOf<Event>()

    // Map to store all events by year, month, and day for quick lookup
    private val allEvents = mutableMapOf<Int, MutableMap<Int, MutableMap<Int, MutableList<Event>>>>()

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
        
        // Initialize the calendar to today's date
        calendar.time = Date()
        
        // Select today's date by default to show the black circle
        selectedDay = calendar.get(Calendar.DAY_OF_MONTH)
        selectedMonth = calendar.get(Calendar.MONTH)
        selectedYear = calendar.get(Calendar.YEAR)
        
        setupCalendarHeader()
        setupCalendarDays()
        setupEventsList()
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
                    
                    // Get events for the selected day from the allEvents structure
                    val selectedDayEvents = allEvents[selectedYear]?.get(selectedMonth)?.get(selectedDay)?.toList() ?: emptyList()
                    val filteredEvents = selectedDayEvents.sortedBy { it.date }
                    
                    // Update events list header to show the selected date
                    val selectedDate = Calendar.getInstance().apply {
                        set(selectedYear, selectedMonth, selectedDay)
                    }.time
                    binding.eventsHeader.text = "Events - ${fullDateFormat.format(selectedDate)}"
                    
                    // Update the RecyclerView with filtered events
                    if (filteredEvents.isEmpty()) {
                        binding.eventsRecyclerView.visibility = View.GONE
                        binding.emptyEventsText.text = "No events for ${fullDateFormat.format(selectedDate)}"
                        binding.emptyEventsText.visibility = View.VISIBLE
                    } else {
                        binding.eventsRecyclerView.visibility = View.VISIBLE
                        binding.emptyEventsText.visibility = View.GONE
                        eventAdapter.submitList(filteredEvents)
                    }
                    
                    // Scroll to the events section if there are events
                    if (filteredEvents.isNotEmpty()) {
                        binding.eventsHeader.requestFocus()
                        binding.eventsHeader.parent.requestChildFocus(binding.eventsHeader, binding.eventsHeader)
                    }
                }
            }
        }
    }
    
    private fun setupEventsList() {
        // Initialize the adapter with a click listener
        eventAdapter = EventAdapter { event ->
            showEventOptionsDialog(event)
        }
        
        // Set up the RecyclerView
        binding.eventsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
        }
    }
    
    private fun setupObservers() {
        // Observe events from the ViewModel
        viewModel.events.observe(viewLifecycleOwner) { events ->
            // Clear previous events
            dayEvents.clear()
            monthEvents.clear()
            allEvents.clear()
            
            // Debug: Log events with endDate
            events.forEach { event ->
                if (event.endDate != null) {
                    val startTime = eventDateFormat.format(event.date)
                    val endTime = eventDateFormat.format(event.endDate)
                    android.util.Log.d("CalendarDebug", "Event with endDate: ${event.title}, ${event.date}, End: ${event.endDate}, $startTime-$endTime")
                }
            }
            
            // Process all events - INCLUDING PAST EVENTS
            events.forEach { event ->
                val eventCal = Calendar.getInstance().apply { time = event.date }
                val year = eventCal.get(Calendar.YEAR)
                val month = eventCal.get(Calendar.MONTH)
                val day = eventCal.get(Calendar.DAY_OF_MONTH)
                
                // Store the event on its start date
                addEventToDateMap(year, month, day, event)
                
                // If the event has an end date and it's different from the start date,
                // add references to the event on all days it spans
                if (event.endDate != null) {
                    val endCal = Calendar.getInstance().apply { time = event.endDate }
                    val endYear = endCal.get(Calendar.YEAR)
                    val endMonth = endCal.get(Calendar.MONTH)
                    val endDay = endCal.get(Calendar.DAY_OF_MONTH)
                    
                    // Check if end date is different from start date
                    if (endYear != year || endMonth != month || endDay != day) {
                        // Create a temporary calendar for iteration
                        val tempCal = Calendar.getInstance().apply { time = event.date }
                        
                        // Move to the next day after the start date
                        tempCal.add(Calendar.DAY_OF_MONTH, 1)
                        
                        // Add the event to each day until we reach the end date
                        while (!isCalendarDateAfter(tempCal, endCal)) {
                            val spanYear = tempCal.get(Calendar.YEAR)
                            val spanMonth = tempCal.get(Calendar.MONTH)
                            val spanDay = tempCal.get(Calendar.DAY_OF_MONTH)
                            
                            // Add to this intermediate day
                            addEventToDateMap(spanYear, spanMonth, spanDay, event)
                            
                            // Move to next day
                            tempCal.add(Calendar.DAY_OF_MONTH, 1)
                        }
                    }
                }
            }
            
            // Update calendar data for current month view
            updateEventsForCurrentMonth()
            
            // Update the calendar UI
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
    
    // Helper method to add an event to the date map
    private fun addEventToDateMap(year: Int, month: Int, day: Int, event: Event) {
        if (!allEvents.containsKey(year)) {
            allEvents[year] = mutableMapOf()
        }
        if (!allEvents[year]!!.containsKey(month)) {
            allEvents[year]!![month] = mutableMapOf()
        }
        if (!allEvents[year]!![month]!!.containsKey(day)) {
            allEvents[year]!![month]!![day] = mutableListOf()
        }
        // Avoid adding duplicate events
        if (!allEvents[year]!![month]!![day]!!.contains(event)) {
            allEvents[year]!![month]!![day]!!.add(event)
        }
    }
    
    // Helper method to check if first date is after the second
    private fun isCalendarDateAfter(cal1: Calendar, cal2: Calendar): Boolean {
        val year1 = cal1.get(Calendar.YEAR)
        val month1 = cal1.get(Calendar.MONTH)
        val day1 = cal1.get(Calendar.DAY_OF_MONTH)
        
        val year2 = cal2.get(Calendar.YEAR)
        val month2 = cal2.get(Calendar.MONTH)
        val day2 = cal2.get(Calendar.DAY_OF_MONTH)
        
        return (year1 > year2) || 
               (year1 == year2 && month1 > month2) || 
               (year1 == year2 && month1 == month2 && day1 > day2)
    }
    
    private fun updateEventsList() {
        // Check if a day is selected
        if (selectedDay > 0) {
            // Get events for the selected day directly from the allEvents structure
            val selectedDayEvents = allEvents[selectedYear]?.get(selectedMonth)?.get(selectedDay)?.toList() ?: emptyList()
            val filteredEvents = selectedDayEvents.sortedBy { it.date }
            
            // Update the RecyclerView with filtered events
            if (filteredEvents.isEmpty()) {
                binding.eventsRecyclerView.visibility = View.GONE
                val selectedDate = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDay)
                }.time
                binding.emptyEventsText.text = "No events for ${fullDateFormat.format(selectedDate)}"
                binding.emptyEventsText.visibility = View.VISIBLE
            } else {
                binding.eventsRecyclerView.visibility = View.VISIBLE
                binding.emptyEventsText.visibility = View.GONE
                eventAdapter.submitList(filteredEvents)
            }
            
            // Update events header with date
            val selectedDate = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, selectedDay)
            }.time
            binding.eventsHeader.text = "Events - ${fullDateFormat.format(selectedDate)}"
        } else {
            // No day selected - don't show any events in the list
            binding.eventsRecyclerView.visibility = View.GONE
            binding.emptyEventsText.text = "Select a date to view events"
            binding.emptyEventsText.visibility = View.VISIBLE
            
            // Update the events header
            binding.eventsHeader.text = "Events - ${dateFormatter.format(calendar.time)}"
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
            
            // Load events for the new month
            updateEventsForCurrentMonth()
            
            // Update calendar UI
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
            
            // Load events for the new month
            updateEventsForCurrentMonth()
            
            // Update calendar UI
            updateCalendarForMonth(calendar)
        }
    }
    
    /**
     * Updates the dayEvents and monthEvents collections for the current month
     * using the data from the allEvents structure
     */
    private fun updateEventsForCurrentMonth() {
        // Clear the current month's data
        dayEvents.clear()
        monthEvents.clear()
        
        // Get the current year and month from the calendar
        val currentCalendarYear = calendar.get(Calendar.YEAR)
        val currentCalendarMonth = calendar.get(Calendar.MONTH)
        
        // Load events for this specific month from allEvents
        if (allEvents.containsKey(currentCalendarYear) && allEvents[currentCalendarYear]!!.containsKey(currentCalendarMonth)) {
            // We have events for this month
            allEvents[currentCalendarYear]!![currentCalendarMonth]!!.forEach { (day, events) ->
                // Add to day events for internal tracking
                dayEvents[day] = events.toMutableList()
                
                // Add all events to monthEvents for display
                monthEvents.addAll(events)
            }
            
            // Sort events by date
            monthEvents.sortBy { it.date }
        }
        
        // Update the events list UI
        updateEventsList()
    }
    
    private fun updateCalendarForMonth(cal: Calendar) {
        // Update header
        binding.monthYearText.text = dateFormatter.format(cal.time)
        
        // Make a copy of the calendar to avoid modifying the original
        val tempCal = Calendar.getInstance()
        tempCal.time = cal.time
        
        // Set to the first day of the month
        tempCal.set(Calendar.DAY_OF_MONTH, 1)
        
        // Determine the day of week for the first day of month (1 = Sunday, 2 = Monday, etc.)
        val firstDayOfMonth = tempCal.get(Calendar.DAY_OF_WEEK)
        
        // Adjust to our calendar grid (Monday is the first day now)
        // Monday = 1, Tuesday = 2, ..., Sunday = 7
        val firstDayPosition = when (firstDayOfMonth) {
            Calendar.SUNDAY -> 7  // Sunday becomes the 7th day
            else -> firstDayOfMonth - 1  // Others shift left by 1
        }
        
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
        
        // Get the current year and month for checking events
        val currentCalendarYear = cal.get(Calendar.YEAR)
        val currentCalendarMonth = cal.get(Calendar.MONTH)
        
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
            
            // Get events for this day if any
            val dayEventsList = allEvents[currentCalendarYear]?.get(currentCalendarMonth)?.get(i) ?: emptyList()
            
            // Determine what types of events exist for this day
            var hasOtherEvents = false
            var hasRegistrationEnd = false
            var hasLesson = false
            
            for (event in dayEventsList) {
                when (event.type) {
                    "Lesson" -> hasLesson = true
                    "Registration End" -> hasRegistrationEnd = true
                    else -> hasOtherEvents = true
                }
            }
            
            // Apply styling based on priority
            when {
                isSelectedDay -> {
                    // Selected day always gets black circle
                    dayView.setBackgroundResource(R.drawable.bg_selected_day) 
                    dayView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    dayView.setTypeface(null, android.graphics.Typeface.NORMAL)
                    dayView.textSize = 14f
                }
                isToday && selectedDay <= 0 -> {
                    // Today gets black circle when no other day is selected
                    dayView.setBackgroundResource(R.drawable.bg_selected_day)
                    dayView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    dayView.setTypeface(null, android.graphics.Typeface.NORMAL)
                    dayView.textSize = 14f
                    
                    // Add event indicator based on priority
                    if (hasOtherEvents) {
                        dayView.foreground = ContextCompat.getDrawable(requireContext(), R.drawable.bg_day_with_event)
                    } else if (hasRegistrationEnd) {
                        dayView.foreground = ContextCompat.getDrawable(requireContext(), R.drawable.bg_day_with_registration)
                    } else if (hasLesson) {
                        dayView.foreground = ContextCompat.getDrawable(requireContext(), R.drawable.bg_day_with_lesson)
                    }
                }
                isToday -> {
                    // Today gets themed bold text when another day is selected
                    updateTodayHighlighting(dayView, true)
                    
                    // Add event indicator based on priority
                    if (hasOtherEvents) {
                        dayView.foreground = ContextCompat.getDrawable(requireContext(), R.drawable.bg_day_with_event)
                    } else if (hasRegistrationEnd) {
                        dayView.foreground = ContextCompat.getDrawable(requireContext(), R.drawable.bg_day_with_registration)
                    } else if (hasLesson) {
                        dayView.foreground = ContextCompat.getDrawable(requireContext(), R.drawable.bg_day_with_lesson)
                    }
                }
                hasOtherEvents -> {
                    // Regular day styling with green indicator (highest priority)
                    dayView.background = null
                    dayView.setTextColor(ContextCompat.getColor(requireContext(), R.color.textPrimary))
                    dayView.setTypeface(null, android.graphics.Typeface.NORMAL)
                    dayView.textSize = 14f
                    dayView.foreground = ContextCompat.getDrawable(requireContext(), R.drawable.bg_day_with_event)
                }
                hasRegistrationEnd -> {
                    // Regular day styling with red indicator (medium priority)
                    dayView.background = null
                    dayView.setTextColor(ContextCompat.getColor(requireContext(), R.color.textPrimary))
                    dayView.setTypeface(null, android.graphics.Typeface.NORMAL)
                    dayView.textSize = 14f
                    dayView.foreground = ContextCompat.getDrawable(requireContext(), R.drawable.bg_day_with_registration)
                }
                hasLesson -> {
                    // Regular day styling with blue indicator (lowest priority)
                    dayView.background = null
                    dayView.setTextColor(ContextCompat.getColor(requireContext(), R.color.textPrimary))
                    dayView.setTypeface(null, android.graphics.Typeface.NORMAL)
                    dayView.textSize = 14f
                    dayView.foreground = ContextCompat.getDrawable(requireContext(), R.drawable.bg_day_with_lesson)
                }
                else -> {
                    // Regular day styling with no indicator
                    dayView.background = null
                    dayView.setTextColor(ContextCompat.getColor(requireContext(), R.color.textPrimary))
                    dayView.setTypeface(null, android.graphics.Typeface.NORMAL)
                    dayView.textSize = 14f
                }
            }
        }
        
        // Update the events list title for the selected day or month
        if (selectedDay > 0) {
            val selectedDate = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, selectedDay)
            }.time
            binding.eventsHeader.text = "Events - ${fullDateFormat.format(selectedDate)}"
        } else {
            binding.eventsHeader.text = "Events - ${dateFormatter.format(cal.time)}"
        }
    }
    
    private fun showAddEventDialog(day: Int) {
        // Create a temporary calendar for the selected date
        val eventCal = Calendar.getInstance()
        eventCal.time = calendar.time
        eventCal.set(Calendar.DAY_OF_MONTH, day)
        
        // Get current date for validation
        val now = Calendar.getInstance()
        
        // Ensure we're only adding events for today or future dates
        if (eventCal.before(now) && !isSameDay(eventCal, now)) {
            Toast.makeText(context, "Cannot add events for past dates", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Create and show the dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_event, null)
        val titleInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.eventTitleInput)
        val timeInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.eventTimeInput)
        val endTimeInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.eventEndTimeInput)
        val descInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.eventDescriptionInput)
        
        // Set up time input formatter
        val timeWatcher = object : TextWatcher {
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
        }
        
        timeInput.addTextChangedListener(timeWatcher)
        endTimeInput.addTextChangedListener(timeWatcher)
        
        // Allow clicking on the time input to show a time picker
        timeInput.setOnClickListener {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            
            TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
                timeInput.setText(String.format("%02d:%02d", selectedHour, selectedMinute))
            }, hour, minute, true).show()
        }
        
        // Allow clicking on the end time input to show a time picker
        endTimeInput.setOnClickListener {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            
            TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
                endTimeInput.setText(String.format("%02d:%02d", selectedHour, selectedMinute))
            }, hour, minute, true).show()
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Event for ${eventDayFormat.format(eventCal.time)}")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val title = titleInput.text.toString()
                val description = descInput.text.toString().takeIf { it.isNotEmpty() }
                val timeText = timeInput.text.toString()
                val endTimeText = endTimeInput.text.toString()
                
                if (title.isNotEmpty() && timeText.matches(Regex("\\d{2}:\\d{2}"))) {
                    // Parse start time
                    val parts = timeText.split(":")
                    val hour = parts[0].toInt()
                    val minute = parts[1].toInt()
                    
                    // Set start time on the event calendar
                    val startCalendar = Calendar.getInstance()
                    startCalendar.time = eventCal.time
                    startCalendar.set(Calendar.HOUR_OF_DAY, hour)
                    startCalendar.set(Calendar.MINUTE, minute)
                    
                    // Parse end time if provided
                    var endCalendar: Calendar? = null
                    if (endTimeText.matches(Regex("\\d{2}:\\d{2}"))) {
                        val endParts = endTimeText.split(":")
                        val endHour = endParts[0].toInt()
                        val endMinute = endParts[1].toInt()
                        
                        endCalendar = Calendar.getInstance()
                        endCalendar.time = eventCal.time
                        endCalendar.set(Calendar.HOUR_OF_DAY, endHour)
                        endCalendar.set(Calendar.MINUTE, endMinute)
                        
                        // Ensure end time is after start time
                        if (endCalendar.before(startCalendar)) {
                            Toast.makeText(context, "End time must be after start time", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                    }
                    
                    // Ensure again that the event isn't in the past after setting the time
                    if (startCalendar.before(now)) {
                        Toast.makeText(context, "Cannot add events in the past", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    viewModel.addEvent(title, description, startCalendar.time, endCalendar?.time)
                    Toast.makeText(context, "Event added", Toast.LENGTH_SHORT).show()
                    
                    // Refresh data to ensure the new event is displayed correctly
                    viewModel.forceRefresh()
                } else {
                    Toast.makeText(context, "Please enter a title and valid time (HH:MM)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    // Helper function to check if two dates are the same day
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
               cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }
    
    private fun showEventOptionsDialog(event: Event) {
        // Add a debug log to see the event details
        android.util.Log.d("EventDialog", "Event: ${event.title}, Type: ${event.type}, Start: ${event.date}, End: ${event.endDate}")
        
        val options = if (event.type == "Lesson") {
            arrayOf("View Details", "Cancel Lesson", "Postpone Lesson")
        } else {
            arrayOf("View Details", "Delete Event")
        }
        
        // Prepare the title text to include start/end time for lessons
        var titleText = event.title
        if (event.type == "Lesson" && event.endDate != null) {
            val startTime = eventDateFormat.format(event.date)
            val endTime = eventDateFormat.format(event.endDate)
            titleText = "$titleText ($startTime-$endTime)"
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleText)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEventDetailsDialog(event)
                    1 -> {
                        if (event.type == "Lesson") {
                            showCancelLessonConfirmation(event)
                        } else {
                            showDeleteEventConfirmation(event)
                        }
                    }
                    2 -> {
                        if (event.type == "Lesson") {
                            showPostponeLessonDialog(event)
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showEventDetailsDialog(event: Event) {
        // We're only using the date and time, no need to store the calendar object
        val date = fullDateFormat.format(event.date)
        val time = eventDateFormat.format(event.date)
        
        // Format event details
        val details = buildString {
            append("Title: ${event.title}\n")
            append("Date: $date\n")
            append("Start Time: $time\n")
            
            // Add end time if available
            if (event.endDate != null) {
                append("End Time: ${eventDateFormat.format(event.endDate)}\n")
            }
            
            append("Type: ${event.type}\n")
            
            if (event.description != null) {
                append("Description: ${event.description}")
            }
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Event Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showDeleteEventConfirmation(event: Event) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete '${event.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteEvent(event)
                Toast.makeText(context, "Event deleted", Toast.LENGTH_SHORT).show()
                
                // Force refresh to update all data structures
                viewModel.forceRefresh()
                
                // Update calendar UI
                updateCalendarForMonth(calendar)
                
                // Update UI with the latest events
                updateEventsList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showCancelLessonConfirmation(event: Event) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancel Lesson")
            .setMessage("Are you sure you want to cancel this lesson (${event.title})?")
            .setPositiveButton("Cancel Lesson") { _, _ ->
                viewModel.cancelLesson(event.date)
                Toast.makeText(context, "Lesson cancelled", Toast.LENGTH_SHORT).show()
                
                // Force refresh to update all data structures
                viewModel.forceRefresh()
                
                // Update calendar UI
                updateCalendarForMonth(calendar)
                
                // Update UI with the latest events
                updateEventsList()
            }
            .setNegativeButton("Keep", null)
            .show()
    }
    
    private fun showPostponeLessonDialog(event: Event) {
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
                        
                        // Force data refresh to update all data structures
                        viewModel.forceRefresh()
                        
                        // Refresh the calendar UI
                        updateCalendarForMonth(calendar)
                        
                        // If the user postponed to a day in the current view, select that day
                        if (month == calendar.get(Calendar.MONTH) && year == calendar.get(Calendar.YEAR)) {
                            selectedDay = dayOfMonth
                            selectedMonth = month
                            selectedYear = year
                            
                            // Update the events list
                            updateEventsList()
                        }
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
        // Remove observers to prevent the "Cannot add the same observer with different lifecycles" error
        viewModel.events.removeObservers(viewLifecycleOwner)
        viewModel.isLoading.removeObservers(viewLifecycleOwner)
        viewModel.errorMessage.removeObservers(viewLifecycleOwner)
        _binding = null
    }
    
    override fun onResume() {
        super.onResume()
        
        // Force refresh to ensure all lessons are visible
        viewModel.forceRefresh()
    }

    // For today's date highlighting
    private fun updateTodayHighlighting(dayView: TextView, isToday: Boolean) {
        if (isToday) {
            // Today gets EXTRA BOLD text
            dayView.background = null
            
            // Get consistent text color based on theme
            val isNightMode = resources.configuration.uiMode and 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            // Set text color based on theme - black in light mode, white in night mode
            val textColor = if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            dayView.setTextColor(textColor)
            
            // Create an extra bold typeface for today
            val typefaceStyle = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 
                                android.graphics.Typeface.BOLD)
            dayView.setTypeface(typefaceStyle)
            
            // Make text even larger for better visibility
            dayView.textSize = 20f
            
            // Make the text appear bolder with paint flags
            dayView.paintFlags = dayView.paintFlags or android.graphics.Paint.FAKE_BOLD_TEXT_FLAG
        }
    }
} 