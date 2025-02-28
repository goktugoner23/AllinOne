package com.example.allinone.ui.wt

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
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
        setupEventsList()
        setupCalendar()
        observeEvents()
    }
    
    private fun setupCalendar() {
        // Setup calendar view
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
            }.time
            
            updateEventsForDate(selectedDate)
        }
        
        binding.calendarView.setOnLongClickListener {
            val selectedDate = Calendar.getInstance().apply {
                timeInMillis = binding.calendarView.date
            }.time
            
            showAddEventDialog(selectedDate)
            true
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
        
        binding.eventsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = eventAdapter
        }
    }
    
    private fun observeEvents() {
        viewModel.events.observe(viewLifecycleOwner) { _ ->
            // Initial load will update for current date
            val selectedDate = Calendar.getInstance().apply {
                timeInMillis = binding.calendarView.date
            }.time
            
            updateEventsForDate(selectedDate)
        }
    }
    
    private fun updateEventsForDate(date: Date) {
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
        
        if (::eventAdapter.isInitialized) {
            eventAdapter.submitList(events.sortedBy { it.date })
        }
        
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
            .setTitle("Wing Tzun Lesson")
            .setMessage("Date: ${dateFormat.format(event.date)}\nTime: ${timeFormat.format(event.date)}\n\n${event.description ?: ""}")
            .setPositiveButton("Close", null)
            .setNeutralButton("Cancel Lesson") { _, _ ->
                viewModel.cancelLesson(event.date)
                Toast.makeText(context, "Lesson canceled", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Postpone") { _, _ ->
                showPostponeLessonDialog(event)
            }
            .show()
    }
    
    private fun showPostponeLessonDialog(event: WTEvent) {
        val calendar = Calendar.getInstance().apply {
            time = event.date
        }
        
        val dialogBinding = DialogAddEventBinding.inflate(layoutInflater)
        dialogBinding.titleInput.visibility = View.GONE
        dialogBinding.descriptionInput.visibility = View.GONE
        
        // Set default date and time to event date + 1 week
        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        
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
            .setTitle("Postpone Lesson")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                viewModel.postponeLesson(event.date, calendar.time)
                Toast.makeText(context, "Lesson postponed", Toast.LENGTH_SHORT).show()
                updateEventsForDate(calendar.time)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 