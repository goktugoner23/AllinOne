package com.example.allinone.ui.workout

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
import com.example.allinone.config.MuscleGroups
import com.example.allinone.data.Program
import com.example.allinone.data.Workout
import com.example.allinone.data.WorkoutExercise
import com.example.allinone.data.WorkoutSet
import com.example.allinone.databinding.FragmentWorkoutExerciseBinding
import com.example.allinone.ui.workout.adapters.WorkoutExerciseAdapter
import com.example.allinone.ui.workout.adapters.WorkoutLogAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class WorkoutExerciseFragment : Fragment() {
    private var _binding: FragmentWorkoutExerciseBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WorkoutViewModel by activityViewModels()

    // Workout variables
    private var selectedProgram: Program? = null
    private var currentWorkout: Workout? = null

    // Log variables
    private var allWorkouts: List<Workout> = emptyList()
    private var filteredWorkouts: List<Workout> = emptyList()
    private var selectedMuscleGroup: String? = null
    private var selectedSortOption: String? = null
    private lateinit var workoutLogAdapter: WorkoutLogAdapter
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())

    private val sortOptions = arrayOf(
        "Date (Newest First)",
        "Date (Oldest First)",
        "Duration (Longest First)",
        "Duration (Shortest First)"
    )

    // Add a flag to track if the no programs message has been shown
    private var hasShownNoProgramsMessage = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkoutExerciseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Force refresh workouts directly from Firebase
        viewModel.refreshWorkouts()

        // Set up program spinner
        viewModel.allPrograms.observe(viewLifecycleOwner) { programs ->
            setupProgramSpinner(programs)
        }

        // Set up create workout button
        binding.createWorkoutButton.setOnClickListener {
            createWorkout()
        }

        // Set up workout history section
        setupWorkoutLog()
    }

    private fun setupWorkoutLog() {
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

        // Set up SwipeRefreshLayout for pull-to-refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshWorkouts()
        }

        // Set refresh indicator colors
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorAccent,
            R.color.colorPrimaryDark
        )

        // Apply filter button
        binding.applyFiltersButton.setOnClickListener {
            applyFilters()
        }

        // Observe workouts data with logging
        viewModel.allWorkouts.observe(viewLifecycleOwner) { workouts ->
            // Hide refresh indicator if it's showing
            binding.swipeRefreshLayout.isRefreshing = false

            android.util.Log.d("WorkoutExerciseFragment", "Received ${workouts.size} workouts from ViewModel")

            // Log each workout for debugging
            workouts.forEach { workout ->
                android.util.Log.d("WorkoutExerciseFragment", "Workout: ${workout.id}, ${workout.programName}, Exercises: ${workout.exercises.size}")
            }

            allWorkouts = workouts
            applyFilters()
        }
    }

    private fun setupFilters() {
        // Set up muscle group filter
        val muscleGroupItems = listOf("All Muscle Groups") + MuscleGroups.MUSCLE_GROUPS.toList()
        val muscleGroupAdapter = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_item,
            muscleGroupItems
        )

        val muscleGroupDropdown = binding.filterMuscleGroup
        muscleGroupDropdown.setAdapter(muscleGroupAdapter)
        muscleGroupDropdown.setText(muscleGroupItems[0], false)

        // Set up sort options
        val sortAdapter = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_item,
            sortOptions
        )

        val sortDropdown = binding.sortOption
        sortDropdown.setAdapter(sortAdapter)
        sortDropdown.setText(sortOptions[0], false)
    }

    private fun applyFilters() {
        android.util.Log.d("WorkoutExerciseFragment", "Applying filters to ${allWorkouts.size} workouts")

        if (allWorkouts.isEmpty()) {
            android.util.Log.d("WorkoutExerciseFragment", "No workouts available to filter")
            updateEmptyState(true)
            return
        }

        // Get selected filter values
        selectedMuscleGroup = binding.filterMuscleGroup.text.toString()
        selectedSortOption = binding.sortOption.text.toString()

        android.util.Log.d("WorkoutExerciseFragment", "Filter - Muscle group: $selectedMuscleGroup, Sort: $selectedSortOption")

        // Apply muscle group filter
        filteredWorkouts = if (selectedMuscleGroup == "All Muscle Groups" || selectedMuscleGroup.isNullOrEmpty()) {
            allWorkouts
        } else {
            allWorkouts.filter { workout ->
                workout.exercises.any { exercise ->
                    exercise.muscleGroup == selectedMuscleGroup
                }
            }
        }

        android.util.Log.d("WorkoutExerciseFragment", "After muscle group filter: ${filteredWorkouts.size} workouts")

        // Apply sorting
        filteredWorkouts = when (selectedSortOption) {
            "Date (Oldest First)" -> filteredWorkouts.sortedBy { it.startTime }
            "Duration (Longest First)" -> filteredWorkouts.sortedByDescending { it.duration }
            "Duration (Shortest First)" -> filteredWorkouts.sortedBy { it.duration }
            else -> filteredWorkouts.sortedByDescending { it.startTime } // Default is newest first
        }

        android.util.Log.d("WorkoutExerciseFragment", "Final filtered workouts: ${filteredWorkouts.size}")

        // Log the details of filtered workouts
        filteredWorkouts.forEach { workout ->
            android.util.Log.d("WorkoutExerciseFragment", "Filtered workout: ID=${workout.id}, Name=${workout.programName ?: "Unnamed"}, Date=${workout.startTime}")
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
        // Log the workout details we're about to display
        android.util.Log.d("WorkoutExerciseFragment", "Showing workout details: ${workout.programName ?: "Unnamed"} (ID: ${workout.id})")
        android.util.Log.d("WorkoutExerciseFragment", "Workout has ${workout.exercises.size} exercises")

        // Create a dialog to show workout details
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_workout_details, null)

        val workoutNameText = dialogView.findViewById<TextView>(R.id.workout_name_text)
        val workoutDateText = dialogView.findViewById<TextView>(R.id.workout_date_text)
        val workoutDurationText = dialogView.findViewById<TextView>(R.id.workout_duration_text)
        val exercisesRecyclerView = dialogView.findViewById<RecyclerView>(R.id.exercises_recycler_view)

        // Set workout details
        workoutNameText.text = when {
            !workout.programName.isNullOrBlank() -> workout.programName
            workout.exercises.isNotEmpty() -> "Workout with ${workout.exercises.size} exercises"
            else -> "Unnamed Workout"
        }
        workoutDateText.text = dateFormat.format(workout.startTime)
        workoutDurationText.text = formatDuration(workout.duration)

        // Add exercise summary text if no exercises
        if (workout.exercises.isEmpty()) {
            val textView = TextView(requireContext())
            textView.text = "No exercises recorded for this workout"
            textView.textSize = 16f
            textView.setPadding(16, 16, 16, 16)

            // Find the parent layout and add the text view before the RecyclerView
            val parent = exercisesRecyclerView.parent as? ViewGroup
            if (parent != null) {
                val index = parent.indexOfChild(exercisesRecyclerView)
                parent.addView(textView, index)
            }
        }

        // Set up exercises RecyclerView
        exercisesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        val exerciseAdapter = WorkoutExerciseAdapter(
            exercises = workout.exercises,
            onExerciseCompletedChange = { _, _ -> /* No-op */ },
            readOnly = true // Set adapter to read-only mode
        )
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

    private fun setupProgramSpinner(programs: List<Program>) {
        android.util.Log.d("WorkoutExerciseFragment", "Setting up program spinner with ${programs.size} programs: ${programs.map { it.name }}")

        if (programs.isEmpty()) {
            // Handle the case where there are no programs
            binding.programSpinner.isEnabled = false
            binding.createWorkoutButton.isEnabled = false

            // Show a message to create programs first, but only once
            if (!hasShownNoProgramsMessage) {
                Toast.makeText(requireContext(), "Create programs first in the Programs tab", Toast.LENGTH_SHORT).show()
                hasShownNoProgramsMessage = true
            }
            return
        }

        // Reset the flag when programs are available
        hasShownNoProgramsMessage = false

        // Re-enable controls if they were disabled
        binding.programSpinner.isEnabled = true
        binding.createWorkoutButton.isEnabled = true

        // Log details about each program and its exercises
        programs.forEach { program ->
            android.util.Log.d("WorkoutExerciseFragment", "Program: ${program.name} (ID: ${program.id}) has ${program.exercises.size} exercises")
            if (program.exercises.isNotEmpty()) {
                android.util.Log.d("WorkoutExerciseFragment", "Exercises: ${program.exercises.map { it.exerciseName }}")
            }
        }

        // Use program names directly without "Custom Workout" option
        val programNames = programs.map { it.name }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            programNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.programSpinner.adapter = adapter

        // Check if the previously selected program still exists
        if (selectedProgram != null && !viewModel.programExists(selectedProgram!!.id)) {
            android.util.Log.d("WorkoutExerciseFragment", "Previously selected program has been deleted")
            selectedProgram = null
        }

        binding.programSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Check if the position is valid
                if (position < 0 || position >= programs.size) {
                    android.util.Log.e("WorkoutExerciseFragment", "Invalid spinner position: $position, programs size: ${programs.size}")
                    selectedProgram = null
                    return
                }

                // Directly map position to programs list
                selectedProgram = programs[position]
                android.util.Log.d("WorkoutExerciseFragment", "Selected program: ${selectedProgram?.name}")

                // Null-safe log to prevent crashes if the program is corrupt
                selectedProgram?.let {
                    android.util.Log.d("WorkoutExerciseFragment", "Selected program has ${it.exercises.size} exercises")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Select first program by default if available
                selectedProgram = if (programs.isNotEmpty()) programs[0] else null
            }
        }

        // Attempt to restore previous selection if possible
        if (selectedProgram != null) {
            val index = programs.indexOfFirst { it.id == selectedProgram!!.id }
            if (index >= 0) {
                binding.programSpinner.setSelection(index)
            } else {
                // Select first program if previous selection is gone
                binding.programSpinner.setSelection(0)
            }
        }
    }

    private fun createWorkout() {
        // Extra check if we have a selected program
        if (selectedProgram != null) {
            // Verify that the program still exists (could have been deleted in another fragment)
            if (!viewModel.programExists(selectedProgram!!.id)) {
                Toast.makeText(
                    requireContext(),
                    "The selected program has been deleted. Please select another program.",
                    Toast.LENGTH_LONG
                ).show()

                // Refresh programs to update the spinner
                viewModel.refreshPrograms()
                return
            }

            android.util.Log.d("WorkoutExerciseFragment", "Creating workout from program: ${selectedProgram!!.name}")
            android.util.Log.d("WorkoutExerciseFragment", "Program has ${selectedProgram!!.exercises.size} exercises")
            android.util.Log.d("WorkoutExerciseFragment", "Exercise list: ${selectedProgram!!.exercises.map { it.exerciseName }}")

            // Force refresh the program to ensure exercises are loaded
            viewModel.getProgram(selectedProgram!!.id) { refreshedProgram ->
                if (refreshedProgram != null) {
                    // Update the selected program with the refreshed one
                    selectedProgram = refreshedProgram
                    android.util.Log.d("WorkoutExerciseFragment", "Refreshed program has ${refreshedProgram.exercises.size} exercises")

                    // Now create the workout with the refreshed program
                    createWorkoutWithProgram(refreshedProgram)
                } else {
                    // Program may have been deleted during refresh attempt
                    Toast.makeText(
                        requireContext(),
                        "Error loading program. It may have been deleted.",
                        Toast.LENGTH_LONG
                    ).show()

                    // Refresh programs to update the spinner
                    viewModel.refreshPrograms()
                }
            }
        } else {
            // Create a custom workout with no exercises
            createWorkoutWithProgram(null)
        }
    }

    private fun createWorkoutWithProgram(program: Program?) {
        val exercises = if (program != null && program.exercises.isNotEmpty()) {
            // Convert program exercises to workout exercises
            program.exercises.map { programExercise ->
                WorkoutExercise(
                    exerciseId = programExercise.exerciseId,
                    exerciseName = programExercise.exerciseName,
                    muscleGroup = programExercise.muscleGroup,
                    sets = List(programExercise.sets) { setIndex ->
                        WorkoutSet(
                            setNumber = setIndex + 1,
                            reps = programExercise.reps,
                            weight = programExercise.weight
                        )
                    }
                )
            }
        } else {
            emptyList()
        }

        // Create a new workout (always use program name if available)
        val workout = Workout(
            programId = program?.id,
            programName = program?.name,
            startTime = Date(),
            exercises = exercises
        )

        android.util.Log.d("WorkoutExerciseFragment", "Created workout with name: ${workout.programName}, ${workout.exercises.size} exercises")

        // Save the created workout to the currentWorkout variable
        currentWorkout = workout

        // Navigate to the active workout fragment
        navigateToActiveWorkout()
    }

    /**
     * Navigate to active workout screen with the current workout
     */
    private fun navigateToActiveWorkout() {
        if (currentWorkout == null) {
            Toast.makeText(requireContext(), "No active workout", Toast.LENGTH_SHORT).show()
            return
        }

        // Serialize workout to JSON for passing to ActiveWorkoutFragment
        val workoutJson = viewModel.workoutToJson(currentWorkout!!)

        // Create arguments bundle
        val args = Bundle().apply {
            putString("workout", workoutJson)
        }

        // Create and configure ActiveWorkoutFragment
        val activeWorkoutFragment = ActiveWorkoutFragment().apply {
            arguments = args
        }

        // Hide the bottom navigation view when in active workout
        val bottomNavigationView = (parentFragment as? WorkoutFragment)?.view?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.workout_bottom_navigation)
        bottomNavigationView?.visibility = View.GONE

        // Use the parent activity's main container (nav_host_fragment) instead of the workout container
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, activeWorkoutFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        return if (seconds > 0) {
            String.format("%d min %02d sec", minutes, seconds)
        } else {
            String.format("%d min", minutes)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()

        // Show the bottom navigation when returning from ActiveWorkoutFragment
        val bottomNavigationView = (parentFragment as? WorkoutFragment)?.view?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.workout_bottom_navigation)
        bottomNavigationView?.visibility = View.VISIBLE

        // Refresh workouts list when returning from active workout
        refreshWorkouts()
    }

    /**
     * Refresh workouts data from the server
     */
    private fun refreshWorkouts() {
        android.util.Log.d("WorkoutExerciseFragment", "Manually refreshing workouts")
        viewModel.refreshWorkouts()
    }
}
