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
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.data.Program
import com.example.allinone.data.Workout
import com.example.allinone.data.WorkoutExercise
import com.example.allinone.data.WorkoutSet
import com.example.allinone.databinding.FragmentWorkoutExerciseBinding
import com.example.allinone.ui.workout.adapters.WorkoutExerciseAdapter
import java.util.Date

class WorkoutExerciseFragment : Fragment() {
    private var _binding: FragmentWorkoutExerciseBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WorkoutViewModel by viewModels()

    private var isTimerRunning = false
    private var startTime: Long = 0
    private var elapsedTime: Long = 0
    private var pausedTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())

    private var selectedProgram: Program? = null
    private var currentWorkout: Workout? = null
    private var workoutExercises = mutableListOf<WorkoutExercise>()

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
        _binding = FragmentWorkoutExerciseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up timer buttons
        binding.playPauseButton.setOnClickListener { togglePlayPause() }
        binding.stopButton.setOnClickListener { confirmStopWorkout() }

        // Set up program spinner
        viewModel.allPrograms.observe(viewLifecycleOwner) { programs ->
            setupProgramSpinner(programs)
        }

        // Set up create workout button
        binding.createWorkoutButton.setOnClickListener {
            createWorkout()
        }

        // Set up save workout button
        binding.saveWorkoutFab.setOnClickListener {
            saveWorkout()
        }
    }

    private fun setupProgramSpinner(programs: List<Program>) {
        val programNames = programs.map { it.name }.toMutableList()
        programNames.add(0, "Custom Workout")

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            programNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.programSpinner.adapter = adapter

        binding.programSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedProgram = if (position == 0) null else programs[position - 1]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedProgram = null
            }
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

            // Disable program selection
            binding.programSpinner.isEnabled = false
            binding.createWorkoutButton.isEnabled = false
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

        // Reset button states
        binding.playPauseButton.text = "Play"
        binding.playPauseButton.icon = resources.getDrawable(R.drawable.ic_play, requireContext().theme)
        binding.stopButton.isEnabled = false

        // Enable program selection
        binding.programSpinner.isEnabled = true
        binding.createWorkoutButton.isEnabled = true
    }

    private fun updateTimerText(timeInMillis: Long) {
        val hours = (timeInMillis / (1000 * 60 * 60)) % 24
        val minutes = (timeInMillis / (1000 * 60)) % 60
        val seconds = (timeInMillis / 1000) % 60
        val milliseconds = (timeInMillis % 1000) / 100

        binding.timerText.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        binding.timerMsText.text = String.format(".%01d", milliseconds)
    }

    private fun confirmStopWorkout() {
        AlertDialog.Builder(requireContext())
            .setTitle("Stop Workout")
            .setMessage("Are you sure you want to stop this workout?")
            .setPositiveButton("Yes") { _, _ ->
                stopTimer()
                finishWorkout()
                saveWorkout()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun createWorkout() {
        // Create a new workout
        val workout = Workout(
            programId = selectedProgram?.id,
            programName = selectedProgram?.name ?: "Custom Workout",
            startTime = Date(),
            exercises = if (selectedProgram != null) {
                // Convert program exercises to workout exercises
                selectedProgram!!.exercises.map { programExercise ->
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
        )

        currentWorkout = workout
        workoutExercises = workout.exercises.toMutableList()

        // Show workout exercises
        setupWorkoutExercisesList()

        // Start the timer
        startTimer()
    }

    private lateinit var workoutExerciseAdapter: WorkoutExerciseAdapter

    private fun setupWorkoutExercisesList() {
        // Set up RecyclerView
        binding.currentWorkoutRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initialize adapter
        workoutExerciseAdapter = WorkoutExerciseAdapter(workoutExercises) { exercise, isCompleted ->
            // Update exercise completion status
            updateExerciseCompletion(exercise, isCompleted)
        }
        binding.currentWorkoutRecyclerView.adapter = workoutExerciseAdapter

        // Show the RecyclerView and save button
        binding.currentWorkoutRecyclerView.visibility = View.VISIBLE
        binding.saveWorkoutFab.visibility = View.VISIBLE
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

    private fun finishWorkout() {
        // Update workout with end time and duration
        currentWorkout?.let {
            val endTime = Date()
            val updatedWorkout = it.copy(
                endTime = endTime,
                duration = elapsedTime,
                exercises = workoutExercises
            )

            currentWorkout = updatedWorkout

            // Show save option
            binding.saveWorkoutFab.visibility = View.VISIBLE
        }
    }

    private fun saveWorkout() {
        currentWorkout?.let {
            viewModel.saveWorkout(it)
            Toast.makeText(requireContext(), R.string.workout_saved, Toast.LENGTH_SHORT).show()

            // Reset UI
            binding.currentWorkoutRecyclerView.visibility = View.GONE
            binding.saveWorkoutFab.visibility = View.GONE
            binding.timerText.text = "00:00:00"
            binding.timerMsText.text = ".0"
            pausedTime = 0
            elapsedTime = 0
            currentWorkout = null
            workoutExercises.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(timerRunnable)
        _binding = null
    }
}
