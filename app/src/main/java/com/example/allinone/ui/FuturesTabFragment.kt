package com.example.allinone.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.allinone.R
import com.example.allinone.databinding.FragmentFuturesTabBinding
import com.example.allinone.viewmodels.InvestmentsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FuturesTabFragment : Fragment() {
    private var _binding: FragmentFuturesTabBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InvestmentsViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFuturesTabBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSwipeRefresh()
        
        // Force an initial refresh
        refreshData()
    }
    
    private fun setupSwipeRefresh() {
        binding.futuresSwipeRefreshLayout.setOnRefreshListener {
            // Add a toast to indicate refresh is happening
            Toast.makeText(context, "Refreshing futures data...", Toast.LENGTH_SHORT).show()
            Log.d("FuturesTabFragment", "Pull-to-refresh triggered")
            refreshData()
        }
        binding.futuresSwipeRefreshLayout.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorAccent,
            R.color.colorPrimaryDark
        )
    }
    
    private fun refreshData() {
        // Safely check if binding is still valid before starting operation
        if (_binding == null) return
        binding.futuresSwipeRefreshLayout.isRefreshing = true
        
        // Use viewLifecycleOwner.lifecycleScope instead of lifecycleScope to tie to view lifecycle
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.refreshData()
                // Add a small delay to make the refresh animation visible
                delay(500)
                // Check if binding is still valid after async operation
                if (_binding != null) {
                    binding.futuresSwipeRefreshLayout.isRefreshing = false
                
                    // In the future, we'll have specific futures data loading here
                    Log.d("FuturesTabFragment", "Refreshed data successfully")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Job cancellation is normal when leaving the fragment, no need to show error
                Log.d("FuturesTabFragment", "Refresh job was cancelled")
            } catch (e: Exception) {
                Log.e("FuturesTabFragment", "Error refreshing data: ${e.message}")
                // Check if binding is still valid after async operation
                if (_binding != null) {
                    binding.futuresSwipeRefreshLayout.isRefreshing = false
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Error refreshing data: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 