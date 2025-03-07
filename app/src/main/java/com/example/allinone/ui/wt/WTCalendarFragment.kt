package com.example.allinone.ui.wt

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.adapters.EventAdapter
import com.example.allinone.data.WTEvent
import com.example.allinone.databinding.FragmentWtCalendarBinding
import com.example.allinone.databinding.DialogAddEventBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WTCalendarFragment : Fragment() {
    private var _binding: FragmentWtCalendarBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WTCalendarViewModel by viewModels()
    private lateinit var eventAdapter: EventAdapter
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWtCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // First initialize the event adapter
        setupEventsList()
        // Then observe events (which might call updateEventsForDate)
        observeEvents()
        // Observe network status
        observeNetworkStatus()
        // Finally set up the calendar (which will call updateEventsForDate)
        setupCalendar()
    }

    override fun onResume() {
        super.onResume()
        // Check and show network status on resume
        updateNetworkStatus()
        
        // Force reload data when returning to this fragment
        android.util.Log.d("WTCalendarFragment", "Fragment resumed, forcing data reload")
        viewModel.forceRefresh()
        
        // Refresh the current date's events
        val selectedDate = Calendar.getInstance().apply {
            timeInMillis = binding.calendarView.date
        }.time
        updateEventsForDate(selectedDate)
    }
    
    private fun setupCalendar() {
        // Setup calendar view
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
            }.time
            
            updateEventsForDate(selectedDate)
        }
        
        // Fix: Replace the CalendarView long click with a custom solution
        // CalendarView doesn't natively support long click events well
        binding.calendarView.setOnLongClickListener {
            val selectedDate = Calendar.getInstance().apply {
                timeInMillis = binding.calendarView.date
            }.time
            
            showAddEventDialog(selectedDate)
            true
        }
        
        // Add a separate button to add events
        binding.fabAddEvent.setOnClickListener {
            val selectedDate = Calendar.getInstance().apply {
                timeInMillis = binding.calendarView.date
            }.time
            
            showAddEventDialog(selectedDate)
        }
        
        // Initialize with current date
        val today = Calendar.getInstance().time
        updateEventsForDate(today)
    }
    
    private fun setupEventsList() {
        eventAdapter = EventAdapter(
            onItemClick = { event -> 
                if (event.type == "Lesson") {
                    showLessonManagementDialog(event)
                } else {
                    showEventDetailsDialog(event)
                }
            }
        )
        
        binding.eventsList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = eventAdapter
        }
    }
    
    private fun observeEvents() {
        viewModel.events.observe(viewLifecycleOwner) { events ->
            android.util.Log.d("WTCalendarFragment", "Events updated: ${events.size} events")
            
            // Initial load will update for current date
            val selectedDate = Calendar.getInstance().apply {
                timeInMillis = binding.calendarView.date
            }.time
            
            updateEventsForDate(selectedDate)
        }
        
        // Also observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!isLoading) {
                android.util.Log.d("WTCalendarFragment", "Loading completed")
            }
        }
    }
    
    private fun updateEventsForDate(date: Date) {
        // If the adapter is not initialized yet, just return
        if (!::eventAdapter.isInitialized) {
            return
        }
        
        val calendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val endCalendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        
        val startOfDay = calendar.time.time
        val endOfDay = endCalendar.time.time
        
        val events = viewModel.events.value?.filter { event ->
            val eventTime = event.date.time
            eventTime in startOfDay..endOfDay
        } ?: emptyList()
        
        eventAdapter.submitList(events.sortedBy { it.date })
        
        // Update date display and empty state
        binding.selectedDateText.text = dateFormat.format(date)
        binding.emptyStateText.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
    }
    
    private fun showAddEventDialog(date: Date) {
        val dialogBinding = DialogAddEventBinding.inflate(layoutInflater)
        
        // Set default date and time
        val calendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        
        dialogBinding.dateInput.setText(dateFormat.format(calendar.time))
        dialogBinding.timeInput.setText(timeFormat.format(calendar.time))
        
        // Setup date picker
        dialogBinding.dateInput.setOnClickListener {
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            
            DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                calendar.set(selectedYear, selectedMonth, selectedDayOfMonth)
                dialogBinding.dateInput.setText(dateFormat.format(calendar.time))
            }, year, month, day).show()
        }
        
        // Setup time picker
        dialogBinding.timeInput.setOnClickListener {
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            
            TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
                calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                calendar.set(Calendar.MINUTE, selectedMinute)
                dialogBinding.timeInput.setText(timeFormat.format(calendar.time))
            }, hour, minute, true).show()
        }
        
        // Show dialog
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Event")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val title = dialogBinding.titleInput.text.toString()
                val description = dialogBinding.descriptionInput.text.toString().takeIf { it.isNotBlank() }
                
                if (title.isBlank()) {
                    Toast.makeText(context, "Please enter a title", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                viewModel.addEvent(title, description, calendar.time)
                updateEventsForDate(calendar.time)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showEventDetailsDialog(event: WTEvent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(event.title)
            .setMessage("Date: ${dateFormat.format(event.date)}\nTime: ${timeFormat.format(event.date)}\n\n${event.description ?: ""}")
            .setPositiveButton("Close", null)
            .setNeutralButton("Delete") { _, _ ->
                viewModel.deleteEvent(event)
                updateEventsForDate(event.date)
            }
            .show()
    }
    
    private fun showLessonManagementDialog(event: WTEvent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Manage Lesson")
            .setMessage("Date: ${dateFormat.format(event.date)}\nTime: ${timeFormat.format(event.date)}")
            .setPositiveButton("Close", null)
            .setNegativeButton("Cancel Lesson") { _, _ ->
                showCancelLessonConfirmation(event)
            }
            .setNeutralButton("Postpone") { _, _ ->
                showPostponeLessonDialog(event)
            }
            .show()
    }
    
    private fun showCancelLessonConfirmation(event: WTEvent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancel Lesson")
            .setMessage("Are you sure you want to cancel this lesson? This will affect student subscription end dates.")
            .setPositiveButton("Yes, Cancel Lesson") { _, _ ->
                viewModel.cancelLesson(event.date)
                Toast.makeText(context, "Lesson cancelled", Toast.LENGTH_SHORT).show()
                updateEventsForDate(event.date)
            }
            .setNegativeButton("No", null)
            .show()
    }
    
    private fun showPostponeLessonDialog(event: WTEvent) {
        val calendar = Calendar.getInstance().apply {
            time = event.date
        }
        
        // Prepare new date picker (default to 1 week later)
        val postponeCalendar = Calendar.getInstance().apply {
            time = event.date
            add(Calendar.WEEK_OF_YEAR, 1)
        }
        
        val year = postponeCalendar.get(Calendar.YEAR)
        val month = postponeCalendar.get(Calendar.MONTH)
        val day = postponeCalendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        // Show date picker
        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDayOfMonth ->
            postponeCalendar.set(selectedYear, selectedMonth, selectedDayOfMonth)
            
            // Show time picker after date is selected
            TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
                postponeCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                postponeCalendar.set(Calendar.MINUTE, selectedMinute)
                
                // Postpone the lesson
                viewModel.postponeLesson(event.date, postponeCalendar.time)
                Toast.makeText(context, "Lesson postponed", Toast.LENGTH_SHORT).show()
                
                // Update calendar view to show the new date
                binding.calendarView.date = postponeCalendar.timeInMillis
                updateEventsForDate(postponeCalendar.time)
                
            }, hour, minute, true).show()
            
        }, year, month, day).show()
    }

    private fun observeNetworkStatus() {
        // Observe Firebase repository connection status
        viewModel.isNetworkAvailable.observe(viewLifecycleOwner) { isAvailable ->
            android.util.Log.d("WTCalendarFragment", "Network availability changed: $isAvailable")
            
            // Update with a slight delay to avoid false network status changes
            Handler(Looper.getMainLooper()).postDelayed({
                if (view != null && isAdded) {
                    binding.networkStatusText.visibility = if (isAvailable) View.GONE else View.VISIBLE
                    
                    if (!isAvailable) {
                        // If network becomes unavailable, show message
                        Toast.makeText(
                            context, 
                            "Network unavailable. Using cached data.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // When network becomes available, automatically refresh data
                        android.util.Log.d("WTCalendarFragment", "Network available, reloading data")
                        viewModel.forceRefresh()
                    }
                }
            }, 1000) // 1-second delay
        }
        
        // Observe Firebase error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message != null && message.isNotEmpty()) {
                android.util.Log.e("WTCalendarFragment", "Error message: $message")
                if (isAdded) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                
                // Clear the error message after showing it
                viewModel.clearErrorMessage()
            }
        }
    }

    /**
     * Updates the network status indicator with current connectivity state
     */
    private fun updateNetworkStatus() {
        // Add a slight delay to allow network state to stabilize
        Handler(Looper.getMainLooper()).postDelayed({
            if (view != null && isAdded) {
                val isAvailable = viewModel.isNetworkAvailable.value ?: false
                binding.networkStatusText.visibility = if (isAvailable) View.GONE else View.VISIBLE
                android.util.Log.d("WTCalendarFragment", "Network status updated: $isAvailable")
            }
        }, 500) // Half-second delay
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 