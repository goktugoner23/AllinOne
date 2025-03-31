package com.example.allinone.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.HistoryAdapter
import com.example.allinone.data.HistoryItem
import com.example.allinone.databinding.FragmentHistoryBinding
import com.example.allinone.viewmodels.HistoryViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.transition.TransitionManager
import android.transition.Slide
import android.view.Gravity

class HistoryFragment : BaseFragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter
    private val TAG = "HistoryFragment"
    private var searchMenuItem: MenuItem? = null
    private var allHistoryItems: List<HistoryItem> = emptyList()
    private var menuProvider: MenuProvider? = null
    private val activeFilters = mutableSetOf<HistoryItem.ItemType?>()
    
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
        
        // Make sure the action bar is visible
        (activity as? AppCompatActivity)?.supportActionBar?.show()
        
        // Set the action bar title
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.history)
        
        // Setup Filter Chips
        setupFilterChips()
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Setup search menu
        setupMenu()
        
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

    private fun setupFilterChips() {
        // All filter
        binding.filterAll?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Clear all other filters
                binding.filterIncome?.isChecked = false
                binding.filterExpense?.isChecked = false
                binding.filterRegistrations?.isChecked = false
                binding.filterNotes?.isChecked = false
                activeFilters.clear()
                applyFilters()
            } else {
                // If no other filter is checked, recheck this one
                if (activeFilters.isEmpty() && 
                    binding.filterIncome?.isChecked != true && 
                    binding.filterExpense?.isChecked != true && 
                    binding.filterRegistrations?.isChecked != true && 
                    binding.filterNotes?.isChecked != true) {
                    binding.filterAll?.isChecked = true
                }
            }
        }
        
        // Income filter
        binding.filterIncome?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.filterAll?.isChecked = false
                activeFilters.add(HistoryItem.ItemType.TRANSACTION_INCOME)
            } else {
                activeFilters.remove(HistoryItem.ItemType.TRANSACTION_INCOME)
            }
            applyFilters()
        }
        
        // Expense filter
        binding.filterExpense?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.filterAll?.isChecked = false
                activeFilters.add(HistoryItem.ItemType.TRANSACTION_EXPENSE)
            } else {
                activeFilters.remove(HistoryItem.ItemType.TRANSACTION_EXPENSE)
            }
            applyFilters()
        }
        
        // Registrations filter
        binding.filterRegistrations?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.filterAll?.isChecked = false
                activeFilters.add(HistoryItem.ItemType.REGISTRATION)
            } else {
                activeFilters.remove(HistoryItem.ItemType.REGISTRATION)
            }
            applyFilters()
        }
        
        // Notes filter
        binding.filterNotes?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.filterAll?.isChecked = false
                activeFilters.add(HistoryItem.ItemType.NOTE)
            } else {
                activeFilters.remove(HistoryItem.ItemType.NOTE)
            }
            applyFilters()
        }
    }
    
    private fun applyFilters() {
        val searchView = searchMenuItem?.actionView as? SearchView
        val searchQuery = if (searchView?.isIconified == false && !searchView.query.isNullOrEmpty()) {
            searchView.query.toString()
        } else {
            ""
        }
        
        val filteredItems = if (binding.filterAll?.isChecked == true || activeFilters.isEmpty()) {
            // If "All" is selected or no filters are active
            if (searchQuery.isBlank()) {
                allHistoryItems
            } else {
                // Apply search filter to all items
                allHistoryItems.filter { item ->
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.description.contains(searchQuery, ignoreCase = true) ||
                    item.amount?.toString()?.contains(searchQuery) == true
                }
            }
        } else {
            // Apply both type filters and search filter
            allHistoryItems.filter { item ->
                val matchesType = when (item.itemType) {
                    HistoryItem.ItemType.TRANSACTION -> {
                        // For backward compatibility, check amount
                        val isIncome = item.amount != null && item.amount > 0
                        if (isIncome) {
                            activeFilters.contains(HistoryItem.ItemType.TRANSACTION_INCOME)
                        } else {
                            activeFilters.contains(HistoryItem.ItemType.TRANSACTION_EXPENSE)
                        }
                    }
                    HistoryItem.ItemType.TRANSACTION_INCOME -> 
                        activeFilters.contains(HistoryItem.ItemType.TRANSACTION_INCOME)
                    HistoryItem.ItemType.TRANSACTION_EXPENSE -> 
                        activeFilters.contains(HistoryItem.ItemType.TRANSACTION_EXPENSE)
                    else -> activeFilters.contains(item.itemType)
                }
                
                val matchesSearch = if (searchQuery.isBlank()) {
                    true
                } else {
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.description.contains(searchQuery, ignoreCase = true) ||
                    item.amount?.toString()?.contains(searchQuery) == true
                }
                
                matchesType && matchesSearch
            }
        }
        
        adapter.submitList(filteredItems)
        
        // Show empty state if needed
        binding.emptyState.visibility = if (filteredItems.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (filteredItems.isEmpty()) View.GONE else View.VISIBLE
    }
    
    private fun setupMenu() {
        // Remove any existing menu provider
        menuProvider?.let {
            requireActivity().removeMenuProvider(it)
        }
        
        // Create a new menu provider
        menuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Clear existing menu items to prevent duplicates
                menu.clear()
                
                menuInflater.inflate(R.menu.search_history, menu)
                searchMenuItem = menu.findItem(R.id.action_search)
                
                val searchView = searchMenuItem?.actionView as? SearchView
                searchView?.let {
                    // Set search view expanded listener
                    it.setOnSearchClickListener { _ ->
                        // Animate search view expansion
                        val slide = Slide(Gravity.END)
                        slide.duration = 200
                        TransitionManager.beginDelayedTransition((activity as AppCompatActivity).findViewById(R.id.toolbar), slide)
                    }
                    
                    // Set search view collapse listener
                    it.setOnCloseListener { 
                        // Animate search view collapse
                        val slide = Slide(Gravity.END)
                        slide.duration = 200
                        TransitionManager.beginDelayedTransition((activity as AppCompatActivity).findViewById(R.id.toolbar), slide)
                        
                        // Reset the history list
                        resetHistoryList()
                        true
                    }
                    
                    // Set up query listener
                    it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?): Boolean {
                            query?.let { searchQuery ->
                                filterHistoryItems(searchQuery)
                            }
                            
                            // Hide keyboard
                            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                            imm.hideSoftInputFromWindow(it.windowToken, 0)
                            
                            return true
                        }
                        
                        override fun onQueryTextChange(newText: String?): Boolean {
                            newText?.let { searchQuery ->
                                filterHistoryItems(searchQuery)
                            }
                            return true
                        }
                    })
                    
                    // Customize search view appearance
                    val searchEditText = it.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
                    searchEditText?.apply {
                        setHintTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                        setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                        hint = getString(R.string.search_hint)
                        imeOptions = EditorInfo.IME_ACTION_SEARCH
                        
                        // Set X button to reset search
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                                // Hide keyboard
                                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                imm.hideSoftInputFromWindow(this.windowToken, 0)
                                return@setOnEditorActionListener true
                            }
                            false
                        }
                    }

                    // Set search icon color to white (expanded state)
                    val searchIcon = it.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
                    searchIcon?.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.white),
                        android.graphics.PorterDuff.Mode.SRC_IN)
                    
                    // Make sure search icon is visible and properly sized
                    searchIcon?.apply {
                        visibility = View.VISIBLE
                        val iconSize = resources.getDimensionPixelSize(androidx.appcompat.R.dimen.abc_dropdownitem_icon_width)
                        val params = layoutParams
                        params.width = iconSize
                        params.height = iconSize
                        layoutParams = params
                    }

                    // Set close button color to white
                    val closeButton = it.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
                    closeButton?.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.white),
                        android.graphics.PorterDuff.Mode.SRC_IN)
                }
            }
            
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_search -> {
                        // The search icon click is handled by the SearchView
                        true
                    }
                    else -> false
                }
            }
        }
        
        // Add the menu provider with STARTED lifecycle to ensure it's removed when fragment is not visible
        requireActivity().addMenuProvider(menuProvider!!, viewLifecycleOwner, Lifecycle.State.STARTED)
    }
    
    private fun resetHistoryList() {
        if (binding.filterAll?.isChecked == true || activeFilters.isEmpty()) {
            adapter.submitList(allHistoryItems)
        } else {
            applyFilters()
        }
        
        // Show empty state if needed
        val currentList = adapter.currentList
        binding.emptyState.visibility = if (currentList.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (currentList.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun filterHistoryItems(query: String) {
        applyFilters()
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
                
                // Store all items for filtering
                allHistoryItems = items
                
                // Apply current filters
                applyFilters()
            }
        }
    }
    
    private fun showDeleteConfirmation(item: HistoryItem) {
        // Create title and message based on item type
        val title = "Delete ${item.itemType.name.lowercase().replaceFirstChar { it.uppercase() }}?"
        val message = when (item.itemType) {
            HistoryItem.ItemType.REGISTRATION -> "Deleting this registration will also remove any related payment transactions."
            HistoryItem.ItemType.TRANSACTION,
            HistoryItem.ItemType.TRANSACTION_INCOME,
            HistoryItem.ItemType.TRANSACTION_EXPENSE -> {
                if (item.title.contains("Registration")) {
                    "Deleting this transaction might affect registration records. Do you want to proceed?"
                } else {
                    "Are you sure you want to delete this transaction?"
                }
            }
            HistoryItem.ItemType.INVESTMENT -> "Are you sure you want to delete this investment?"
            HistoryItem.ItemType.NOTE -> "Are you sure you want to delete this note?"
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
        
        // Remove the menu provider when the view is destroyed
        menuProvider?.let {
            try {
                requireActivity().removeMenuProvider(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing menu provider: ${e.message}")
            }
        }
        
        _binding = null
    }
} 