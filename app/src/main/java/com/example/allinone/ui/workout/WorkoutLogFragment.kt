package com.example.allinone.ui.workout

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.config.MuscleGroups
import com.example.allinone.data.Workout
import com.example.allinone.databinding.FragmentWorkoutLogBinding
import com.example.allinone.ui.workout.adapters.WorkoutExerciseAdapter
import com.example.allinone.ui.workout.adapters.WorkoutLogAdapter
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class WorkoutLogFragment : Fragment() {
    private var _binding: FragmentWorkoutLogBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WorkoutViewModel by viewModels()

    private var allWorkouts: List<Workout> = emptyList()
    private var filteredWorkouts: List<Workout> = emptyList()
    private var selectedMuscleGroup: String? = null
    private var selectedSortOption: String? = null
    
    private val sortOptions = arrayOf(
        "Date (Newest First)", 
        "Date (Oldest First)", 
        "Duration (Longest First)", 
        "Duration (Shortest First)"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkoutLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    private lateinit var workoutLogAdapter: WorkoutLogAdapter
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up filter UI
        setupFilters()

        // Set up RecyclerView
        binding.workoutLogRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initialize adapter
        workoutLogAdapter = WorkoutLogAdapter(emptyList()) { workout ->
            // Handle workout click - show workout details
            showWorkoutDetailsDialog(workout)
        }
        binding.workoutLogRecyclerView.adapter = workoutLogAdapter

        // Apply filter button
        binding.applyFiltersButton.setOnClickListener {
            applyFilters()
        }

        // Observe workouts data
        viewModel.allWorkouts.observe(viewLifecycleOwner) { workouts ->
            allWorkouts = workouts
            applyFilters()
        }
    }
    
    private fun setupFilters() {
        // Set up muscle group filter
        val muscleGroupItems = listOf("All Muscle Groups") + MuscleGroups.MUSCLE_GROUPS.toList()
        val muscleGroupAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            muscleGroupItems
        )
        
        val muscleGroupDropdown = binding.filterMuscleGroup
        muscleGroupDropdown.setAdapter(muscleGroupAdapter)
        muscleGroupDropdown.setText(muscleGroupItems[0], false)
        
        // Set up sort options
        val sortAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            sortOptions
        )
        
        val sortDropdown = binding.sortOption
        sortDropdown.setAdapter(sortAdapter)
        sortDropdown.setText(sortOptions[0], false)
    }
    
    private fun applyFilters() {
        if (allWorkouts.isEmpty()) {
            updateEmptyState(true)
            return
        }
        
        // Get selected filter values
        selectedMuscleGroup = binding.filterMuscleGroup.text.toString()
        selectedSortOption = binding.sortOption.text.toString()
        
        // Apply muscle group filter
        filteredWorkouts = if (selectedMuscleGroup == "All Muscle Groups" || selectedMuscleGroup.isNullOrEmpty()) {
            allWorkouts
        } else {
            allWorkouts.filter { workout ->
                workout.exercises.any { exercise ->
                    // Try to find the muscle group in an exercise's property
                    // This assumes you have a way to get the muscle group from an exercise
                    exercise.exerciseName.contains(selectedMuscleGroup!!, ignoreCase = true)
                }
            }
        }
        
        // Apply sorting
        filteredWorkouts = when (selectedSortOption) {
            "Date (Oldest First)" -> filteredWorkouts.sortedBy { it.startTime }
            "Duration (Longest First)" -> filteredWorkouts.sortedByDescending { it.duration }
            "Duration (Shortest First)" -> filteredWorkouts.sortedBy { it.duration }
            else -> filteredWorkouts.sortedByDescending { it.startTime } // Default is newest first
        }
        
        // Update RecyclerView
        workoutLogAdapter.updateWorkouts(filteredWorkouts)
        updateEmptyState(filteredWorkouts.isEmpty())
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyLogText.visibility = View.VISIBLE
            binding.workoutLogRecyclerView.visibility = View.GONE
        } else {
            binding.emptyLogText.visibility = View.GONE
            binding.workoutLogRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showWorkoutDetailsDialog(workout: Workout) {
        // Create a dialog to show workout details
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_workout_details, null)

        val workoutNameText = dialogView.findViewById<TextView>(R.id.workout_name_text)
        val workoutDateText = dialogView.findViewById<TextView>(R.id.workout_date_text)
        val workoutDurationText = dialogView.findViewById<TextView>(R.id.workout_duration_text)
        val exercisesRecyclerView = dialogView.findViewById<RecyclerView>(R.id.exercises_recycler_view)

        // Set workout details
        workoutNameText.text = workout.programName ?: "Custom Workout"
        workoutDateText.text = dateFormat.format(workout.startTime)
        workoutDurationText.text = formatDuration(workout.duration)

        // Set up exercises RecyclerView
        exercisesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        val exerciseAdapter = WorkoutExerciseAdapter(workout.exercises) { _, _ ->
            // This is just for viewing, so we don't need to handle completion changes
        }
        exercisesRecyclerView.adapter = exerciseAdapter

        // Show dialog
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.workout_details)
            .setView(dialogView)
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.delete) { _, _ ->
                confirmDeleteWorkout(workout)
            }
            .show()
    }

    private fun confirmDeleteWorkout(workout: Workout) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_workout)
            .setMessage(getString(R.string.delete_workout_confirmation, dateFormat.format(workout.startTime)))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteWorkout(workout.id)
                Toast.makeText(requireContext(), R.string.workout_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun formatDuration(durationMs: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)

        return when {
            hours > 0 -> "$hours hr ${minutes % 60} min"
            else -> "$minutes min"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
