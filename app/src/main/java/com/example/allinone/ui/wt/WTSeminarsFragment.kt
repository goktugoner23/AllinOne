package com.example.allinone.ui.wt

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.SeminarAdapter
import com.example.allinone.data.WTSeminar
import com.example.allinone.viewmodels.WTSeminarsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.allinone.databinding.DialogEditSeminarBinding
import com.example.allinone.databinding.FragmentWtSeminarsBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Fragment for displaying Wing Tzun seminars
 */
class WTSeminarsFragment : Fragment() {
    private var _binding: FragmentWtSeminarsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: WTSeminarsViewModel by viewModels()
    private lateinit var adapter: SeminarAdapter
    
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    // Calendar for date and time selection
    private val calendar = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWtSeminarsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Setup FAB
        binding.addSeminarFab.setOnClickListener {
            showAddSeminarDialog()
        }
        
        // Observe data
        observeViewModel()
    }
    
    private fun setupRecyclerView() {
        adapter = SeminarAdapter(
            onShareClick = { seminar -> shareSeminar(seminar) },
            onItemClick = { seminar -> showSeminarDetails(seminar) }
        )
        
        binding.seminarsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WTSeminarsFragment.adapter
        }
    }
    
    private fun observeViewModel() {
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        // Observe seminars
        viewModel.seminars.observe(viewLifecycleOwner) { _ ->
            val upcomingSeminars = viewModel.getUpcomingSeminars()
            adapter.submitList(upcomingSeminars)
            
            // Show/hide empty state
            if (upcomingSeminars.isEmpty()) {
                binding.emptyStateText.visibility = View.VISIBLE
                binding.seminarsRecyclerView.visibility = View.GONE
            } else {
                binding.emptyStateText.visibility = View.GONE
                binding.seminarsRecyclerView.visibility = View.VISIBLE
            }
        }
    }
    
    private fun showAddSeminarDialog() {
        val dialogBinding = DialogEditSeminarBinding.inflate(layoutInflater)
        
        // Set default date to today
        calendar.time = Date()
        dialogBinding.dateInput.setText(dateFormat.format(calendar.time))
        
        // Set default time (e.g., 9:00 AM)
        calendar.set(Calendar.HOUR_OF_DAY, 9)
        calendar.set(Calendar.MINUTE, 0)
        dialogBinding.startTimeInput.setText(timeFormat.format(calendar.time))
        
        // Set default end time (4 hours later)
        calendar.add(Calendar.HOUR_OF_DAY, 4)
        dialogBinding.endTimeInput.setText(timeFormat.format(calendar.time))
        calendar.add(Calendar.HOUR_OF_DAY, -4) // Reset for future use
        
        // Set up date picker
        dialogBinding.dateInput.setOnClickListener {
            showDatePicker(dialogBinding)
        }
        dialogBinding.dateLayout.setEndIconOnClickListener {
            showDatePicker(dialogBinding)
        }
        
        // Set up time pickers
        dialogBinding.startTimeInput.setOnClickListener {
            showStartTimePicker(dialogBinding)
        }
        dialogBinding.startTimeLayout.setEndIconOnClickListener {
            showStartTimePicker(dialogBinding)
        }
        
        dialogBinding.endTimeInput.setOnClickListener {
            showEndTimePicker(dialogBinding)
        }
        dialogBinding.endTimeLayout.setEndIconOnClickListener {
            showEndTimePicker(dialogBinding)
        }
        
        // Create dialog
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Seminar")
            .setView(dialogBinding.root)
            .setPositiveButton("Save", null) // We'll set this in a moment
            .setNegativeButton("Cancel", null)
            .create()
        
        // Show dialog and set the positive button click listener
        dialog.show()
        
        // Override the positive button to validate input before dismissing
        dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
            val name = dialogBinding.nameInput.text.toString().trim()
            val dateStr = dialogBinding.dateInput.text.toString()
            val startTimeStr = dialogBinding.startTimeInput.text.toString()
            val endTimeStr = dialogBinding.endTimeInput.text.toString()
            val description = dialogBinding.descriptionInput.text.toString().trim()
            
            // Validate input
            if (name.isEmpty() || dateStr.isEmpty() || startTimeStr.isEmpty() || endTimeStr.isEmpty()) {
                Toast.makeText(context, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Parse times
            val startTimeParts = startTimeStr.split(":")
            val endTimeParts = endTimeStr.split(":")
            
            if (startTimeParts.size != 2 || endTimeParts.size != 2) {
                Toast.makeText(context, "Invalid time format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val startHour = startTimeParts[0].toInt()
            val startMinute = startTimeParts[1].toInt()
            val endHour = endTimeParts[0].toInt()
            val endMinute = endTimeParts[1].toInt()
            
            // Create seminar object
            val seminar = WTSeminar(
                name = name,
                date = calendar.time, // We've already set this to the selected date
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                description = if (description.isNotEmpty()) description else null
            )
            
            // Add seminar
            viewModel.addSeminar(seminar)
            
            // Dismiss dialog
            dialog.dismiss()
            
            // Show confirmation
            Toast.makeText(context, "Seminar added", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showDatePicker(dialogBinding: DialogEditSeminarBinding) {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        
        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            calendar.set(selectedYear, selectedMonth, selectedDay)
            dialogBinding.dateInput.setText(dateFormat.format(calendar.time))
        }, year, month, day).show()
    }
    
    private fun showStartTimePicker(dialogBinding: DialogEditSeminarBinding) {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
            // Set the selected start time
            calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
            calendar.set(Calendar.MINUTE, selectedMinute)
            
            // Update start time field
            dialogBinding.startTimeInput.setText(timeFormat.format(calendar.time))
            
            // Calculate and set end time (4 hours later)
            calendar.add(Calendar.HOUR_OF_DAY, 4)
            dialogBinding.endTimeInput.setText(timeFormat.format(calendar.time))
            
            // Reset calendar to start time for future calculations
            calendar.add(Calendar.HOUR_OF_DAY, -4)
        }, hour, minute, true).show()
    }
    
    private fun showEndTimePicker(dialogBinding: DialogEditSeminarBinding) {
        // Get current end time values
        val endTimeStr = dialogBinding.endTimeInput.text.toString()
        val parts = endTimeStr.split(":")
        
        // Default to 4 hours after start time if parsing fails
        val hour = if (parts.size >= 2) parts[0].toIntOrNull() ?: 13 else 13
        val minute = if (parts.size >= 2) parts[1].toIntOrNull() ?: 0 else 0
        
        TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
            // Update the end time field
            val tempCal = Calendar.getInstance()
            tempCal.set(Calendar.HOUR_OF_DAY, selectedHour)
            tempCal.set(Calendar.MINUTE, selectedMinute)
            dialogBinding.endTimeInput.setText(timeFormat.format(tempCal.time))
        }, hour, minute, true).show()
    }
    
    private fun showSeminarDetails(seminar: WTSeminar) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(seminar.name)
            .setMessage(
                "Date: ${dateFormat.format(seminar.date)}\n" +
                "Time: ${viewModel.formatTime(seminar.startHour, seminar.startMinute)} - " +
                        "${viewModel.formatTime(seminar.endHour, seminar.endMinute)}\n" +
                (if (seminar.description != null) "\nDescription: ${seminar.description}" else "")
            )
            .setPositiveButton("Close", null)
            .setNegativeButton("Delete") { _, _ -> confirmDeleteSeminar(seminar) }
            .show()
    }
    
    private fun confirmDeleteSeminar(seminar: WTSeminar) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Seminar")
            .setMessage("Are you sure you want to delete this seminar?\n\n${seminar.name}")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSeminar(seminar)
                Toast.makeText(context, "Seminar deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun shareSeminar(seminar: WTSeminar) {
        val shareText = "Wing Tzun Seminar: ${seminar.name}\n" +
                "Date: ${dateFormat.format(seminar.date)}\n" +
                "Time: ${viewModel.formatTime(seminar.startHour, seminar.startMinute)} - " +
                "${viewModel.formatTime(seminar.endHour, seminar.endMinute)}" +
                (if (seminar.description != null) "\n\nDescription: ${seminar.description}" else "")
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Wing Tzun Seminar: ${seminar.name}")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        
        startActivity(Intent.createChooser(shareIntent, "Share Seminar Details"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    override fun onResume() {
        super.onResume()
        // Force refresh data when fragment becomes visible
        viewModel.refreshSeminars()
    }
} 