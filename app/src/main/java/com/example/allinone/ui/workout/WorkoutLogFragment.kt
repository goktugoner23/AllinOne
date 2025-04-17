package com.example.allinone.ui.workout

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allinone.R
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

        // Set up RecyclerView
        binding.workoutLogRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initialize adapter
        workoutLogAdapter = WorkoutLogAdapter(emptyList()) { workout ->
            // Handle workout click - show workout details
            showWorkoutDetailsDialog(workout)
        }
        binding.workoutLogRecyclerView.adapter = workoutLogAdapter

        // Observe workouts data
        viewModel.allWorkouts.observe(viewLifecycleOwner) { workouts ->
            if (workouts.isEmpty()) {
                binding.emptyLogText.visibility = View.VISIBLE
                binding.workoutLogRecyclerView.visibility = View.GONE
            } else {
                binding.emptyLogText.visibility = View.GONE
                binding.workoutLogRecyclerView.visibility = View.VISIBLE
                workoutLogAdapter.updateWorkouts(workouts)
            }
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
