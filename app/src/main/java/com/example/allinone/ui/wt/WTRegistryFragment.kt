package com.example.allinone.ui.wt

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.allinone.R
import com.example.allinone.databinding.FragmentWtRegistryBinding
import com.example.allinone.databinding.FragmentWtRegisterBinding
import com.example.allinone.data.WTLesson
import com.example.allinone.data.WTStudent
import com.example.allinone.viewmodels.WTRegisterViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.*

class WTRegistryFragment : Fragment() {
    private var _binding: FragmentWtRegistryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WTRegisterViewModel by viewModels()
    private var networkStatusText: TextView? = null
    
    // Keep fragment instances to reuse them
    private val studentsFragment: Fragment by lazy { 
        childFragmentManager.findFragmentByTag("students") ?: WTStudentsFragment()
    }
    
    // For the register tab, we're using a separate WTRegisterContentFragment class
    private val registerFragment: Fragment by lazy {
        childFragmentManager.findFragmentByTag("register") ?: WTRegisterContentFragment()
    }
    
    private val lessonsFragment: Fragment by lazy { 
        childFragmentManager.findFragmentByTag("lessons") ?: WTLessonsFragment()
    }
    private val seminarsFragment: Fragment by lazy { 
        childFragmentManager.findFragmentByTag("seminars") ?: WTSeminarsFragment()
    }
    
    // Track the current fragment
    private var currentFragment: Fragment? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWtRegistryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set initial title
        updateTitle(R.id.wtStudentsFragment)
        
        // Hide main bottom navigation and show WT bottom navigation
        requireActivity().findViewById<View>(R.id.bottomNavigation).visibility = View.GONE
        binding.wtBottomNavigation.visibility = View.VISIBLE
        
        // Setup network status indicator
        setupNetworkStatusIndicator()
        
        // Setup navigation between Students, Register, Lessons, and Seminars
        binding.wtBottomNavigation.setOnItemSelectedListener { item ->
            // Update title based on selected item
            updateTitle(item.itemId)
            
            when (item.itemId) {
                R.id.wtStudentsFragment -> {
                    switchFragment(studentsFragment, "students")
                    true
                }
                R.id.wtRegisterFragment -> {
                    switchFragment(registerFragment, "register")
                    true
                }
                R.id.wtLessonsFragment -> {
                    switchFragment(lessonsFragment, "lessons")
                    true
                }
                R.id.wtSeminarsFragment -> {
                    switchFragment(seminarsFragment, "seminars")
                    true
                }
                else -> false
            }
        }
        
