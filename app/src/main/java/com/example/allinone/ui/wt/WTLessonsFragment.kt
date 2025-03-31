package com.example.allinone.ui.wt

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.allinone.R
import com.example.allinone.data.WTLesson
import com.example.allinone.databinding.FragmentWtLessonsBinding
import com.example.allinone.databinding.ItemLessonBinding
import com.example.allinone.viewmodels.WTLessonsViewModel
import com.google.android.material.chip.Chip
import java.util.Calendar

class WTLessonsFragment : Fragment() {
    private var _binding: FragmentWtLessonsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WTLessonsViewModel by viewModels()
    
    // Day chips
    private val dayChips = mutableMapOf<Int, Chip>()
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWtLessonsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set up day chips
        setupDayChips()
        
        // Configure time input fields
        setupTimeInputFields(binding.startTimeField)
        setupTimeInputFields(binding.endTimeField)
        
        // Set default values for time fields
        binding.startTimeField.setText("18:00")
        binding.endTimeField.setText("19:30")
        
        // Set up add lesson button
        binding.addLessonButton.setOnClickListener {
            addLessons()
        }
        
        // Set up loading indicator
        binding.progressBar.visibility = View.VISIBLE
        
        // Observe lessons
        viewModel.lessons.observe(viewLifecycleOwner) { lessons ->
            binding.progressBar.visibility = View.GONE
            updateLessonsList(lessons)
        }
        
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        // Observe network availability
        viewModel.isNetworkAvailable.observe(viewLifecycleOwner) { isAvailable ->
            // Refresh data if network becomes available
            if (isAvailable) {
                viewModel.refreshData()
            }
        }
        
        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage()
            }
        }
        
        // Observe currently editing lesson
        viewModel.currentEditingLesson.observe(viewLifecycleOwner) { lesson ->
            lesson?.let {
                showEditDialog(it)
            }
        }
        
        // Initially load lessons
        viewModel.refreshData()
    }

    private fun setupTimeInputFields(editText: EditText) {
        // Set input type to show numeric keyboard
        editText.inputType = InputType.TYPE_CLASS_NUMBER
        
        // Add automatic formatting for time entry
        editText.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable) {
                if (isFormatting) return
                
                isFormatting = true
                
                // Handle time formatting (HH:MM)
                val text = s.toString().replace(":", "")
                if (text.length >= 3) {
                    // Format as HH:MM
                    val hour = text.substring(0, 2)
                    val minute = text.substring(2, minOf(text.length, 4))
                    s.replace(0, s.length, "$hour:$minute")
                } else if (text.length == 2) {
                    // Add colon after hours
                    s.replace(0, s.length, "$text:")
                }
                
                isFormatting = false
            }
        })
    }
    
    private fun setupDayChips() {
        // Map days of week to chips
        dayChips[Calendar.MONDAY] = binding.mondayChip
        dayChips[Calendar.TUESDAY] = binding.tuesdayChip
        dayChips[Calendar.WEDNESDAY] = binding.wednesdayChip
        dayChips[Calendar.THURSDAY] = binding.thursdayChip
        dayChips[Calendar.FRIDAY] = binding.fridayChip
        dayChips[Calendar.SATURDAY] = binding.saturdayChip
        dayChips[Calendar.SUNDAY] = binding.sundayChip
    }
    
    private fun parseTimeField(timeString: String): Pair<Int, Int>? {
        try {
            if (!timeString.contains(":")) {
                // Try to handle 4-digit input without colon (like "1830")
                if (timeString.length == 4) {
                    val hour = timeString.substring(0, 2).toInt()
                    val minute = timeString.substring(2, 4).toInt()
                    if (hour in 0..23 && minute in 0..59) {
                        return Pair(hour, minute)
                    }
                }
                return null
            }
            
            val parts = timeString.split(":")
            if (parts.size != 2) return null
            
            val hour = parts[0].trim().toInt()
            val minute = parts[1].trim().toInt()
            
            if (hour in 0..23 && minute in 0..59) {
                return Pair(hour, minute)
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun addLessons() {
        // Parse time input
        val startTimeString = binding.startTimeField.text.toString()
        val endTimeString = binding.endTimeField.text.toString()
        
        if (TextUtils.isEmpty(startTimeString) || TextUtils.isEmpty(endTimeString)) {
            Toast.makeText(requireContext(), "Please enter valid time values", Toast.LENGTH_SHORT).show()
            return
        }
        
        val startTime = parseTimeField(startTimeString)
        val endTime = parseTimeField(endTimeString)
        
        if (startTime == null || endTime == null) {
            Toast.makeText(requireContext(), "Please enter time in format HH:MM", Toast.LENGTH_SHORT).show()
            return
        }
        
        val (startHour, startMinute) = startTime
        val (endHour, endMinute) = endTime
        
        // Validate time values
        if (endHour < startHour || (endHour == startHour && endMinute <= startMinute)) {
            Toast.makeText(requireContext(), "End time must be after start time", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if at least one day is selected
        val selectedDays = dayChips.filter { it.value.isChecked }.keys
        if (selectedDays.isEmpty()) {
            Toast.makeText(requireContext(), "Please select at least one day", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Add lessons for each selected day
        for (day in selectedDays) {
            viewModel.addLesson(
                day,
                startHour,
                startMinute,
                endHour,
                endMinute
            )
        }
        
        // Clear selections
        clearSelections()
        
        // Save lessons to ensure the calendar is updated
        onSaveLessonsClick()
        
        // Show confirmation
        Toast.makeText(requireContext(), "Lessons added successfully", Toast.LENGTH_SHORT).show()
    }
    
    private fun clearSelections() {
        // Clear day selections
        for (chip in dayChips.values) {
            chip.isChecked = false
        }
        
        // Reset time fields to default values
        binding.startTimeField.setText("18:00")
        binding.endTimeField.setText("19:30")
    }
    
    private fun updateLessonsList(lessons: List<WTLesson>) {
        binding.lessonsContainer.removeAllViews()
        
        if (lessons.isEmpty()) {
            binding.noLessonsText.visibility = View.VISIBLE
        } else {
            binding.noLessonsText.visibility = View.GONE
            
            // Sort lessons by day of week and then by start time
            val sortedLessons = lessons.sortedWith(compareBy<WTLesson> { it.dayOfWeek }
                .thenBy { it.startHour }
                .thenBy { it.startMinute })
            
            for (lesson in sortedLessons) {
                addLessonCard(lesson)
            }
        }
    }
    
    private fun addLessonCard(lesson: WTLesson) {
        val lessonBinding = ItemLessonBinding.inflate(layoutInflater, binding.lessonsContainer, false)
        
        // Set lesson details
        lessonBinding.dayText.text = viewModel.getDayName(lesson.dayOfWeek)
        
        val timeText = "${viewModel.formatTime(lesson.startHour, lesson.startMinute)} - " +
                "${viewModel.formatTime(lesson.endHour, lesson.endMinute)}"
        lessonBinding.timeText.text = timeText
        
        // Set up edit button
        lessonBinding.editButton.setOnClickListener {
            viewModel.setEditingLesson(lesson)
        }
        
        // Set up delete button
        lessonBinding.deleteButton.setOnClickListener {
            showDeleteConfirmation(lesson)
        }
        
        binding.lessonsContainer.addView(lessonBinding.root)
    }
    
    private fun showDeleteConfirmation(lesson: WTLesson) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Lesson")
            .setMessage("Are you sure you want to delete this lesson?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteLesson(lesson)
                // After deletion, save changes to update the calendar
                onSaveLessonsClick()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showEditDialog(lesson: WTLesson) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_lesson, null)
        
        // Find views
        val dayText = dialogView.findViewById<TextView>(R.id.dayText)
        val startTimeField = dialogView.findViewById<EditText>(R.id.startTimeField)
        val endTimeField = dialogView.findViewById<EditText>(R.id.endTimeField)
        
        // Configure time fields
        setupTimeInputFields(startTimeField)
        setupTimeInputFields(endTimeField)
        
        // Set current values
        dayText.text = viewModel.getDayName(lesson.dayOfWeek)
        startTimeField.setText(viewModel.formatTime(lesson.startHour, lesson.startMinute))
        endTimeField.setText(viewModel.formatTime(lesson.endHour, lesson.endMinute))
        
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Lesson")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                // Parse time input
                val startTimeString = startTimeField.text.toString()
                val endTimeString = endTimeField.text.toString()
                
                val startTime = parseTimeField(startTimeString)
                val endTime = parseTimeField(endTimeString)
                
                if (startTime == null || endTime == null) {
                    Toast.makeText(requireContext(), "Please enter time in format HH:MM", Toast.LENGTH_SHORT).show()
                    viewModel.setEditingLesson(null)
                    return@setPositiveButton
                }
                
                val (startHour, startMinute) = startTime
                val (endHour, endMinute) = endTime
                
                // Validate time values
                if (endHour < startHour || (endHour == startHour && endMinute <= startMinute)) {
                    Toast.makeText(requireContext(), "End time must be after start time", Toast.LENGTH_SHORT).show()
                    viewModel.setEditingLesson(null)
                    return@setPositiveButton
                }
                
                // Update lesson
                viewModel.updateCurrentLesson(
                    lesson.dayOfWeek,
                    startHour,
                    startMinute,
                    endHour,
                    endMinute
                )
                
                // After editing, save changes to update the calendar
                onSaveLessonsClick()
            }
            .setNegativeButton("Cancel") { _, _ ->
                viewModel.setEditingLesson(null)
            }
            .setOnCancelListener {
                viewModel.setEditingLesson(null)
            }
            .show()
    }
    
    /**
     * Get the current lessons from the ViewModel
     * Note: This method name is misleading as it doesn't actually use the chip selections.
     * It simply returns the current lessons from the ViewModel.
     */
    private fun getLessonsFromChips(): List<WTLesson> {
        return viewModel.lessons.value ?: emptyList()
    }
    
    private fun onSaveLessonsClick() {
        val lessons = getLessonsFromChips()
        viewModel.saveLessons(lessons)
        
        // Also update the register view model to update the calendar
        val parentFragment = parentFragment
        if (parentFragment is WTRegistryFragment) {
            parentFragment.updateLessons(lessons)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        
        // Force refresh the lessons data from Firebase when the fragment becomes visible
        viewModel.refreshData()
        
        // Log for debugging
        Log.d("WTLessonsFragment", "onResume: Refreshing lessons data")
    }
} 