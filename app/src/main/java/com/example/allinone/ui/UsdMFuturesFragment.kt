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

class UsdmFuturesFragment : Fragment() {
    private var _binding: FragmentFuturesTabBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InvestmentsViewModel by viewModels({ requireParentFragment().requireParentFragment() })

    private lateinit var binanceApiClient: BinanceApiClient
    private lateinit var futuresAdapter: BinanceFuturesAdapter

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2  // Only show 2 decimal places for USDT values
    }
    // Don't use currency formatter for prices to show exact values from Binance
    private val priceFormatter = NumberFormat.getInstance(Locale.US).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 20  // Allow for maximum precision
        isGroupingUsed = true  // Keep the thousands separator
    }
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
        futuresAdapter = BinanceFuturesAdapter(
            onItemClick = { position ->
                // Handle position click
                Toast.makeText(context, "Position: ${position.symbol}", Toast.LENGTH_SHORT).show()
            },
            // We'll update the prices later when they're fetched
            prices = emptyMap()
        )

        binding.positionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = futuresAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.futuresSwipeRefreshLayout.setOnRefreshListener {
            // Add a toast to indicate refresh is happening
            Toast.makeText(context, "Refreshing USD-M futures data...", Toast.LENGTH_SHORT).show()
            Log.d("UsdmFuturesFragment", "Pull-to-refresh triggered")
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
                Log.d("UsdmFuturesFragment", "Fetching USD-M account balance...")
                val balances = binanceApiClient.getAccountBalance()
                Log.d("UsdmFuturesFragment", "Fetched ${balances.size} balances")

                Log.d("UsdmFuturesFragment", "Fetching USD-M position information...")
                val positions = binanceApiClient.getPositionInformation()
                Log.d("UsdmFuturesFragment", "Fetched ${positions.size} positions")

                Log.d("UsdmFuturesFragment", "Fetching USD-M latest prices...")
                val prices = binanceApiClient.getLatestPrices()
                Log.d("UsdmFuturesFragment", "Fetched ${prices.size} prices")

                // Update UI with the fetched data
                updateUI(balances, positions, prices)

                // Add a small delay to make the refresh animation visible
                delay(500)

                // Check if binding is still valid after async operation
                if (_binding != null) {
                    binding.futuresSwipeRefreshLayout.isRefreshing = false
                    showLoading(false)
                    Log.d("UsdmFuturesFragment", "Refreshed data successfully")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Job cancellation is normal when leaving the fragment, no need to show error
                Log.d("UsdmFuturesFragment", "Refresh job was cancelled")
            } catch (e: Exception) {
                Log.e("UsdmFuturesFragment", "Error refreshing data: ${e.message}")
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

    private suspend fun updateUI(balances: List<BinanceBalance>, positions: List<BinanceFutures>, prices: Map<String, Double>) {
        if (_binding == null) return

        withContext(Dispatchers.Main) {
            // Filter for USD-M futures only
            val usdmPositions = positions.filter { it.futuresType == "USD-M" }

            // Create a new adapter with the latest prices
            futuresAdapter = BinanceFuturesAdapter(
                onItemClick = { position ->
                    // Handle position click
                    Toast.makeText(context, "Position: ${position.symbol}", Toast.LENGTH_SHORT).show()
                },
                prices = prices
            )

            // Set the new adapter
            binding.positionsRecyclerView.adapter = futuresAdapter

            // Update positions list
            futuresAdapter.submitList(usdmPositions)

            // Show empty state if no positions
            binding.emptyStateText.visibility = if (usdmPositions.isEmpty()) View.VISIBLE else View.GONE
            if (usdmPositions.isEmpty()) {
                binding.emptyStateText.text = "No open USD-M futures positions"
            }

            // Log all balances for debugging
            Log.d("UsdmFuturesFragment", "USD-M Balances: ${balances.joinToString { "${it.asset}=${it.balance}" }}")

            // Find USDT balance for summary
            val usdtBalance = balances.find { it.asset == "USDT" && it.futuresType == "USD-M" }

            // Log the balance we're using for reference
            if (usdtBalance != null) {
                // If we have USDT balance, log it
                Log.d("UsdmFuturesFragment", "Found USDT balance: ${usdtBalance.balance} USDT")
            } else if (balances.isNotEmpty()) {
                // If we don't have USDT, log the first available balance
                val firstBalance = balances.first()
                Log.d("UsdmFuturesFragment", "No USDT balance found, first balance is: ${firstBalance.asset} = ${firstBalance.balance}")
            }

            // Calculate total unrealized PNL
            val totalPnl = usdmPositions.sumOf { it.unRealizedProfit }

            // Calculate total USDT value of all balances
            var totalUsdtValue = 0.0

            // Log all prices for debugging
            Log.d("UsdmFuturesFragment", "Available prices: ${prices.keys.joinToString()}")

            // Calculate USDT value for each non-zero balance
            for (balance in balances) {
                if (balance.balance > 0) {
                    val asset = balance.asset
                    // For USD-M futures, USDT balance is already in USDT
                    if (asset == "USDT") {
                        totalUsdtValue += balance.balance
                        Log.d("UsdmFuturesFragment", "$asset: ${balance.balance} USDT (direct)")
                    } else {
                        val price = prices[asset] ?: 0.0
                        val usdtValue = balance.balance * price
                        Log.d("UsdmFuturesFragment", "$asset: ${balance.balance} * $price = $usdtValue USDT")
                        totalUsdtValue += usdtValue
                    }
                }
            }

            // Calculate margin balance as (wallet balance + unrealized PNL) in USDT
            // This is the correct formula used by Binance
            val marginBalanceUsdt = totalUsdtValue + totalPnl

            // Update the UI with the correct balance information
            // 1. At the top: Margin Balance
            binding.balanceValueText.text = currencyFormatter.format(marginBalanceUsdt)
            Log.d("UsdmFuturesFragment", "Setting top balance text to margin balance: ${currencyFormatter.format(marginBalanceUsdt)}")

            // 2. Below: Wallet Balance (total balance)
            binding.marginBalanceValueText.text = currencyFormatter.format(totalUsdtValue)
            Log.d("UsdmFuturesFragment", "Setting margin balance text to wallet balance: ${currencyFormatter.format(totalUsdtValue)}")

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
