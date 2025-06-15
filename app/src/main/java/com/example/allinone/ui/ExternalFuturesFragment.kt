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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.allinone.adapters.BinancePositionAdapter
import com.example.allinone.api.*
import com.example.allinone.data.BinancePosition
import com.example.allinone.databinding.FragmentFuturesTabBinding
import com.example.allinone.viewmodels.InvestmentsViewModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

class ExternalFuturesFragment : Fragment() {
    private var _binding: FragmentFuturesTabBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InvestmentsViewModel by viewModels({ requireParentFragment().requireParentFragment() })

    private lateinit var repository: ExternalBinanceRepository
    private lateinit var webSocketClient: BinanceWebSocketClient
    private lateinit var futuresAdapter: BinancePositionAdapter
    private var openOrders: List<OrderData> = emptyList()
    
    private val gson = Gson()
    
    companion object {
        private const val TAG = "ExternalFuturesFragment"
        private const val HEARTBEAT_INTERVAL = 30000L // 30 seconds
    }

    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    
    private val priceFormatter = NumberFormat.getInstance(Locale.US).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 20
        isGroupingUsed = true
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

        // Initialize repository
        repository = ExternalBinanceRepository()

        // Setup RecyclerView and adapter
        setupRecyclerView()

        // Setup swipe refresh
        setupSwipeRefresh()

        // Initialize WebSocket
        initializeWebSocket()

        // Set initial UI state
        showLoading(true)

        // Initial data fetch
        fetchInitialData()

