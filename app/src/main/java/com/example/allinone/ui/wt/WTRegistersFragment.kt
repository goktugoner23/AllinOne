package com.example.allinone.ui.wt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.allinone.R
import com.example.allinone.databinding.FragmentWtRegistersBinding

class WTRegistersFragment : Fragment() {
    private var _binding: FragmentWtRegistersBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWtRegistersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Hide main bottom navigation and show WT bottom navigation
        requireActivity().findViewById<View>(R.id.bottomNavigation).visibility = View.GONE
        binding.wtBottomNavigation.visibility = View.VISIBLE
        
        // Setup navigation between Register and History
        binding.wtBottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.wtRegisterFragment -> {
                    childFragmentManager.beginTransaction()
                        .replace(R.id.wtFragmentContainer, WTRegisterFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }
        
        // Set default fragment
        if (savedInstanceState == null) {
            binding.wtBottomNavigation.selectedItemId = R.id.wtRegisterFragment
        }
    }

    override fun onDestroyView() {
        // Show main bottom navigation when leaving
        requireActivity().findViewById<View>(R.id.bottomNavigation).visibility = View.VISIBLE
        super.onDestroyView()
        _binding = null
    }
} 