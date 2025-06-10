package com.example.allinone.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.ScheduleAdapter
import com.example.allinone.data.Schedule
import com.example.allinone.databinding.FragmentScheduleBinding
import com.example.allinone.viewmodels.ScheduleViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ScheduleFragment : Fragment() {
    
    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: ScheduleViewModel
    private lateinit var scheduleAdapter: ScheduleAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[ScheduleViewModel::class.java]
        
        setupRecyclerView()
        setupCategorySpinner()
        setupObservers()
        setupClickListeners()
        
        // Load schedules
        viewModel.loadSchedules()
    }
    
    private fun setupRecyclerView() {
        scheduleAdapter = ScheduleAdapter(
            onScheduleClick = { schedule ->
                showScheduleDetailsDialog(schedule)
            },
            onScheduleEdit = { schedule ->
                showAddEditScheduleDialog(schedule)
            },
            onScheduleDelete = { schedule ->
                showDeleteConfirmationDialog(schedule)
            },
            onScheduleToggle = { schedule ->
                viewModel.toggleScheduleEnabled(schedule)
            }
        )
        
        binding.schedulesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scheduleAdapter
        }
    }
    
    private fun setupCategorySpinner() {
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            viewModel.categories
        )
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        binding.categorySpinner.adapter = categoryAdapter
        
        binding.categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCategory = viewModel.categories[position]
                viewModel.setSelectedCategory(selectedCategory)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupObservers() {
        // Observe schedules
        viewModel.schedules.observe(viewLifecycleOwner) { schedules ->
            scheduleAdapter.submitList(schedules)
            
            // Show/hide empty state
            if (schedules.isEmpty()) {
                binding.emptyStateLayout.visibility = View.VISIBLE
                binding.schedulesRecyclerView.visibility = View.GONE
                binding.nextScheduleCard.visibility = View.GONE
            } else {
                binding.emptyStateLayout.visibility = View.GONE
                binding.schedulesRecyclerView.visibility = View.VISIBLE
                
                // Update next schedule (but keep card hidden for now)
                updateNextSchedule()
            }
        }
        
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        // Observe errors
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage()
            }
        }
        
        // Observe selected category
        viewModel.selectedCategory.observe(viewLifecycleOwner) { category ->
            val position = viewModel.categories.indexOf(category)
            if (position >= 0 && binding.categorySpinner.selectedItemPosition != position) {
                binding.categorySpinner.setSelection(position)
            }
        }
        
        // Observe show enabled only
        viewModel.showEnabledOnly.observe(viewLifecycleOwner) { showEnabledOnly ->
            binding.enabledOnlySwitch.isChecked = showEnabledOnly
        }
    }
    
    private fun setupClickListeners() {
        // Add schedule button
        binding.addScheduleButton.setOnClickListener {
            showAddEditScheduleDialog(null)
        }
        
        // Enabled only switch
        binding.enabledOnlySwitch.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleShowEnabledOnly()
        }
    }
    
    private fun updateNextSchedule() {
        val nextSchedule = viewModel.getNextSchedule()
        if (nextSchedule != null) {
            binding.nextScheduleText.text = "${nextSchedule.title} at ${nextSchedule.getFormattedTime()}"
            binding.nextScheduleCard.visibility = View.VISIBLE
        } else {
            binding.nextScheduleCard.visibility = View.GONE
        }
    }
    
    private fun showAddEditScheduleDialog(schedule: Schedule?) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_edit_schedule, null)
        
        // Get dialog views
        val titleEdit = dialogView.findViewById<EditText>(R.id.titleEdit)
        val descriptionEdit = dialogView.findViewById<EditText>(R.id.descriptionEdit)
        val timeButton = dialogView.findViewById<View>(R.id.timeButton)
        val timeText = dialogView.findViewById<android.widget.TextView>(R.id.timeText)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.categorySpinnerDialog)
        val colorSpinner = dialogView.findViewById<Spinner>(R.id.colorSpinner)
        val daysContainer = dialogView.findViewById<LinearLayout>(R.id.daysContainer)
        
        // Set up category spinner
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            viewModel.categories.filter { it != "All" }
        )
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = categoryAdapter
        
        // Set up color spinner
        val colorNames = listOf("Green", "Blue", "Orange", "Purple", "Red", "Blue Grey", "Brown", "Teal", "Indigo", "Yellow")
        val colorAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, colorNames)
        colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        colorSpinner.adapter = colorAdapter
        
        // Set up days checkboxes
        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val dayCheckBoxes = mutableListOf<CheckBox>()
        
        dayNames.forEachIndexed { index, dayName ->
            val checkBox = CheckBox(requireContext())
            checkBox.text = dayName
            checkBox.tag = index + 1 // 1=Monday, 7=Sunday
            dayCheckBoxes.add(checkBox)
            daysContainer.addView(checkBox)
        }
        
        // Variables for time selection
        var selectedHour = 9
        var selectedMinute = 0
        
        // If editing, populate fields
        if (schedule != null) {
            titleEdit.setText(schedule.title)
            descriptionEdit.setText(schedule.description ?: "")
            selectedHour = schedule.hour
            selectedMinute = schedule.minute
            timeText.text = schedule.getFormattedTime()
            
            // Set category
            val categoryIndex = viewModel.categories.filter { it != "All" }.indexOf(schedule.category)
            if (categoryIndex >= 0) {
                categorySpinner.setSelection(categoryIndex)
            }
            
            // Set color
            val colorIndex = viewModel.availableColors.indexOf(schedule.color)
            if (colorIndex >= 0) {
                colorSpinner.setSelection(colorIndex)
            }
            
            // Set days
            val enabledDays = schedule.getEnabledDays()
            dayCheckBoxes.forEach { checkBox ->
                val dayValue = checkBox.tag as Int
                checkBox.isChecked = enabledDays.contains(dayValue)
            }
        } else {
            timeText.text = String.format("%02d:%02d", selectedHour, selectedMinute)
            // Check weekdays by default
            dayCheckBoxes.take(5).forEach { it.isChecked = true }
        }
        
        // Time picker
        timeButton.setOnClickListener {
            val timePickerDialog = TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    selectedHour = hourOfDay
                    selectedMinute = minute
                    timeText.text = String.format("%02d:%02d", selectedHour, selectedMinute)
                },
                selectedHour,
                selectedMinute,
                true
            )
            timePickerDialog.show()
        }
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (schedule == null) "Add Schedule" else "Edit Schedule")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val title = titleEdit.text.toString().trim()
                val description = descriptionEdit.text.toString().trim().takeIf { it.isNotEmpty() }
                
                if (title.isEmpty()) {
                    Toast.makeText(context, "Please enter a title", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Get selected days
                val selectedDays = dayCheckBoxes
                    .filter { it.isChecked }
                    .map { it.tag as Int }
                    .sorted()
                    .joinToString(",")
                
                if (selectedDays.isEmpty()) {
                    Toast.makeText(context, "Please select at least one day", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val selectedCategory = viewModel.categories.filter { it != "All" }[categorySpinner.selectedItemPosition]
                val selectedColor = viewModel.availableColors[colorSpinner.selectedItemPosition]
                
                if (schedule == null) {
                    // Add new schedule
                    viewModel.addSchedule(
                        title = title,
                        description = description,
                        hour = selectedHour,
                        minute = selectedMinute,
                        daysOfWeek = selectedDays,
                        category = selectedCategory,
                        color = selectedColor
                    )
                    Toast.makeText(context, "Schedule added", Toast.LENGTH_SHORT).show()
                } else {
                    // Update existing schedule
                    val updatedSchedule = schedule.copy(
                        title = title,
                        description = description,
                        hour = selectedHour,
                        minute = selectedMinute,
                        daysOfWeek = selectedDays,
                        category = selectedCategory,
                        color = selectedColor
                    )
                    viewModel.updateSchedule(updatedSchedule)
                    Toast.makeText(context, "Schedule updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    private fun showScheduleDetailsDialog(schedule: Schedule) {
        val details = buildString {
            append("Title: ${schedule.title}\n")
            append("Time: ${schedule.getFormattedTime()}\n")
            
            if (!schedule.description.isNullOrBlank()) {
                append("Description: ${schedule.description}\n")
            }
            
            append("Category: ${schedule.category}\n")
            append("Days: ${schedule.getDayNames()}\n")
            append("Status: ${if (schedule.isEnabled) "Enabled" else "Disabled"}")
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Schedule Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showDeleteConfirmationDialog(schedule: Schedule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Schedule")
            .setMessage("Are you sure you want to delete '${schedule.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSchedule(schedule)
                Toast.makeText(context, "Schedule deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 