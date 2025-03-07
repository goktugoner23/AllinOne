package com.example.allinone.ui.wt

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
import com.example.allinone.R
import com.example.allinone.data.WTLesson
import com.example.allinone.databinding.FragmentWtLessonsBinding
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.*

class WTLessonsFragment : Fragment() {
    private var _binding: FragmentWtLessonsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WTCalendarViewModel by viewModels()
    private val selectedDays = mutableSetOf<Int>()
    private var startTime: Calendar = Calendar.getInstance()
    private var endTime: Calendar = Calendar.getInstance()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWtLessonsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupDayChips()
        setupTimeButtons()
        setupSaveButton()
        observeScheduledLessons()
        observeNetworkStatus()
    }
    
    override fun onResume() {
        super.onResume()
        // Reload data when returning to this fragment
        updateNetworkStatus()
        
        // Force refresh of data
        android.util.Log.d("WTLessonsFragment", "Fragment resumed, forcing data refresh")
        viewModel.forceRefresh()
    }
    
    private fun reloadLessons() {
        // Force reload of lessons data
        viewModel.forceRefresh()
        android.util.Log.d("WTLessonsFragment", "Forcing refresh of lessons data")
    }
    
    private fun setupDayChips() {
        // Set up chips for day selection
        binding.mondayChip.setOnCheckedChangeListener { _, isChecked ->
            updateSelectedDays(Calendar.MONDAY, isChecked)
        }
        
        binding.tuesdayChip.setOnCheckedChangeListener { _, isChecked ->
            updateSelectedDays(Calendar.TUESDAY, isChecked)
        }
        
        binding.wednesdayChip.setOnCheckedChangeListener { _, isChecked ->
            updateSelectedDays(Calendar.WEDNESDAY, isChecked)
        }
        
        binding.thursdayChip.setOnCheckedChangeListener { _, isChecked ->
            updateSelectedDays(Calendar.THURSDAY, isChecked)
        }
        
        binding.fridayChip.setOnCheckedChangeListener { _, isChecked ->
            updateSelectedDays(Calendar.FRIDAY, isChecked)
        }
        
        binding.saturdayChip.setOnCheckedChangeListener { _, isChecked ->
            updateSelectedDays(Calendar.SATURDAY, isChecked)
        }
        
        binding.sundayChip.setOnCheckedChangeListener { _, isChecked ->
            updateSelectedDays(Calendar.SUNDAY, isChecked)
        }
    }
    
    private fun updateSelectedDays(day: Int, isSelected: Boolean) {
        if (isSelected) {
            selectedDays.add(day)
        } else {
            selectedDays.remove(day)
        }
    }
    
    private fun setupTimeButtons() {
        // Default times (20:30 - 22:00)
        startTime.set(Calendar.HOUR_OF_DAY, 20)
        startTime.set(Calendar.MINUTE, 30)
        endTime.set(Calendar.HOUR_OF_DAY, 22)
        endTime.set(Calendar.MINUTE, 0)
        
        updateTimeDisplay()
        
        binding.startTimeButton.setOnClickListener {
            showTimePickerDialog(true)
        }
        
        binding.endTimeButton.setOnClickListener {
            showTimePickerDialog(false)
        }
    }
    
    private fun showTimePickerDialog(isStartTime: Boolean) {
        val calendar = if (isStartTime) startTime else endTime
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                if (isStartTime) {
                    startTime.set(Calendar.HOUR_OF_DAY, selectedHour)
                    startTime.set(Calendar.MINUTE, selectedMinute)
                } else {
                    endTime.set(Calendar.HOUR_OF_DAY, selectedHour)
                    endTime.set(Calendar.MINUTE, selectedMinute)
                }
                updateTimeDisplay()
            },
            hour,
            minute,
            true
        ).show()
    }
    
    private fun updateTimeDisplay() {
        binding.startTimeButton.text = timeFormat.format(startTime.time)
        binding.endTimeButton.text = timeFormat.format(endTime.time)
    }
    
    private fun setupSaveButton() {
        binding.saveScheduleButton.setOnClickListener {
            if (selectedDays.isEmpty()) {
                Toast.makeText(context, "Please select at least one day", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Create lesson schedule
            val lessons = mutableListOf<WTLesson>()
            for (day in selectedDays) {
                lessons.add(
                    WTLesson(
                        dayOfWeek = day,
                        startHour = startTime.get(Calendar.HOUR_OF_DAY),
                        startMinute = startTime.get(Calendar.MINUTE),
                        endHour = endTime.get(Calendar.HOUR_OF_DAY),
                        endMinute = endTime.get(Calendar.MINUTE)
                    )
                )
            }
            
            // Save lessons to view model
            viewModel.setLessonSchedule(lessons)
            
            // Add events to calendar
            viewModel.generateCalendarEvents()
            
            Toast.makeText(context, "Lesson schedule saved", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun observeScheduledLessons() {
        viewModel.lessonSchedule.observe(viewLifecycleOwner) { lessons ->
            android.util.Log.d("WTLessonsFragment", "Lesson schedule updated with ${lessons.size} lessons")
            
            // Clear existing scheduled days
            binding.mondayChip.isChecked = false
            binding.tuesdayChip.isChecked = false
            binding.wednesdayChip.isChecked = false
            binding.thursdayChip.isChecked = false
            binding.fridayChip.isChecked = false
            binding.saturdayChip.isChecked = false
            binding.sundayChip.isChecked = false
            
            selectedDays.clear()
            
            if (lessons.isEmpty()) {
                binding.noLessonsText.visibility = View.VISIBLE
                binding.scheduledLessonsContainer.removeAllViews()
                return@observe
            }
            
            // Set the time from the first lesson (assuming all lessons have the same time)
            val firstLesson = lessons.first()
            startTime.set(Calendar.HOUR_OF_DAY, firstLesson.startHour)
            startTime.set(Calendar.MINUTE, firstLesson.startMinute)
            endTime.set(Calendar.HOUR_OF_DAY, firstLesson.endHour)
            endTime.set(Calendar.MINUTE, firstLesson.endMinute)
            updateTimeDisplay()
            
            // Update chips for scheduled days and collect days
            for (lesson in lessons) {
                when (lesson.dayOfWeek) {
                    Calendar.MONDAY -> binding.mondayChip.isChecked = true
                    Calendar.TUESDAY -> binding.tuesdayChip.isChecked = true
                    Calendar.WEDNESDAY -> binding.wednesdayChip.isChecked = true
                    Calendar.THURSDAY -> binding.thursdayChip.isChecked = true
                    Calendar.FRIDAY -> binding.fridayChip.isChecked = true
                    Calendar.SATURDAY -> binding.saturdayChip.isChecked = true
                    Calendar.SUNDAY -> binding.sundayChip.isChecked = true
                }
                selectedDays.add(lesson.dayOfWeek)
            }
            
            // Update scheduled lessons display
            updateScheduledLessonsDisplay(lessons)
        }
    }
    
    private fun updateScheduledLessonsDisplay(lessons: List<WTLesson>) {
        android.util.Log.d("WTLessonsFragment", "Updating scheduled lessons display with ${lessons.size} lessons")
        
        binding.scheduledLessonsContainer.removeAllViews()
        
        if (lessons.isEmpty()) {
            binding.noLessonsText.visibility = View.VISIBLE
            return
        }
        
        binding.noLessonsText.visibility = View.GONE
        
        // Sort lessons by day of week
        val sortedLessons = lessons.sortedBy { it.dayOfWeek }
        android.util.Log.d("WTLessonsFragment", "Sorted lessons: " + sortedLessons.map {
            when(it.dayOfWeek) {
                Calendar.MONDAY -> "Monday"
                Calendar.TUESDAY -> "Tuesday"
                Calendar.WEDNESDAY -> "Wednesday"
                Calendar.THURSDAY -> "Thursday"
                Calendar.FRIDAY -> "Friday"
                Calendar.SATURDAY -> "Saturday"
                Calendar.SUNDAY -> "Sunday"
                else -> "Unknown"
            }
        }.joinToString(", "))
        
        // Add each lesson day as a chip
        for (lesson in sortedLessons) {
            val dayName = when (lesson.dayOfWeek) {
                Calendar.MONDAY -> "Monday"
                Calendar.TUESDAY -> "Tuesday"
                Calendar.WEDNESDAY -> "Wednesday"
                Calendar.THURSDAY -> "Thursday"
                Calendar.FRIDAY -> "Friday"
                Calendar.SATURDAY -> "Saturday"
                Calendar.SUNDAY -> "Sunday"
                else -> "Unknown"
            }
            
            val timeText = "${lesson.startHour}:${lesson.startMinute.toString().padStart(2, '0')} - " +
                    "${lesson.endHour}:${lesson.endMinute.toString().padStart(2, '0')}"
            
            val chip = Chip(requireContext()).apply {
                text = "$dayName: $timeText"
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    showDeleteLessonConfirmation(lesson)
                }
            }
            
            binding.scheduledLessonsContainer.addView(chip)
            android.util.Log.d("WTLessonsFragment", "Added chip for $dayName")
        }
    }
    
    private fun showDeleteLessonConfirmation(lesson: WTLesson) {
        // Create dialog to confirm deletion
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Lesson")
            .setMessage("Are you sure you want to delete this lesson?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.removeLesson(lesson)
                Toast.makeText(context, "Lesson removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun observeNetworkStatus() {
        // Observe Firebase repository connection status
        viewModel.isNetworkAvailable.observe(viewLifecycleOwner) { isAvailable ->
            android.util.Log.d("WTLessonsFragment", "Network availability changed: $isAvailable")
            
            // Update the network status indicator with a short delay to avoid false-positives
            Handler(Looper.getMainLooper()).postDelayed({
                // Check if the view is still attached to avoid crashes
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
                        android.util.Log.d("WTLessonsFragment", "Network available, reloading data")
                        viewModel.forceRefresh()
                    }
                }
            }, 1000) // 1-second delay to avoid false network status changes
        }
        
        // Observe Firebase error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message != null && message.isNotEmpty()) {
                android.util.Log.e("WTLessonsFragment", "Error message: $message")
                if (isAdded) { // Check if fragment is still attached
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                
                // Clear the error message after showing it
                viewModel.clearErrorMessage()
            }
        }
    }
    
    /**
     * Updates the network status with current connectivity state
     */
    private fun updateNetworkStatus() {
        // Add a slight delay to allow network state to stabilize
        Handler(Looper.getMainLooper()).postDelayed({
            if (view != null && isAdded) {
                val isAvailable = viewModel.isNetworkAvailable.value ?: false
                // Update the network status indicator
                binding.networkStatusText.visibility = if (isAvailable) View.GONE else View.VISIBLE
                android.util.Log.d("WTLessonsFragment", "Network status updated: $isAvailable")
                
                if (!isAvailable) {
                    android.util.Log.d("WTLessonsFragment", "Network unavailable on fragment resume")
                    Toast.makeText(
                        context,
                        "Network unavailable. Using cached data.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }, 500) // Half-second delay
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 