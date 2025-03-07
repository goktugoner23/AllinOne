package com.example.allinone.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.HistoryAdapter
import com.example.allinone.data.HistoryItem
import com.example.allinone.databinding.FragmentHistoryBinding
import com.example.allinone.viewmodels.HistoryViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: HistoryViewModel
    private lateinit var adapter: HistoryAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set title in main app bar
        (requireActivity() as AppCompatActivity).supportActionBar?.title = "History"
        
        // Hide the toolbar
        activity?.findViewById<View>(R.id.toolbar)?.visibility = View.GONE
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        
        // Setup RecyclerView
        adapter = HistoryAdapter { historyItem ->
            // Handle delete action
            viewModel.deleteItem(historyItem)
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HistoryFragment.adapter
        }
        
        // Observe history items
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.historyItems.collectLatest { items ->
                adapter.submitList(items)
                
                // Show empty state if needed
                binding.emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
            }
        }
        
        // Setup refresh
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshAllData()
            binding.swipeRefresh.isRefreshing = false
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Show the toolbar again when leaving
        activity?.findViewById<View>(R.id.toolbar)?.visibility = View.VISIBLE
        _binding = null
    }
} 