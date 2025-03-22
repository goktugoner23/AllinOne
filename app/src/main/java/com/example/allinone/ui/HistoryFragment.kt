package com.example.allinone.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.HistoryAdapter
import com.example.allinone.data.HistoryItem
import com.example.allinone.databinding.FragmentHistoryBinding
import com.example.allinone.viewmodels.HistoryViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()
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
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Observe history items
        observeHistoryItems()
        
        // Setup refresh
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshData()
            binding.swipeRefresh.isRefreshing = false
        }
    }
    
    private fun setupToolbar() {
        // Set up toolbar title
        binding.toolbarTitle.text = getString(R.string.history)
        
        // Setup menu button to open drawer
        binding.menuButton.setOnClickListener {
            // openDrawer()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onItemClick = { /* We could handle item clicks here */ },
            onDeleteClick = { item -> showDeleteConfirmation(item) }
        )
        
        binding.recyclerView.apply {
            adapter = this@HistoryFragment.adapter
        }
    }
    
    private fun observeHistoryItems() {
        lifecycleScope.launch {
            viewModel.historyItems.collect { items ->
                adapter.submitList(items)
                
                // Show empty state if needed
                binding.emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
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