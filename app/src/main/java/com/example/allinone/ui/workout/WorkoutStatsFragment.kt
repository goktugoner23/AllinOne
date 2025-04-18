package com.example.allinone.ui.workout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.allinone.databinding.FragmentWorkoutStatsBinding

class WorkoutStatsFragment : Fragment() {
    private var _binding: FragmentWorkoutStatsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkoutStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Empty implementation
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 