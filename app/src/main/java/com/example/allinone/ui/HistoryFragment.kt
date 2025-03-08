package com.example.allinone.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.HistoryAdapter
import com.example.allinone.databinding.FragmentHistoryBinding
import com.example.allinone.viewmodels.HistoryViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryFragment : BaseFragment() {
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
        
        // Setup custom toolbar
        setupToolbar()
        
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
    
    private fun setupToolbar() {
        // Set up toolbar title
        binding.toolbarTitle.text = getString(R.string.history)
        
        // Setup menu button to open drawer
        binding.menuButton.setOnClickListener {
            openDrawer()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 