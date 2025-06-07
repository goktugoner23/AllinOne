package com.example.allinone.ui.workout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.allinone.R
import com.example.allinone.databinding.FragmentWorkoutDashboardBinding
import java.text.SimpleDateFormat
import java.util.Locale

class WorkoutDashboardFragment : Fragment() {
    private var _binding: FragmentWorkoutDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WorkoutViewModel by activityViewModels()

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkoutDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up SwipeRefreshLayout
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshWorkouts()
        }

        // Set refresh indicator colors
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorAccent,
            R.color.colorPrimaryDark
        )

        // Observe workout data
        viewModel.allWorkouts.observe(viewLifecycleOwner) { workouts ->
            // Hide refresh indicator if it's showing
            binding.swipeRefreshLayout.isRefreshing = false

            updateDashboard(workouts)
        }
    }

    /**
     * Refresh workouts data from the server
     */
    private fun refreshWorkouts() {
        android.util.Log.d("WorkoutDashboardFragment", "Refreshing workouts")
        viewModel.refreshWorkouts()
    }

    private fun updateDashboard(workouts: List<com.example.allinone.data.Workout>) {
        // Update weekly stats
        val weeklyWorkouts = viewModel.getWeeklyWorkouts(workouts)
        binding.weeklyWorkoutCount.text = getString(R.string.weekly_workout_count, weeklyWorkouts.size)

        val weeklyDuration = weeklyWorkouts.sumOf { it.duration }
        binding.weeklyWorkoutDuration.text = getString(R.string.weekly_workout_duration, formatDuration(weeklyDuration))

        // Update most recent workout
        if (workouts.isNotEmpty()) {
            val mostRecent = workouts.maxByOrNull { it.startTime }
            mostRecent?.let {
                binding.recentWorkoutName.text = it.programName ?: getString(R.string.custom_workout)
                binding.recentWorkoutDate.text = dateFormat.format(it.startTime)
            }
        } else {
            binding.recentWorkoutName.text = getString(R.string.no_recent_workouts)
            binding.recentWorkoutDate.text = ""
        }

        // Update all-time stats
        binding.totalWorkoutCount.text = getString(R.string.total_workout_count, workouts.size)

        val totalDuration = workouts.sumOf { it.duration }
        binding.totalWorkoutDuration.text = getString(R.string.total_workout_duration, formatDuration(totalDuration))
    }

    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> "$hours hr ${minutes % 60} min"
            else -> "$minutes min"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()

        // Refresh data when returning to this fragment
        refreshWorkouts()
    }
}