        // Start heartbeat
        startHeartbeat()
    }

    private fun setupRecyclerView() {
        futuresAdapter = BinancePositionAdapter(
            onItemClick = { position ->
                Log.d(TAG, "Position clicked: ${position.symbol}")
                // Handle position click if needed
            },
            onTpSlClick = { position ->
                Log.d(TAG, "TP/SL clicked for position: ${position.symbol}")
                // Handle TP/SL click if needed
            }
        )

        binding.positionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = futuresAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.futuresSwipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }
    }

    private fun initializeWebSocket() {
        webSocketClient = BinanceWebSocketClient(
            onMessage = { type, data ->
                requireActivity().runOnUiThread {
                    handleWebSocketMessage(type, data)
                }
            },
            onConnectionChange = { connected ->
                requireActivity().runOnUiThread {
                    updateConnectionStatus(connected)
                }
            }
        )
        
        // Connect WebSocket
        webSocketClient.connect()
        
        // Subscribe to futures-specific data streams
        lifecycleScope.launch {
            delay(1000) // Wait for connection to establish
            if (webSocketClient.isConnected()) {
                webSocketClient.subscribeToPositionUpdates()
                webSocketClient.subscribeToOrderUpdates()
                webSocketClient.subscribeToBalanceUpdates()
            }
        }
    }

    private fun handleWebSocketMessage(type: String, data: JsonObject) {
        Log.d(TAG, "WebSocket message received: $type")
        
        when (type) {
            "welcome" -> {
                Log.d(TAG, "Welcome message received")
                val message = data.get("message")?.asString
                if (message != null) {
                    Log.d(TAG, "Welcome: $message")
                }
            }
            "positions_update" -> {
                Log.d(TAG, "Position update: $data")
                // Parse position update and refresh UI
                lifecycleScope.launch {
                    refreshPositions()
                }
            }
            "order_update" -> {
                Log.d(TAG, "Order update: $data")
                // Parse order update and refresh orders
                lifecycleScope.launch {
                    refreshOrders()
                }
            }
            "balance_update" -> {
                Log.d(TAG, "Balance update: $data")
                // Handle balance updates
                lifecycleScope.launch {
                    refreshAccountData()
                }
            }
            "ticker" -> {
                Log.d(TAG, "Ticker update: $data")
                // Handle ticker updates if needed
            }
            "connection" -> {
                val status = data.get("status")?.asString
                Log.d(TAG, "Connection status: $status")
            }
            "pong" -> {
                Log.d(TAG, "Heartbeat pong received")
            }
            "error" -> {
                val error = data.get("error")?.asString ?: "Unknown error"
                Log.e(TAG, "WebSocket error: $error")
                showError("WebSocket error: $error")
            }
            else -> {
                Log.d(TAG, "Unknown message type: $type")
            }
        }
    }

    private fun updateConnectionStatus(connected: Boolean) {
        Log.d(TAG, "Connection status changed: $connected")
        
        if (connected) {
            // Show connected indicator if needed
            Toast.makeText(requireContext(), "Live futures data connected", Toast.LENGTH_SHORT).show()
            
            // Subscribe to data streams after connection
            lifecycleScope.launch {
                delay(500) // Small delay to ensure connection is stable
                if (webSocketClient.isConnected()) {
                    webSocketClient.subscribeToPositionUpdates()
                    webSocketClient.subscribeToOrderUpdates()
                    webSocketClient.subscribeToBalanceUpdates()
                }
            }
        } else {
            // Show disconnected indicator
            Toast.makeText(requireContext(), "Live futures data disconnected", Toast.LENGTH_SHORT).show()
            
            // Try to reconnect after delay
            lifecycleScope.launch {
                delay(5000)
                if (!webSocketClient.isConnected()) {
                    Log.d(TAG, "Attempting to reconnect WebSocket")
                    webSocketClient.resetConnection()
                }
            }
        }
    }

    private fun fetchInitialData() {
        lifecycleScope.launch {
            try {
                // Check health first
                repository.getHealth().fold(
                    onSuccess = { health ->
                        Log.d(TAG, "Service health: ${health.status}")
                        if (health.services.binanceUsdm == "connected") {
                            // Service is healthy, fetch data
                            refreshAllData()
                        } else {
                            // Show warning but still try to refresh data since REST API might work
                            Log.w(TAG, "USD-M service not connected, trying REST API")
                            refreshAllData()
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Health check failed: ${error.message}")
                        showError("Service unavailable: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in fetchInitialData: ${e.message}")
                showError("Failed to connect to service")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun refreshData() {
        lifecycleScope.launch {
            refreshAllData()
            if (_binding != null) {
                binding.futuresSwipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private suspend fun refreshAllData() {
        try {
            // Fetch positions and orders in parallel
            refreshPositions()
            refreshOrders()
            refreshAccountData()
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing data: ${e.message}")
            showError("Failed to refresh data: ${e.message}")
        }
    }

    private suspend fun refreshPositions() {
        repository.getFuturesPositions().fold(
            onSuccess = { response ->
                if (response.success && response.data != null) {
                    Log.d(TAG, "USD-M futures positions fetched: ${response.data.size}")
                    updatePositionsUI(response.data)
                } else {
                    Log.e(TAG, "USD-M futures positions fetch failed: ${response.error}")
                    showError(response.error ?: "Failed to fetch USD-M futures positions")
                }
            },
            onFailure = { error ->
                Log.e(TAG, "USD-M futures positions fetch error: ${error.message}")
                showError("Network error: ${error.message}")
            }
        )
    }

    private suspend fun refreshOrders() {
        repository.getFuturesOrders().fold(
            onSuccess = { response ->
                if (response.success && response.data != null) {
                    Log.d(TAG, "USD-M futures orders fetched: ${response.data.size}")
                    openOrders = response.data
                } else {
                    Log.e(TAG, "USD-M futures orders fetch failed: ${response.error}")
                }
            },
            onFailure = { error ->
                Log.e(TAG, "USD-M futures orders fetch error: ${error.message}")
            }
        )
    }

    private suspend fun refreshAccountData() {
        repository.getFuturesAccount().fold(
            onSuccess = { response ->
                if (response.success && response.data != null) {
                    Log.d(TAG, "USD-M futures account data fetched: ${response.data.totalWalletBalance}")
                    // Update account UI if needed
                } else {
                    Log.e(TAG, "USD-M futures account fetch failed: ${response.error}")
                }
            },
            onFailure = { error ->
                Log.e(TAG, "USD-M futures account fetch error: ${error.message}")
            }
        )
    }

    private fun updatePositionsUI(positions: List<PositionData>) {
        requireActivity().runOnUiThread {
            // Convert PositionData to BinancePosition for adapter compatibility
            val binancePositions = positions.map { position ->
                // Find TP/SL orders for this position
                val positionOrders = openOrders.filter { it.symbol == position.symbol }
                
                // Get the correct side for TP/SL orders (opposite to position)
                val isLong = position.positionAmount > 0
                val expectedSide = if (isLong) "SELL" else "BUY"
                
                // Find TP and SL orders
                val tpOrder = positionOrders.find { 
                    it.side == expectedSide && (it.type == "TAKE_PROFIT_MARKET" || it.type == "TAKE_PROFIT")
                }
                val slOrder = positionOrders.find { 
                    it.side == expectedSide && (it.type == "STOP_MARKET" || it.type == "STOP_LOSS_MARKET")
                }
                
                BinancePosition(
                    symbol = position.symbol,
                    positionAmt = position.positionAmount,
                    entryPrice = position.entryPrice,
                    markPrice = position.markPrice,
                    unrealizedProfit = position.unrealizedProfit,
                    liquidationPrice = calculateLiquidationPrice(position), // Calculate liquidation price
                    leverage = position.leverage.toInt(),
                    marginType = position.marginType,
                    isolatedMargin = position.isolatedMargin,
                    roe = position.percentage, // Use percentage as ROE
                    takeProfitPrice = tpOrder?.stopPrice ?: 0.0, // Get TP price from orders
                    stopLossPrice = slOrder?.stopPrice ?: 0.0, // Get SL price from orders
                    positionSide = position.positionSide,
                    percentage = position.percentage,
                    maxNotionalValue = position.maxNotionalValue,
                    isAutoAddMargin = position.isAutoAddMargin
                )
            }

            // Update adapter
            futuresAdapter.submitList(binancePositions)

            // Show empty state if no positions
            binding.emptyStateText.visibility = if (binancePositions.isEmpty()) View.VISIBLE else View.GONE
            if (binancePositions.isEmpty()) {
                binding.emptyStateText.text = "No open futures positions"
            }

            Log.d(TAG, "UI updated with ${binancePositions.size} positions")
        }
    }

    /**
     * Calculate liquidation price based on position data
     * This is a simplified calculation - in reality Binance uses more complex formulas
     */
    private fun calculateLiquidationPrice(position: PositionData): Double {
        return try {
            if (position.positionAmount == 0.0) return 0.0
            
            val leverage = position.leverage
            val entryPrice = position.entryPrice
            val isLong = position.positionAmount > 0
            
            // Simplified liquidation price calculation
            // For LONG: liqPrice = entryPrice * (1 - 1/leverage)  
            // For SHORT: liqPrice = entryPrice * (1 + 1/leverage)
            val liqPrice = if (isLong) {
                entryPrice * (1 - 1 / leverage)
            } else {
                entryPrice * (1 + 1 / leverage)
            }
            
            // Ensure liquidation price is positive
            if (liqPrice > 0) liqPrice else 0.0
        } catch (e: Exception) {
            Log.e("ExternalFuturesFragment", "Error calculating liquidation price: ${e.message}")
            0.0
        }
    }

    private fun startHeartbeat() {
        lifecycleScope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL)
                if (webSocketClient.isConnected()) {
                    webSocketClient.sendHeartbeat()
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        if (_binding != null) {
            binding.loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    private fun showError(message: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error: $message")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webSocketClient.disconnect()
        _binding = null
    }
} 