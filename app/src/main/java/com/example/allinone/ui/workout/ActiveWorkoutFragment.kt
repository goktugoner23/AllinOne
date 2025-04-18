package com.example.allinone.ui.workout

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.data.Workout
import com.example.allinone.data.WorkoutExercise
import com.example.allinone.databinding.FragmentActiveWorkoutBinding
import com.example.allinone.ui.workout.adapters.WorkoutExerciseAdapter
import java.util.Date

/**
 * Fragment for active workout tracking with timer and exercise list
 */
class ActiveWorkoutFragment : Fragment() {

    private var _binding: FragmentActiveWorkoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WorkoutViewModel by viewModels()
    
    // Timer variables
    private var isTimerRunning = false
    private var startTime: Long = 0
    private var elapsedTime: Long = 0
    private var pausedTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    
    // Workout variables
    private lateinit var currentWorkout: Workout
    private var workoutExercises = mutableListOf<WorkoutExercise>()
    private lateinit var workoutExerciseAdapter: WorkoutExerciseAdapter
    
    private val timerRunnable = object : Runnable {
        override fun run() {
            val currentTime = SystemClock.elapsedRealtime()
            elapsedTime = currentTime - startTime + pausedTime

            updateTimerText(elapsedTime)

            handler.postDelayed(this, 100)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActiveWorkoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set up the action bar
        setupActionBar()
        
        // Set up menu provider for handling back button
        setupMenuProvider()
        
        // Get current workout from arguments
        val workoutJson = arguments?.getString("workout") ?: return navigateBack()
        
        try {
            // Parse workout from JSON
            currentWorkout = viewModel.parseWorkoutFromJson(workoutJson)
            workoutExercises = currentWorkout.exercises.toMutableList()
            
            android.util.Log.d("ActiveWorkoutFragment", "Started workout with ${workoutExercises.size} exercises")
            
            // Set workout name title
            binding.workoutNameTitle.text = currentWorkout.programName ?: "Custom Workout"
            
            // Setup exercise list
            setupWorkoutExercisesList()
            
            // Setup timer controls
            binding.playPauseButton.setOnClickListener { togglePlayPause() }
            binding.stopButton.setOnClickListener { confirmStopWorkout() }
            
            // Start the timer
            startTimer()
            
        } catch (e: Exception) {
            android.util.Log.e("ActiveWorkoutFragment", "Error parsing workout: ${e.message}")
            Toast.makeText(requireContext(), "Error loading workout", Toast.LENGTH_SHORT).show()
            navigateBack()
        }
    }
    
    private fun setupActionBar() {
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.apply {
            title = "Active Workout"
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }
    
    private fun setupMenuProvider() {
        // Add menu provider to handle the back button
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // No menu items to add
            }
            
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle back button
                if (menuItem.itemId == android.R.id.home) {
                    confirmStopWorkout()
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
    
    private fun setupWorkoutExercisesList() {
        // Set up RecyclerView
        binding.exercisesRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initialize adapter
        workoutExerciseAdapter = WorkoutExerciseAdapter(
            exercises = workoutExercises,
            onExerciseCompletedChange = { exercise, isCompleted ->
                // Update exercise completion status
                updateExerciseCompletion(exercise, isCompleted)
            }
        )
        binding.exercisesRecyclerView.adapter = workoutExerciseAdapter
    }
    
    private fun updateExerciseCompletion(exercise: WorkoutExercise, isCompleted: Boolean) {
        // Find the exercise in the list
        val index = workoutExercises.indexOfFirst { it.exerciseId == exercise.exerciseId }
        if (index != -1) {
            // Update all sets in the exercise to the new completion status
            val updatedSets = exercise.sets.map { set ->
                set.copy(completed = isCompleted)
            }

            // Create updated exercise
            val updatedExercise = exercise.copy(sets = updatedSets)

            // Update the list
            workoutExercises[index] = updatedExercise

            // Update the adapter
            workoutExerciseAdapter.updateExerciseCompletion(exercise.exerciseId, isCompleted)
        }
    }
    
    private fun togglePlayPause() {
        if (isTimerRunning) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        if (!isTimerRunning) {
            startTime = SystemClock.elapsedRealtime()
            handler.post(timerRunnable)
            isTimerRunning = true

            // Update button states
            binding.playPauseButton.text = "Pause"
            binding.playPauseButton.icon = resources.getDrawable(R.drawable.ic_pause, requireContext().theme)
            binding.stopButton.isEnabled = true
        }
    }

    private fun pauseTimer() {
        if (isTimerRunning) {
            handler.removeCallbacks(timerRunnable)
            pausedTime = elapsedTime
            isTimerRunning = false

            // Update button states
            binding.playPauseButton.text = "Play"
            binding.playPauseButton.icon = resources.getDrawable(R.drawable.ic_play, requireContext().theme)
        }
    }

    private fun stopTimer() {
        if (isTimerRunning) {
            handler.removeCallbacks(timerRunnable)
            isTimerRunning = false
        }
    }
    
    private fun updateTimerText(timeInMillis: Long) {
        val hours = (timeInMillis / (1000 * 60 * 60)) % 24
        val minutes = (timeInMillis / (1000 * 60)) % 60
        val seconds = (timeInMillis / 1000) % 60
        val milliseconds = (timeInMillis % 1000) / 10

        binding.timerText.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        binding.timerMsText.text = String.format(".%02d", milliseconds)
    }
    
    private fun confirmStopWorkout() {
        AlertDialog.Builder(requireContext())
            .setTitle("Stop Workout")
            .setMessage("Are you sure you want to stop this workout?")
            .setPositiveButton("Yes") { _, _ ->
                stopTimer()
                finishWorkout()
                saveWorkout()
                navigateBack()
            }
            .setNegativeButton("No", null)
            .show()
    }
    
    private fun finishWorkout() {
        val endTime = Date()
        val updatedWorkout = currentWorkout.copy(
            endTime = endTime,
            duration = elapsedTime,
            exercises = workoutExercises
        )
        
        android.util.Log.d("ActiveWorkoutFragment", "Finishing workout with ${workoutExercises.size} exercises, duration: $elapsedTime ms")
        currentWorkout = updatedWorkout
    }
    
    private fun saveWorkout() {
        android.util.Log.d("ActiveWorkoutFragment", "Saving workout: ${currentWorkout.programName} with ${currentWorkout.exercises.size} exercises")
        
        if (currentWorkout.exercises.isNotEmpty()) {
            android.util.Log.d("ActiveWorkoutFragment", "Exercises: ${currentWorkout.exercises.map { exercise -> 
                "${exercise.exerciseName} (${exercise.sets.size} sets)"
            }}")
        }
        
        // Save the workout in the ViewModel
        viewModel.saveWorkout(currentWorkout)
        Toast.makeText(requireContext(), R.string.workout_saved, Toast.LENGTH_SHORT).show()
    }
    
    private fun navigateBack() {
        // Restore default action bar configuration before navigating back
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowHomeEnabled(false)
            title = "Workout"
        }
        
        // Navigate back to the previous fragment
        requireActivity().supportFragmentManager.popBackStack()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(timerRunnable)
        _binding = null
    }
} 