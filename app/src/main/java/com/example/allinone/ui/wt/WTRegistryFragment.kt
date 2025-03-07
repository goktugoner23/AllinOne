package com.example.allinone.ui.wt

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.allinone.R
import com.example.allinone.databinding.FragmentWtRegistryBinding
import com.example.allinone.viewmodels.WTRegisterViewModel

class WTRegistryFragment : Fragment() {
    private var _binding: FragmentWtRegistryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WTRegisterViewModel by viewModels()
    private var networkStatusText: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWtRegistryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set initial title
        updateTitle(R.id.wtRegisterFragment)
        
        // Hide main bottom navigation and show WT bottom navigation
        requireActivity().findViewById<View>(R.id.bottomNavigation).visibility = View.GONE
        binding.wtBottomNavigation.visibility = View.VISIBLE
        
        // Setup network status indicator
        setupNetworkStatusIndicator()
        
        // Setup navigation between Register, Lessons and Calendar
        binding.wtBottomNavigation.setOnItemSelectedListener { item ->
            // Update title based on selected item
            updateTitle(item.itemId)
            
            when (item.itemId) {
                R.id.wtRegisterFragment -> {
                    childFragmentManager.beginTransaction()
                        .replace(R.id.wtFragmentContainer, WTRegisterFragment())
                        .commit()
                    true
                }
                R.id.wtLessonsFragment -> {
                    childFragmentManager.beginTransaction()
                        .replace(R.id.wtFragmentContainer, WTLessonsFragment())
                        .commit()
                    true
                }
                R.id.wtCalendarFragment -> {
                    childFragmentManager.beginTransaction()
                        .replace(R.id.wtFragmentContainer, WTCalendarFragment())
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
    
    private fun setupNetworkStatusIndicator() {
        // Create network status text view if it doesn't exist
        if (networkStatusText == null) {
            networkStatusText = TextView(requireContext()).apply {
                text = "Network unavailable. Using cached data."
                setBackgroundResource(R.color.colorError)
                setTextColor(resources.getColor(android.R.color.white, null))
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                visibility = View.GONE
                setPadding(8, 8, 8, 8)
                
                // Add to the root layout, at the top
                binding.root.addView(this, 0)
                
                // Set layout params
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        }
        
        // Observe network status
        observeNetworkStatus()
    }
    
    private fun observeNetworkStatus() {
        // Observe Firebase repository connection status
        viewModel.isNetworkAvailable.observe(viewLifecycleOwner) { isAvailable ->
            // Update with a delay to prevent false network status changes
            Handler(Looper.getMainLooper()).postDelayed({
                if (view != null && isAdded && networkStatusText != null) {
                    networkStatusText?.visibility = if (isAvailable) View.GONE else View.VISIBLE
                    
                    if (!isAvailable) {
                        // Only show toast if network is newly unavailable
                        Toast.makeText(
                            context, 
                            "Network unavailable. Using cached data.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // When network becomes available, refresh data
                        viewModel.refreshData()
                    }
                }
            }, 1000) // 1-second delay
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check network status on resume with a delay to avoid flicker
        Handler(Looper.getMainLooper()).postDelayed({
            if (view != null && isAdded && networkStatusText != null) {
                val isAvailable = viewModel.isNetworkAvailable.value ?: true
                networkStatusText?.visibility = if (isAvailable) View.GONE else View.VISIBLE
            }
        }, 500)
    }
    
    private fun updateTitle(itemId: Int) {
        val title = when (itemId) {
            R.id.wtRegisterFragment -> getString(R.string.title_wing_tzun_registry)
            R.id.wtLessonsFragment -> getString(R.string.title_lesson_schedule)
            R.id.wtCalendarFragment -> getString(R.string.title_calendar)
            else -> getString(R.string.title_wing_tzun_registry)
        }
        
        // Set title in both action bar and parent activity
        (requireActivity() as AppCompatActivity).supportActionBar?.title = title
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        networkStatusText = null
    }
} 