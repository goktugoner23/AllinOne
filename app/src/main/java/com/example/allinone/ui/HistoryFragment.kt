package com.example.allinone.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.HistoryAdapter
import com.example.allinone.data.HistoryItem
import com.example.allinone.databinding.FragmentHistoryBinding
import com.example.allinone.viewmodels.HistoryViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HistoryFragment : BaseFragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter
    private val TAG = "HistoryFragment"
    
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
        Log.d(TAG, "onViewCreated called")
        
        // Setup custom toolbar
        setupToolbar()
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Observe history items
        observeHistoryItems()
        
        // Setup refresh
        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "Manual refresh triggered")
            viewModel.refreshData()
            binding.swipeRefresh.isRefreshing = false
        }
        
        // Initial load
        binding.swipeRefresh.isRefreshing = true
        viewModel.refreshData()
        binding.swipeRefresh.isRefreshing = false
    }
    
    private fun setupToolbar() {
        Log.d(TAG, "Setting up toolbar")
        // Set up toolbar title
        binding.toolbarTitle.text = getString(R.string.history)
        
        // Setup menu button to open drawer
        binding.menuButton.setOnClickListener {
            Log.d(TAG, "Menu button clicked, opening drawer")
            openDrawer()
        }
    }
    
    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView")
        adapter = HistoryAdapter(
            onItemClick = { 
                Log.d(TAG, "History item clicked: ${it.title}")
            },
            onDeleteClick = { item -> 
                Log.d(TAG, "Delete clicked for item: ${item.title}")
                showDeleteConfirmation(item) 
            }
        )
        
        binding.recyclerView.apply {
            this.adapter = this@HistoryFragment.adapter
            layoutManager = LinearLayoutManager(context)
        }
    }
    
    private fun observeHistoryItems() {
        Log.d(TAG, "Starting to observe history items")
        lifecycleScope.launch {
            viewModel.historyItems.collect { items ->
                Log.d(TAG, "Received ${items.size} history items")
                adapter.submitList(items)
                
                // Show empty state if needed
                binding.emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                
                if (items.isEmpty()) {
                    Log.d(TAG, "History list is empty, showing empty state")
                    // Show a message to the user
                    Snackbar.make(binding.root, R.string.no_history_items, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showDeleteConfirmation(item: HistoryItem) {
        // Create title and message based on item type
        val title = "Delete ${item.itemType.name.lowercase().replaceFirstChar { it.uppercase() }}?"
        val message = when (item.itemType) {
            HistoryItem.ItemType.REGISTRATION -> "Deleting this registration will also remove any related payment transactions."
            HistoryItem.ItemType.TRANSACTION -> {
                if (item.title.contains("Registration")) {
                    "Deleting this transaction might affect registration records. Do you want to proceed?"
                } else {
                    "Are you sure you want to delete this transaction?"
                }
            }
            else -> "Are you sure you want to delete this item?"
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Delete") { _, _ ->
                deleteItem(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteItem(item: HistoryItem) {
        viewModel.deleteItem(item)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 