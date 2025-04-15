package com.example.allinone.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.allinone.R
import com.example.allinone.databinding.FragmentInstagramBusinessBinding
import com.example.allinone.ui.instagram.InstagramAskAIFragment
import com.example.allinone.ui.instagram.InstagramInsightsFragment
import com.example.allinone.ui.instagram.InstagramPostsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class InstagramBusinessFragment : BaseFragment() {
    
    private var _binding: FragmentInstagramBusinessBinding? = null
    private val binding get() = _binding!!
    
    private val postsFragment = InstagramPostsFragment()
    private val insightsFragment = InstagramInsightsFragment()
    private val askAIFragment = InstagramAskAIFragment()
    
    companion object {
        private const val TAG = "InstagramBusiness"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstagramBusinessBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set up the bottom navigation
        setupBottomNavigation()
        
        // Set default tab
        if (savedInstanceState == null) {
            showTab(R.id.tab_posts)
        }
    }
    
    private fun setupBottomNavigation() {
        try {
            // Debug properties in binding
            for (field in binding.javaClass.declaredFields) {
                Log.d(TAG, "Binding field: ${field.name} - ${field.type}")
            }
            
            // Handle tab changes using findViewById
            val bottomNav = view?.findViewById<BottomNavigationView>(R.id.instagram_bottom_navigation)
            bottomNav?.setOnItemSelectedListener { item ->
                showTab(item.itemId)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up bottom navigation: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun showTab(tabId: Int) {
        try {
            // Start a fragment transaction
            val transaction = childFragmentManager.beginTransaction()
            
            // Hide all fragments first
            childFragmentManager.fragments.forEach { fragment ->
                transaction.hide(fragment)
            }
            
            // Show or add the selected fragment
            when (tabId) {
                R.id.tab_posts -> {
                    if (!postsFragment.isAdded) {
                        transaction.add(R.id.instagram_fragment_container, postsFragment)
                    } else {
                        transaction.show(postsFragment)
                    }
                }
                
                R.id.tab_insight -> {
                    if (!insightsFragment.isAdded) {
                        transaction.add(R.id.instagram_fragment_container, insightsFragment)
                    } else {
                        transaction.show(insightsFragment)
                    }
                }
                
                R.id.tab_ask_ai -> {
                    if (!askAIFragment.isAdded) {
                        transaction.add(R.id.instagram_fragment_container, askAIFragment)
                    } else {
                        transaction.show(askAIFragment)
                    }
                }
            }
            
            // Commit the transaction
            transaction.commitNow()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing tab: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 