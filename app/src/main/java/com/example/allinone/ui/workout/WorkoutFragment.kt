package com.example.allinone.ui.workout

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.allinone.R
import com.example.allinone.databinding.FragmentWorkoutBinding
import com.example.allinone.ui.BaseFragment

class WorkoutFragment : BaseFragment() {
    private var _binding: FragmentWorkoutBinding? = null
    private val binding get() = _binding!!
    private val TAG = "WorkoutFragment"

    // Shared ViewModel for all child fragments
    private val viewModel: WorkoutViewModel by viewModels()

    // Keep fragment instances to reuse them
    private val dashboardFragment: Fragment by lazy {
        childFragmentManager.findFragmentByTag("dashboard") ?: WorkoutDashboardFragment()
    }

    private val exerciseFragment: Fragment by lazy {
        childFragmentManager.findFragmentByTag("exercise") ?: WorkoutExerciseFragment()
    }

    private val programFragment: Fragment by lazy {
        childFragmentManager.findFragmentByTag("program") ?: WorkoutProgramFragment()
    }

    private val statsFragment: Fragment by lazy {
        childFragmentManager.findFragmentByTag("stats") ?: WorkoutStatsFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup navigation between Dashboard, Exercise, Program, and Stats
        binding.workoutBottomNavigation.setOnItemSelectedListener { item ->
            // Update title based on selected item
            updateTitle(item.itemId)

            when (item.itemId) {
                R.id.workout_dashboard -> {
                    switchFragment(dashboardFragment, "dashboard")
                    true
                }
                R.id.workout_exercise -> {
                    switchFragment(exerciseFragment, "exercise")
                    true
                }
                R.id.workout_program -> {
                    switchFragment(programFragment, "program")
                    true
                }
                R.id.workout_stats -> {
                    switchFragment(statsFragment, "stats")
                    true
                }
                else -> false
            }
        }

        // Set default fragment to Dashboard tab
        if (savedInstanceState == null) {
            binding.workoutBottomNavigation.selectedItemId = R.id.workout_dashboard
        }
    }

    override fun onResume() {
        super.onResume()

        // Refresh data when returning to this fragment (e.g., from ActiveWorkoutFragment)
        android.util.Log.d(TAG, "WorkoutFragment resumed, refreshing data")
        viewModel.refreshWorkouts()
        viewModel.refreshPrograms()
    }

    private fun switchFragment(fragment: Fragment, tag: String) {
        childFragmentManager.beginTransaction().apply {
            // Hide all fragments
            childFragmentManager.fragments.forEach { hide(it) }

            // Show the selected fragment if it's already added, otherwise add it
            if (fragment.isAdded) {
                show(fragment)
            } else {
                add(R.id.workout_fragment_container, fragment, tag)
            }
        }.commit()
    }

    private fun updateTitle(itemId: Int) {
        val title = when (itemId) {
            R.id.workout_dashboard -> "Dashboard"
            R.id.workout_exercise -> "Exercise"
            R.id.workout_program -> "Programs"
            R.id.workout_stats -> "Stats"
            else -> "Workout"
        }

        // Set title in action bar
        (requireActivity() as AppCompatActivity).supportActionBar?.title = title
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
