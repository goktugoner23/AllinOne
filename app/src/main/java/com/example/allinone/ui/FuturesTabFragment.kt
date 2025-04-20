package com.example.allinone.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.R
import com.example.allinone.adapters.BinanceFuturesAdapter
import com.example.allinone.api.BinanceApiClient
import com.example.allinone.data.BinanceBalance
import com.example.allinone.data.BinanceFutures
import com.example.allinone.databinding.FragmentFuturesTabBinding
import com.example.allinone.viewmodels.InvestmentsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class FuturesTabFragment : Fragment() {
    private var _binding: FragmentFuturesTabBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InvestmentsViewModel by viewModels({ requireParentFragment() })

    private lateinit var binanceApiClient: BinanceApiClient
    private lateinit var futuresAdapter: BinanceFuturesAdapter

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)
    private val numberFormatter = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 8
    }

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

        // Initialize Binance API client
        binanceApiClient = BinanceApiClient()

        // Setup RecyclerView and adapter
        setupRecyclerView()

        // Setup swipe refresh
        setupSwipeRefresh()

        // Set initial UI state
        showLoading(true)

        // Force an initial refresh
        refreshData()
    }

    private fun setupRecyclerView() {
        futuresAdapter = BinanceFuturesAdapter { position ->
            // Handle position click
            Toast.makeText(context, "Position: ${position.symbol}", Toast.LENGTH_SHORT).show()
        }

        binding.positionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = futuresAdapter
        }
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

    private fun showLoading(isLoading: Boolean) {
        if (_binding == null) return

        if (isLoading) {
            binding.loadingProgress.visibility = View.VISIBLE
            binding.emptyStateText.visibility = View.GONE
        } else {
            binding.loadingProgress.visibility = View.GONE
        }
    }

    private fun refreshData() {
        // Safely check if binding is still valid before starting operation
        if (_binding == null) return
        binding.futuresSwipeRefreshLayout.isRefreshing = true
        showLoading(true)

        // Use viewLifecycleOwner.lifecycleScope instead of lifecycleScope to tie to view lifecycle
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Fetch Binance Futures data
                val balances = binanceApiClient.getAccountBalance()
                val positions = binanceApiClient.getPositionInformation()

                // Update UI with the fetched data
                updateUI(balances, positions)

                // Add a small delay to make the refresh animation visible
                delay(500)

                // Check if binding is still valid after async operation
                if (_binding != null) {
                    binding.futuresSwipeRefreshLayout.isRefreshing = false
                    showLoading(false)
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
                    showLoading(false)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error refreshing data: ${e.message}", Toast.LENGTH_SHORT).show()
                        binding.emptyStateText.visibility = View.VISIBLE
                        binding.emptyStateText.text = "Error loading data: ${e.message}"
                    }
                }
            }
        }
    }

    private suspend fun updateUI(balances: List<BinanceBalance>, positions: List<BinanceFutures>) {
        if (_binding == null) return

        withContext(Dispatchers.Main) {
            // Update positions list
            futuresAdapter.submitList(positions)

            // Show empty state if no positions
            binding.emptyStateText.visibility = if (positions.isEmpty()) View.VISIBLE else View.GONE

            // Find USDT balance for summary
            val usdtBalance = balances.find { it.asset == "USDT" }

            // Calculate total unrealized PNL
            val totalPnl = positions.sumOf { it.unRealizedProfit }

            // Update summary card
            usdtBalance?.let {
                binding.balanceValueText.text = currencyFormatter.format(it.balance)
                binding.availableBalanceValueText.text = currencyFormatter.format(it.availableBalance)
                binding.marginBalanceValueText.text = currencyFormatter.format(it.crossWalletBalance)
            }

            // Update PNL with color
            binding.pnlValueText.text = if (totalPnl >= 0) "+${currencyFormatter.format(totalPnl)}"
                                       else currencyFormatter.format(totalPnl)
            binding.pnlValueText.setTextColor(
                if (totalPnl >= 0) Color.parseColor("#4CAF50") // Green
                else Color.parseColor("#F44336") // Red
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}