        // Set default fragment to Students tab
        if (savedInstanceState == null) {
            binding.wtBottomNavigation.selectedItemId = R.id.wtStudentsFragment
        }
    }
    
    private fun switchFragment(fragment: Fragment, tag: String) {
        if (currentFragment == fragment) {
            // Fragment already displayed, no need to switch
            return
        }
        
        val transaction = childFragmentManager.beginTransaction()
        
        if (fragment.isAdded) {
            // Fragment already added, just show it
            currentFragment?.let { transaction.hide(it) }
            transaction.show(fragment)
        } else {
            // First time showing this fragment
            currentFragment?.let { transaction.hide(it) }
            transaction.add(R.id.wtFragmentContainer, fragment, tag)
        }
        
        transaction.commit()
        currentFragment = fragment
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
                if (view != null && isAdded) {
                    if (!isAvailable) {
                        // If network becomes unavailable, show message
                        networkStatusText?.visibility = View.VISIBLE
                    } else {
                        // When network becomes available, hide message
                        networkStatusText?.visibility = View.GONE
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
            R.id.wtStudentsFragment -> getString(R.string.title_students)
            R.id.wtRegisterFragment -> getString(R.string.title_register)
            R.id.wtLessonsFragment -> getString(R.string.title_lesson_schedule)
            R.id.wtSeminarsFragment -> getString(R.string.title_seminars)
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

    /**
     * Update the lessons in the calendar
     * Note: This method is intentionally empty as the calendar view is updated 
     * automatically through the connection between WTLessonsViewModel and 
     * CalendarViewModel established in MainActivity
     * 
     * @param lessons The list of lessons (parameter is required by the interface but not used here)
     */
    fun updateLessons(@Suppress("UNUSED_PARAMETER") lessons: List<WTLesson>) {
        // No action needed in this fragment
    }

    /**
     * Get the current list of lessons from the lessons fragment
     * This is used by other fragments to calculate end dates
     * @return List of lessons
     */
    fun getLessons(): List<WTLesson> {
        val lessonsFragment = childFragmentManager.findFragmentByTag("lessons") as? WTLessonsFragment
        return lessonsFragment?.getLessons() ?: emptyList()
    }
    
    /**
     * Calculate the end date after a specific number of lessons
     * This uses the lesson schedule to determine how many weeks are needed
     * 
     * @param startDate The calendar containing the start date
     * @param lessonCount The number of lessons to attend
     * @param lessons The list of available lessons
     * @return The calculated end date
     */
    fun calculateEndDateAfterLessons(
        startDate: Calendar,
        lessonCount: Int,
        lessons: List<WTLesson>
    ): Date {
        // If no lessons defined or lesson count is 0, default to 8 weeks
        if (lessons.isEmpty() || lessonCount <= 0) {
            val calendar = Calendar.getInstance()
            calendar.time = startDate.time
            calendar.add(Calendar.WEEK_OF_YEAR, 8)
            return calendar.time
        }
        
        // Count how many lessons occur each week
        val lessonsPerWeek = lessons.size
        
        // Calculate how many weeks needed
        val weeksNeeded = if (lessonsPerWeek > 0) {
            Math.ceil(lessonCount.toDouble() / lessonsPerWeek).toInt()
        } else {
            8 // Default to 8 weeks if no lessons per week
        }
        
        // Calculate end date
        val calendar = Calendar.getInstance()
        calendar.time = startDate.time
        calendar.add(Calendar.WEEK_OF_YEAR, weeksNeeded)
        
        return calendar.time
    }

    /**
     * Calculate the end date after N lessons from the start date
     * @param startDate The starting date to search from
     * @param lessons The list of weekly lessons to check against
     * @param lessonCount The number of lessons to count (default is 8)
     * @return The date after the specified number of lessons
     */
    fun calculateEndDateAfterNLessons(
        startDate: Calendar,
        lessons: List<WTLesson>,
        lessonCount: Int = 8
    ): Date {
        // Since CalendarViewModel is not easily accessible here, we implement the logic directly
        
        // If there are no lessons, return a date 8 weeks after the start date
        if (lessons.isEmpty()) {
            val endCalendar = Calendar.getInstance()
            endCalendar.time = startDate.time
            endCalendar.add(Calendar.WEEK_OF_YEAR, 8)
            return endCalendar.time
        }
        
        // Clone the start date to avoid modifying the original
        val currentDate = Calendar.getInstance()
        currentDate.time = startDate.time
        
        // Keep track of how many lessons we've found
        var lessonsFound = 0
        
        // Try for up to a year to find enough lessons
        for (day in 0 until 365) {
            // Get the day of week (1 = Sunday, 7 = Saturday)
            val dayOfWeek = currentDate.get(Calendar.DAY_OF_WEEK)
            
            // Check if there's a lesson on this day
            for (lesson in lessons) {
                // Convert WTLesson day format to Calendar.DAY_OF_WEEK
                // WTLesson uses: 0 = Monday, 6 = Sunday
                // Calendar uses: 1 = Sunday, 2 = Monday, ..., 7 = Saturday
                val lessonDayOfWeek = when (lesson.dayOfWeek) {
                    0 -> Calendar.MONDAY
                    1 -> Calendar.TUESDAY
                    2 -> Calendar.WEDNESDAY
                    3 -> Calendar.THURSDAY
                    4 -> Calendar.FRIDAY
                    5 -> Calendar.SATURDAY
                    6 -> Calendar.SUNDAY
                    else -> -1 // Invalid day
                }
                
                // If this is a lesson day
                if (dayOfWeek == lessonDayOfWeek) {
                    // We found a lesson day
                    lessonsFound++
                    
                    // If we've found enough lessons, return this date
                    if (lessonsFound >= lessonCount) {
                        return currentDate.time
                    }
                    
                    // Break the inner loop since we've already counted this day's lesson
                    break
                }
            }
            
            // Move to the next day
            currentDate.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        // If not enough lessons found within a year, fallback to 8 weeks after start
        val fallbackCalendar = Calendar.getInstance()
        fallbackCalendar.time = startDate.time
        fallbackCalendar.add(Calendar.WEEK_OF_YEAR, 8)
        return fallbackCalendar.time
    }

    companion object {
        fun newInstance() = WTRegistryFragment()
    }
} 