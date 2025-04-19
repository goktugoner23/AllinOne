package com.example.allinone.ui

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.allinone.R
import com.example.allinone.adapters.InvestmentAdapter
import com.example.allinone.adapters.InvestmentImageAdapter
import com.example.allinone.data.Investment
import com.example.allinone.databinding.DialogEditInvestmentBinding
import com.example.allinone.databinding.FragmentInvestmentsTabBinding
import com.example.allinone.viewmodels.InvestmentsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

class InvestmentsTabFragment : Fragment() {
    private var _binding: FragmentInvestmentsTabBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InvestmentsViewModel by viewModels({ requireParentFragment() })
    private lateinit var adapter: InvestmentAdapter
    private lateinit var imageAdapter: InvestmentImageAdapter
    private val selectedImages = mutableListOf<Uri>()

    private val getContent = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris?.let { selectedUris ->
            selectedUris.forEach { uri ->
                try {
                    // Take persistable permission for the URI
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            selectedImages.addAll(selectedUris)
            imageAdapter.submitList(selectedImages.toList())
            dialogBinding?.imagesRecyclerView?.visibility = View.VISIBLE
        }
    }

    private var dialogBinding: DialogEditInvestmentBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvestmentsTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
        
        // Force a data refresh when the fragment is created
        refreshData()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorAccent,
            R.color.colorPrimaryDark
        )
    }

    private fun refreshData() {
        // Safely check if binding is still valid before starting operation
        if (_binding == null) return
        binding.swipeRefreshLayout.isRefreshing = true
        
        // Use viewLifecycleOwner.lifecycleScope instead of lifecycleScope to tie to view lifecycle
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.refreshData()
                // Add a small delay to make the refresh animation visible
                delay(500)
                // Check if binding is still valid after async operation
                if (_binding != null) {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Job cancellation is normal when leaving the fragment, no need to show error
                Log.d("InvestmentsTabFragment", "Refresh job was cancelled")
            } catch (e: Exception) {
                Log.e("InvestmentsTabFragment", "Error refreshing data: ${e.message}")
                // Check if binding is still valid after async operation
                if (_binding != null) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Error refreshing data: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = InvestmentAdapter(
            onItemClick = { investment -> showInvestmentDetails(investment) },
            onItemLongClick = { investment -> showDeleteConfirmation(investment) },
            onImageClick = { uri -> showFullscreenImage(uri.toString()) }
        )

        binding.investmentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@InvestmentsTabFragment.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun observeViewModel() {
        viewModel.allInvestments.observe(viewLifecycleOwner) { investments ->
            // Sort investments by date (newest first) and update the adapter
            val sortedInvestments = investments.sortedByDescending { it.date }
            adapter.submitList(sortedInvestments)
            
            // Show/hide empty state
            binding.emptyStateText.visibility = if (investments.isEmpty()) View.VISIBLE else View.GONE
            
            // Update summary information
            val totalAmount = investments.sumOf { it.amount }
            binding.totalInvestmentsText.text = 
                "Total Investments: ₺${String.format("%,.2f", totalAmount)}"
            binding.investmentCountText.text = "Number of Investments: ${investments.size}"
            
            // Enhanced logging for debugging
            Log.d("InvestmentsTabFragment", "Loaded ${investments.size} investments, total: ₺$totalAmount")
            investments.forEach { investment ->
                Log.d("InvestmentsTabFragment", "Investment: ID=${investment.id}, Name=${investment.name}, Amount=${investment.amount}, Type=${investment.type}, Date=${investment.date}")
            }
        }
        
        // Observe error messages from ViewModel
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Log.e("InvestmentsTabFragment", "ViewModel error: $message")
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
        
        // Observe add status
        viewModel.addStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is InvestmentsViewModel.AddStatus.SUCCESS -> {
                    Log.d("InvestmentsTabFragment", "Investment added successfully")
                    refreshData()
                }
                is InvestmentsViewModel.AddStatus.ERROR -> {
                    Log.e("InvestmentsTabFragment", "Error adding investment")
                }
                else -> { /* Do nothing */ }
            }
        }
        
        // Observe update status
        viewModel.updateStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is InvestmentsViewModel.UpdateStatus.SUCCESS -> {
                    Log.d("InvestmentsTabFragment", "Investment updated successfully")
                    refreshData()
                }
                is InvestmentsViewModel.UpdateStatus.ERROR -> {
                    Log.e("InvestmentsTabFragment", "Error updating investment")
                }
                else -> { /* Do nothing */ }
            }
        }
        
        // Observe delete status
        viewModel.deleteStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is InvestmentsViewModel.DeleteStatus.SUCCESS -> {
                    Log.d("InvestmentsTabFragment", "Investment deleted successfully")
                    refreshData()
                }
                is InvestmentsViewModel.DeleteStatus.ERROR -> {
                    Log.e("InvestmentsTabFragment", "Error deleting investment")
                }
                else -> { /* Do nothing */ }
            }
        }
    }

    private fun showInvestmentDetails(investment: Investment) {
        // This method would be implemented in the parent fragment and called through an interface
        (parentFragment as? InvestmentsFragment)?.showInvestmentDetails(investment)
    }

    private fun showDeleteConfirmation(investment: Investment) {
        // This method would be implemented in the parent fragment and called through an interface
        (parentFragment as? InvestmentsFragment)?.showDeleteConfirmation(investment)
    }

    private fun showFullscreenImage(uri: String?) {
        // This method would be implemented in the parent fragment and called through an interface
        (parentFragment as? InvestmentsFragment)?.showFullscreenImage(uri)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 